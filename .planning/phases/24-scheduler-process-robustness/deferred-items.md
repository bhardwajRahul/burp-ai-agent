# Phase 24 — Deferred Items

Out-of-scope discoveries logged during execution. Nothing here was fixed; each is unrelated to the
task that surfaced it.

## From plan 24-01

### `RedactionTest.windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment` flakes under CPU load

- **Surfaced during:** plan 24-01 task 3, the unfiltered `./gradlew test detekt ktlintCheck` gate.
- **Symptom:** one failure out of 805 tests on the first full-suite run; the suite passed in
  isolation (`./gradlew test --tests '*RedactionTest'`) and the full gate passed green on an
  immediate re-run.
- **Cause (pre-existing, not a regression):** the test compares against `SafeRegex`'s wall-clock
  50 ms deadline, so it fails when the machine is loaded. It is the wall-clock-threshold family that
  `24-VALIDATION.md` §Assertions Explicitly Ruled Out bans for NEW assertions.
- **Why not fixed here:** out of scope. It lives in `src/test/kotlin/.../redact/RedactionTest.kt`,
  a file plan 24-01 does not touch, and REL-06 has no claim over the redaction engine's timing
  contract. Fixing it means changing `SafeRegex`'s deadline contract or restructuring the assertion,
  which is a decision the redaction phase owns.
- **Suggested owner:** a follow-up in the reliability backlog — replace the deadline comparison with
  a deterministic budget (an injected clock or an operation counter) rather than raising the timeout.
