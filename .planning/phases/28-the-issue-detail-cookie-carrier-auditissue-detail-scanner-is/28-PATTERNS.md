# Phase 28: The Issue-Detail Cookie Carrier — Pattern Map (GAP CLOSURE, round 2)

**Mapped:** 2026-08-27
**Files analyzed:** 4 production-modified + 2 test-created (+ 3 prose sites under D-28-08)
**Analogs found:** 5 / 6 — one test file has **NO ANALOG IN THE REPOSITORY** (see "No Analog Found")

Source of the file list: `28-CONTEXT.md` (D-28-05 … D-28-08) and `28-VERIFICATION.md` gaps.
There is no `28-RESEARCH.md` — this is a gap round.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/kotlin/.../scanner/ScannerIssueSupport.kt` (route 1, `Payload Used:` at `:121`) | service (pure render helper) | transform | **the same file, `sanitizeInjectionPointValue` `:61-71`** | exact (in-file sibling) |
| `src/main/kotlin/.../scanner/AiScanCheck.kt` (route 2, `buildDetail` `:322-368`) | scan-check adapter (Montoya `ActiveScanCheck`) | request-response → transform | `ActiveAiScanner.createConfirmedIssue` `:1230-1246` for policy acquisition; `ScannerIssueSupport` for the control shape | role-match |
| `src/test/kotlin/.../scanner/*` route-1 red probe (new or amended) | test | transform | `IssueDetailCookieCarrierTest.kt` (mocks-free, 759 lines) | exact |
| `src/test/kotlin/.../scanner/*` route-2 test (new) | test | transform | **none** — see "No Analog Found" | none |
| `26-SECURITY.md` `AR-27-08` cell (append-and-amend) | record | — | `Redaction.kt:596-620` supersession block + the 28-03 protocol | exact |
| `CookieCarrierInventoryTest.kt:407` prose correction | test-record | — | same file's existing entry prose | exact |

## Pattern Assignments

### Route 1 — `ScannerIssueSupport.kt` (`Payload Used:` line)

**Analog: the same file, one line up.** Copy the *shape and the KDoc discipline* verbatim; the
control for the payload is a sibling function, not a new mechanism.

**The gate to copy** (`ScannerIssueSupport.kt:61-71`, verbatim):

```kotlin
internal fun sanitizeInjectionPointValue(
    point: InjectionPoint,
    policy: RedactionPolicy,
): String =
    when {
        // The cookie carrier. Same marker sanitizeParameters and sanitizeHeaders write for a
        // stripped cookie, so one vocabulary is met across every cookie control in the product.
        policy.stripCookies && point.type == InjectionType.COOKIE -> INJECTION_VALUE_STRIPPED_MARKER
        // D-28-01: every other type passes through, truncated exactly as before. Deliberate.
        else -> point.originalValue.take(ORIGINAL_VALUE_MAX_CHARS)
    }
```

**The marker constant to reuse — do NOT invent a second one** (`:18-24`):

```kotlin
/**
 * (PRIV-05) 28-01 — the marker written in place of a stripped cookie value.
 *
 * The text is READ FROM the marker `McpToolHelpers.sanitizeParameters` and `sanitizeHeaders`
 * already write for a stripped cookie (`McpToolHelpers.kt:393`), not invented here, so a reader
 * meets ONE vocabulary across every cookie control in the product.
 */
internal const val INJECTION_VALUE_STRIPPED_MARKER = "[STRIPPED]"
```

**The KDoc discipline sentence D-28-07 binds the plan to** (`:41-44`, verbatim — quote it, do not
paraphrase):

> TYPE-KEYED, never shape-keyed. The decision is taken on [InjectionType.COOKIE], a member of a
> closed enum, so no reformatting of the detail line can defeat it.

**The call site to change** (`:120-121`) — note line 120 already has the policy in scope, so the
payload branch needs **no new parameter**, only a second sanitizer call:

```kotlin
detailLines.add("  Original Value: ${sanitizeInjectionPointValue(point, policy)}")
detailLines.add("  Payload Used: ${payload.value.take(PAYLOAD_VALUE_MAX_CHARS)}")   // <- uncontrolled
```

**Two KDoc blocks D-28-08 requires corrected in this same file (they contradict the new code):**

- `:32-33` — `PAYLOAD_VALUE_MAX_CHARS`'s KDoc ends: *"This constant is NOT part of the privacy
  control: the payload is agent-authored, not operator traffic."* False for context-aware payloads.
- `:74-75` — *"THE ONLY PRODUCER OF THE ACTIVE-SCAN ISSUE DETAIL LINES IN THE REPOSITORY."* False:
  `grep -rn "Original Value" src/main/kotlin/` returns two (this file and `AiScanCheck.kt:353`).

Correction *style* analog (append-and-amend inside a KDoc, prior text kept verbatim under a dated
marker): `Redaction.kt:600-620` —

```kotlin
 * SUPERSEDED IN PART — 2026-08-27, phase 28 plan 28-02. Bound 2's paragraph above is KEPT
 * VERBATIM as the historical record: while the issue-detail route was uncontrolled, refusing the
 * conversion WAS the correct call, and deleting the reasoning would leave a later reader unable
 * to tell a considered deferral from an oversight.
```

---

### Route 2 — `AiScanCheck.kt` (`buildDetail`)

**Analog for policy acquisition: `ActiveAiScanner.kt:1237-1245`** —

```kotlin
// (PRIV-05) 28-01 / AR-27-08: these lines are built by the SINGLE producer in
// ScannerIssueSupport, never inline here. A second producer is how the cookie control on
// this carrier gets bypassed without anyone editing it.
val detailLines =
    ScannerIssueSupport.buildActiveIssueDetailLines(
        target.injectionPoint,
        target.vulnHint.vulnClass.name,
        payload,
        confirmation.evidence,
        metadataSection,
        RedactionPolicy.fromMode(getSettings().privacyMode),
    )
```

`ActiveAiScanner` holds `private val getSettings: () -> AgentSettings` (`:65`) — **identical
collaborator shape to `AiScanCheck`**:

```kotlin
class AiScanCheck(
    private val api: MontoyaApi,
    private val getSettings: () -> AgentSettings,
) : ActiveScanCheck
```

wired at `App.kt:214-215`:

```kotlin
val aiScanCheck = AiScanCheck(api) { settingsRepo.load() }
api.scanner().registerActiveScanCheck(aiScanCheck, ScanCheckType.PER_INSERTION_POINT)
```

**Key finding — nothing needs threading.** `buildDetail` **already calls `getSettings()`** on its
first line (`AiScanCheck.kt:327`):

```kotlin
private fun buildDetail(
    insertionPoint: AuditInsertionPoint,
    payload: Payload,
    evidence: String,
): String {
    val settings = getSettings()
    val backendId = settings.preferredBackendId
```

so the policy is one expression away: `RedactionPolicy.fromMode(settings.privacyMode)`. Importing
`com.six2dez.burp.aiagent.redact.RedactionPolicy` (as `ActiveAiScanner.kt:14` does) is the whole
plumbing cost. A plan that proposes threading a policy parameter down from `doCheck` is doing more
work than the file requires.

**The two uncontrolled lines** (`AiScanCheck.kt:352-357`):

```kotlin
**Insertion Point:** ${insertionPoint.name()} (${insertionPoint.type()})
**Original Value:** ${insertionPoint.baseValue().take(100)}

**Payload Used:**
```
${payload.value.take(500)}
```
```

**⚠ TYPE-KEYING DOES NOT TRANSFER LITERALLY — measured, not assumed.**
`ScannerIssueSupport` keys on the project's own `InjectionType.COOKIE`. `AiScanCheck` holds a
Montoya `AuditInsertionPoint`, whose `type()` returns `AuditInsertionPointType`, and the cookie
constant there is named **`PARAM_COOKIE`**, verified from the shipped jar:

```
$ javap AuditInsertionPointType.class | grep -i cookie
  public static final burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType PARAM_COOKIE;
```

Therefore `Redaction.isCookieParameterType(...)` (`Redaction.kt:622`) — which compares
`typeName.trim().uppercase() == "COOKIE"` — **returns false for `PARAM_COOKIE`** and must not be
reused here as-is. The correct type-keyed gate is an identity compare against the closed Montoya
enum constant, e.g. `insertionPoint.type() == AuditInsertionPointType.PARAM_COOKIE`, which preserves
D-28-07's "closed enum, never a rendered string" discipline. If the plan instead wants ONE predicate,
that is a *widening of `Redaction.isCookieParameterType`* and must be planned explicitly, since
`CookieRouteDispositionTest.exactlyOneCookieTypePredicateExistsInMainSource` counts predicates in
main source and will move.

Value-preserving-swap comment analog, if a shared predicate is chosen
(`InjectionPointExtractor.kt:30-37`):

```kotlin
// The cookie-type question is OWNED by Redaction.isCookieParameterType (phase 28, plan 28-02).
// This is an IDENTITY swap: the shared predicate trims and upper-cases before comparing, so it
// accepts every type name the old inline `== "COOKIE"` accepted and no others that Burp's
// closed HttpParameterType enum can produce.
```

---

### Test — route 1 red probe

**Analog: `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt`** —
exact match, mocks-free, and it is the file the verifier already mutated to produce the SC1
falsification. Amend it (the sentinels, `BeforeEach` distinctness check and attribution control are
already in place) rather than starting a new file.

**Fixture construction to copy** (`:557-576`):

```kotlin
fun cookiePoint() = InjectionPoint(InjectionType.COOKIE, COOKIE_POINT_NAME, DETAIL_SENTINEL)
fun urlParamPoint() = InjectionPoint(InjectionType.URL_PARAM, COOKIE_POINT_NAME, DETAIL_SENTINEL)

fun detailLinesFor(point: InjectionPoint, mode: PrivacyMode): List<String> =
    ScannerIssueSupport.buildActiveIssueDetailLines(
        point,
        VulnClass.SQLI.name,
        PAYLOAD,
        "evidence-marker-present",
        METADATA_SECTION,
        RedactionPolicy.fromMode(mode),
    )
```

**The fixture the verifier proved is the defect** (`:544`) — this is what the gap round must replace
with a `PayloadGenerator`-built payload (VERIFICATION `missing[3]`):

```kotlin
val PAYLOAD =
    Payload(
        value = "benign-probe-payload",      // contradicts its own VulnClass.SQLI label
        vulnClass = VulnClass.SQLI,
        ...
    )
```

**Mode parameterisation convention:** not JUnit `@ParameterizedTest`. The file uses one named `@Test`
per mode — `cookieOriginalValueIsStrippedUnderStrict`, `…UnderBalanced`,
`cookieOriginalValueSurvivesUnderOff` — precisely so SC3 can name a single designated red-probe
assertion. Mirror that for the payload line (`…PayloadIsStrippedUnderStrict` etc.).

**Named-assertion convention** — every assertion carries a message that states the invariant, the
mode and the observed value, so the failure text is self-describing (this is what SC3 records
verbatim). Measured example from the verifier's probe:

```
org.opentest4j.AssertionFailedError: STRICT: the COOKIE-typed injection point's originalValue
must be ABSENT from the serialized issue detail, but the sentinel
'apple-orange-basket-lantern' was present. ==> expected: <false> but was: <true>
```

**The `originalValueRenderedFor` helper** (`:620-637`) — the shape a `payloadRenderedFor` twin should
copy, INCLUDING the single-producer assertion and its message:

```kotlin
fun originalValueRenderedFor(point: InjectionPoint, mode: PrivacyMode): String {
    val matching = detailLinesFor(point, mode).filter { it.contains(ORIGINAL_VALUE_PREFIX) }
    assertEquals(
        1,
        matching.size,
        "SINGLE PRODUCER: exactly one detail line may carry the '$ORIGINAL_VALUE_PREFIX' " +
            "prefix. Found ${matching.size}: $matching. A second producer is how this " +
            "control gets bypassed without anyone editing it.",
    )
    return matching[0].substringAfter(ORIGINAL_VALUE_PREFIX)
}
```

**Note for the planner (D-28-06):** this helper's `assertEquals(1, ...)` is exactly the "gate" WR-01
calls structurally incapable — it filters the list one producer returned. Copying it for the payload
line copies that limitation too. The file already carries a `File`-based source-scan idiom
(`ACTIVE_SCANNER_SOURCE_PATH = "src/main/kotlin/.../ActiveAiScanner.kt"`, `import java.io.File`) and
`CookieRouteDispositionTest.exactlyOneCookieTypePredicateExistsInMainSource` is the working
repo-wide-scan analog if a plan chooses to build a real gate — but D-28-06 says that is a NAMED
RESIDUAL, not gap scope. Do not let it be quietly satisfied or quietly claimed.

**Windows caveat carried from `already_known` WR-05:** `CookieRouteDispositionTest.kt:286` mixes the
`"src/main/kotlin"` literal with `File.separator`. Any source-scanning code copied from it inherits
that defect.

---

### Test — route 2 (`AiScanCheck`)

See "No Analog Found" below. The available *partial* analogs, in ranked order:

1. **`AiPassiveScanCheckTest.kt:28-49`** — the only test in the repo that drives a Burp `ScanCheck`.
   It documents the hard constraint the route-2 plan must design around:

```kotlin
val api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
...
// doCheck() completes without blocking on AI.
// AuditResult.auditResult() throws NPE without Burp runtime — expected in unit tests.
try {
    check.doCheck(reqResp)
} catch (_: NullPointerException) {
    // Expected: Burp ObjectFactoryLocator.FACTORY is null outside the Burp runtime.
}
```

   i.e. **`AuditIssue.auditIssue(...)` and `AuditResult.auditResult(...)` cannot be called in a unit
   test** — they route through `ObjectFactoryLocator.FACTORY`, which is null outside Burp. A route-2
   test therefore cannot go through `createIssue` (`AiScanCheck.kt:249-260`); it must reach
   `buildDetail` directly, which means **`buildDetail` has to be widened from `private` to
   `internal`** (the same visibility `ScannerIssueSupport`'s controlled functions use). Plan for that
   explicitly.

2. **Mockito deep-stub helper idiom**, e.g. `AgentSettingsMigrationTest.kt:203-207`:

```kotlin
private fun apiWith(preferences: Preferences): MontoyaApi {
    val api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    whenever(api.persistence().preferences()).thenReturn(preferences)
    return api
}
```

   `AuditInsertionPoint` is a plain **interface** (verified via `javap`: `name()`, `baseValue()`,
   `buildHttpRequestWithPayload`, `issueHighlights`, `default type()`), so `mock<AuditInsertionPoint>()`
   + `whenever(it.baseValue()).thenReturn(DETAIL_SENTINEL)` +
   `whenever(it.type()).thenReturn(AuditInsertionPointType.PARAM_COOKIE)` is viable with the
   already-declared `org.mockito.kotlin:mockito-kotlin:5.4.0` (`build.gradle.kts:60`). No new
   dependency is needed.

3. **`IssueDetailCookieCarrierTest`'s serialization tail** — reusable unchanged once a detail STRING
   exists: `IssueDetails(detail = …)` → `toolJson.encodeToString` → `Redaction.apply(blob, policy,
   HOST_SALT)` → `detailFieldOf(blob)`. This is also the only existing way to discharge VERIFICATION
   `missing[6]` (the unmeasured `Redaction.apply` pass over the route-2 shape).

## Shared Patterns

### One marker vocabulary
**Source:** `ScannerIssueSupport.kt:24` — `INJECTION_VALUE_STRIPPED_MARKER = "[STRIPPED]"`, itself
read from `McpToolHelpers.kt:393`.
**Apply to:** both routes. D-28-05's discretion clause says the two routes may parallelise "but the
shared marker vocabulary must come out identical." If route 2 is a separate plan/worktree, the
marker must be *referenced*, not retyped — a second `"[STRIPPED]"` literal in `AiScanCheck.kt` is the
failure mode this rule names.

### Policy acquisition
**Source:** `Redaction.kt:27-45` `RedactionPolicy.fromMode(mode)` → `stripCookies = true` for STRICT
and BALANCED, `false` for OFF.
**Apply to:** every write site. Both routes read the LIVE policy at the write site via
`getSettings()`, never a cached one — pinned for route 1 by `theWriteSiteReadsTheLivePolicy`.

### Type-keyed, never shape-keyed
**Source:** `ScannerIssueSupport.kt:41-44` KDoc.
**Apply to:** both routes — but with the two DIFFERENT enums documented above
(`InjectionType.COOKIE` vs `AuditInsertionPointType.PARAM_COOKIE`).

### Append-and-amend record correction
**Source:** `Redaction.kt:600-620` (KDoc form) and the 28-03 `26-SECURITY.md` protocol (prior text
byte-prefix intact, dated supersession marker, nothing rewritten; SC5 verified this by sha256 of the
first 3399 bytes).
**Apply to:** `26-SECURITY.md:315`, `CookieCarrierInventoryTest.kt:407`, and both
`ScannerIssueSupport.kt` KDoc blocks (D-28-08).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| route-2 test for `AiScanCheck.buildDetail` | test | transform | **No test in this repository constructs or fakes a Montoya `AuditInsertionPoint`** — `grep -rc 'AuditInsertionPoint' src/test/kotlin/` returns **0 files**. No test calls `AuditIssue.auditIssue(...)` either. `AiPassiveScanCheckTest` is the nearest thing and it *works around* the Burp factory by catching the NPE rather than by faking the collaborator. This materially changes the plan shape: the route-2 test needs a purpose-built `AuditInsertionPoint` mock, a widened `internal` `buildDetail`, and it CANNOT be an end-to-end `doCheck` test. |

Not nominating a distant analog: `ChatPanelTestHarness.kt` and the `apiWith(...)` deep-stub helpers
are *`MontoyaApi`* analogs, not insertion-point analogs, and `AiScanCheck` does not need `api` at all
inside `buildDetail`.

## Metadata

**Analog search scope:** `src/main/kotlin/com/six2dez/burp/aiagent/{scanner,redact}`, all of
`src/test/kotlin`, `build.gradle.kts` (test deps), and the shipped `montoya-api-2026.2.jar`
(`javap` on `AuditInsertionPoint` / `AuditInsertionPointType`).
**Files scanned:** ~30 greps + 8 targeted reads.
**Pattern extraction date:** 2026-08-27
