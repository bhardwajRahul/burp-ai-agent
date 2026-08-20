package com.six2dez.burp.aiagent.ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import burp.api.montoya.persistence.Preferences
import com.six2dez.burp.aiagent.audit.AuditLogger
import com.six2dez.burp.aiagent.backends.BackendRegistry
import com.six2dez.burp.aiagent.config.AgentSettings
import com.six2dez.burp.aiagent.mcp.McpRequestLimiter
import com.six2dez.burp.aiagent.mcp.McpSupervisor
import com.six2dez.burp.aiagent.mcp.McpToolContext
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.redact.Redaction
import com.six2dez.burp.aiagent.scanner.ActiveAiScanner
import com.six2dez.burp.aiagent.scanner.PassiveAiScanner
import com.six2dez.burp.aiagent.supervisor.AgentSupervisor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.SwingUtilities

/**
 * SC4 / E7 / E8 / FLAG-23-06 — the Settings save path.
 *
 * **Naming constraint (hard).** `build.gradle.kts` excludes `*IntegrationTest`, `*ConcurrencyTest`,
 * `*BackpressureTest`, `*RestartPolicyTest` and `*SupervisionTest` under `-PexcludeHeavyTests=true`,
 * which is exactly what `.github/workflows/build.yml` passes. `SettingsSaveAsyncTest` is the approved
 * name; any of those five suffixes would make this suite silently stop running on the PR gate.
 *
 * **No test here compares an elapsed duration to a threshold.** A blocked EDT is proved by a mutual
 * latch whose failure mode is a deadlock that [assertTimeoutPreemptively] reports categorically, and
 * by the recorded outcome of that handshake rather than by a latch count alone — the vacuous-pass trap
 * plan 23-01 documented after hitting it.
 *
 * The `JOptionPane` save-failure modal is deliberately NOT asserted: `JOptionPane.getRootFrame()`
 * throws `HeadlessException`, a measured fact recorded in `CONCERNS.md`. These tests assert the banner
 * and the busy seam; the modal is routed to `23-HUMAN-UAT.md`. The modal was not deleted to make it
 * testable — D-12 keeps both surfaces on purpose.
 */
class SettingsSaveAsyncTest {
    /** Labels recorded from [OffEdtDispatch]'s settle observer — one per completed dispatch. */
    private val settled = CopyOnWriteArrayList<String>()
    private lateinit var settledSignal: CountDownLatch
    private val panels = CopyOnWriteArrayList<SettingsPanel>()

    @BeforeEach
    fun installObserver() {
        settled.clear()
        settledSignal = CountDownLatch(1)
        OffEdtDispatch.registerSettledObserver { label ->
            settled.add(label)
            settledSignal.countDown()
        }
    }

    @AfterEach
    fun releaseObserver() {
        OffEdtDispatch.registerSettledObserver(null)
        // Redaction is a process-wide singleton, so a custom pattern left installed by the E8 test
        // would follow this JVM into RedactionTest. Clearing is isolation, not tidiness.
        Redaction.setCustomPatterns(emptyList())
        panels.forEach { it.shutdown() }
        panels.clear()
    }

    /**
     * A2 — is a real [SettingsPanel] constructible under `-Djava.awt.headless=true`?
     *
     * Everything the rest of this suite asserts is built on a real panel, so this is checked first and
     * on its own. It asserts nothing about saving: construction succeeding, and `currentSettings()`
     * returning a settings object read back off the real Swing components, is the whole claim.
     */
    @Test
    fun settingsPanelIsHeadlesslyConstructible() {
        val fixture = newFixture()

        assertNotNull(
            fixture.panel.currentSettings(),
            "A2: a real SettingsPanel must be constructible headlessly and must read its own " +
                "Swing components back through currentSettings().",
        )
    }

    /**
     * S-11 (a) / SC4 / E7 — the EDT runs queued work while a save is in flight.
     *
     * `mcpSupervisor.applySettings` stands in for the real path's reach into
     * `KtorMcpServerManager.stop()`'s bounded `future.get(10, TimeUnit.SECONDS)`: it blocks until a
     * runnable queued to the EDT *after* the click has run. If the save body were still on the EDT
     * that runnable could never run, and the handshake records a categorical failure rather than a
     * slow path a timer has to guess at.
     */
    @Test
    fun theEdtIsFreeWhileASettingsSaveIsInFlight() {
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            val fixture = newFixture()
            val workerEntered = CountDownLatch(1)
            val probeRan = CountDownLatch(1)
            val edtWasFree = AtomicBoolean(false)
            val workerThread = AtomicReference<String>()

            whenever(fixture.mcpSupervisor.applySettings(any(), any(), any(), any())).thenAnswer {
                workerThread.set(Thread.currentThread().name)
                workerEntered.countDown()
                edtWasFree.set(probeRan.await(20, TimeUnit.SECONDS))
                null
            }

            SwingUtilities.invokeAndWait { fixture.panel.saveSettings() }
            assertTrue(
                workerEntered.await(20, TimeUnit.SECONDS),
                "SC4: the save never reached mcpSupervisor.applySettings.",
            )
            SwingUtilities.invokeLater { probeRan.countDown() }
            assertTrue(settledSignal.await(25, TimeUnit.SECONDS), "SC4: the save never settled.")

            // The LOUD clause, asserted first: the handshake's own recorded outcome. A bare
            // `probeRan.count == 0` would also read zero after a synchronous save finally returned and
            // the EDT drained its queue — green with the defect fully present.
            assertTrue(
                edtWasFree.get(),
                "SC4/E7: the EDT must run queued work while the save is mid-flight. A false here means " +
                    "the save body blocked the EDT for the whole bounded ten-second MCP stop wait.",
            )
            assertEquals(
                "burp-ai-settings-save",
                workerThread.get(),
                "SC4: the save body must run on the named daemon worker, not the EDT.",
            )
        }
    }

    /**
     * S-11 (b) / E7 — the snapshot is taken on the EDT before dispatch, and the success banner is
     * written from the completion callback rather than from the line after the dispatch returns.
     *
     * The component is mutated *while the worker is blocked*. A body that read the live Swing
     * components instead of its snapshot would observe the second value; a body holding the
     * pre-dispatch snapshot observes the first. That is what makes this assertion discriminating
     * rather than a restatement of the call.
     */
    @Test
    fun theSnapshotIsTakenOnTheEdtAndTheBannerIsWrittenFromTheCallback() {
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            val fixture = newFixture()
            val workerEntered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val appliedSnapshot = AtomicReference<AgentSettings>()

            whenever(fixture.supervisor.applySettings(any())).thenAnswer { invocation ->
                appliedSnapshot.set(invocation.getArgument(0))
                workerEntered.countDown()
                release.await(20, TimeUnit.SECONDS)
                null
            }

            SwingUtilities.invokeAndWait {
                fixture.panel.privacyMode.selectedItem = PrivacyMode.OFF
                fixture.panel.saveSettings()
            }
            assertTrue(workerEntered.await(20, TimeUnit.SECONDS), "The save never reached the worker.")

            // Mid-flight: the banner still reads the start message, and the component is edited under
            // the worker's feet. D-10 leaves every field editable during a save on purpose.
            SwingUtilities.invokeAndWait {
                assertEquals(
                    "Saving settings...",
                    fixture.panel.saveFeedbackLabel.text,
                    "UI-SPEC T1: the start banner stands for the whole flight.",
                )
                fixture.panel.privacyMode.selectedItem = PrivacyMode.STRICT
            }

            release.countDown()
            assertTrue(settledSignal.await(25, TimeUnit.SECONDS), "The save never settled.")
            SwingUtilities.invokeAndWait { }

            assertEquals(
                PrivacyMode.OFF,
                appliedSnapshot.get().privacyMode,
                "D-11: the body must apply the snapshot currentSettings() read on the EDT before " +
                    "dispatch. STRICT here means it read the live Swing component from the worker.",
            )
            assertEquals(
                "Saved and applied.",
                fixture.panel.saveFeedbackLabel.text,
                "Rule C-2: the success banner is written from the completion callback.",
            )
        }
    }

    /**
     * FLAG-23-06 / T-23-09 — the busy seam lowers on BOTH failure shapes, exactly once each.
     *
     * A path that returns without lowering leaves the Settings tab permanently unsaveable with no
     * error and no way back short of reloading the extension, so the failure paths are asserted
     * specifically. A success-only test does not cover the hazard.
     */
    @Test
    fun theBusySeamLowersOnBothFailureShapes() {
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            // Shape 1: the save body throws.
            val bodyFailure = newFixture()
            val bodyBusy = recordBusy(bodyFailure.panel)
            whenever(bodyFailure.supervisor.applySettings(any()))
                .thenAnswer { throw IllegalStateException("mcp restart refused") }

            SwingUtilities.invokeAndWait { bodyFailure.panel.saveSettings() }
            assertTrue(settledSignal.await(25, TimeUnit.SECONDS), "The failing save never settled.")
            SwingUtilities.invokeAndWait { }

            assertEquals(
                listOf(true, false),
                bodyBusy.toList(),
                "FLAG-23-06: the seam must be raised once and lowered once when the body throws.",
            )
            val failureBanner = bodyFailure.panel.saveFeedbackLabel.text
            assertTrue(
                failureBanner.startsWith("Save failed: "),
                "Rule C-2: the failure banner is written from the completion callback. Actual: $failureBanner",
            )

            // Shape 2: the completion callback itself throws. This is the second `finally` layer;
            // OffEdtDispatch's own tail wrapper cannot lower a seam it does not know about.
            settledSignal = CountDownLatch(1)
            val callbackFailure = newFixture()
            val callbackBusy = recordBusy(callbackFailure.panel)
            SwingUtilities.invokeAndWait {
                callbackFailure.panel.applyAndSaveSettingsAsync(callbackFailure.panel.currentSettings()) {
                    throw IllegalStateException("completion callback blew up")
                }
            }
            assertTrue(settledSignal.await(25, TimeUnit.SECONDS), "The save never settled.")
            SwingUtilities.invokeAndWait { }

            assertEquals(
                listOf(true, false),
                callbackBusy.toList(),
                "FLAG-23-06: the seam must still lower when the completion callback throws.",
            )
        }
    }

    /**
     * UI-SPEC Rule T-1 / T-2 / D-10 — the seam disables BOTH action buttons, and restores Save's fill.
     *
     * Driven through a real [BottomTabsPanel], which is what installs the listener, so this asserts
     * the shipped wiring rather than a listener the test supplied. Disabling only Save would leave
     * Restore defaults able to start a second save mid-flight, re-entering the double-save race
     * through the other door.
     */
    @Test
    fun bothActionButtonsGoInertWhileBusyAndComeBack() {
        val fixture = newFixture()
        val tabs = BottomTabsPanel(fixture.panel)
        val save = requireNotNull(button(tabs, "Save settings")) { "Save settings button not found." }
        val restore = requireNotNull(button(tabs, "Restore defaults")) { "Restore defaults button not found." }
        val seam = requireNotNull(fixture.panel.busyListener) { "BottomTabsPanel did not install the busy seam." }

        seam(true)
        assertFalse(save.isEnabled, "Rule T-1: Save settings must be inert while a save is in flight.")
        assertFalse(restore.isEnabled, "Rule T-1: Restore defaults must be inert too, not only Save.")
        assertEquals(
            UiTheme.Colors.outlineVariant,
            save.background,
            "Rule T-2: the opaque Save button keeps painting its fill when disabled, so it is recolored.",
        )
        assertNotEquals(
            UiTheme.Colors.primary,
            save.background,
            "Rule T-2: an inert Save button must not still read as the primary action.",
        )

        seam(false)
        assertTrue(save.isEnabled, "Rule T-1: Save settings must come back.")
        assertTrue(restore.isEnabled, "Rule T-1: Restore defaults must come back.")
        assertEquals(UiTheme.Colors.primary, save.background, "Rule T-2: T0 restores the primary fill.")
    }

    /**
     * E8 / S-11 (c) — a tool worker dispatched mid-save redacts under its own snapshot privacy mode,
     * against a fully-published custom-pattern list, and is never unredacted.
     *
     * This pins the recorded `documented-residual` answer to AI-SPEC dimension E8. It goes RED if a
     * future refactor ever makes the tool path read `getSettings()` live instead of the immutable
     * [McpToolContext] snapshot taken on the EDT before its dispatch — exactly the regression E8
     * exists to catch.
     */
    @Test
    fun aToolWorkerMidSaveRedactsUnderItsSnapshotModeAndIsNeverUnredacted() {
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            val fixture = newFixture()
            val marker = "SAVE-MARKER-4242"
            Redaction.setCustomPatterns(emptyList())

            // The save flips privacy mode STRICT -> OFF and publishes a custom pattern matching the
            // marker. Those are the two privacy-relevant global writes E8 names, and the body keeps
            // them contiguous.
            val updated =
                fixture.panel.currentSettings().copy(
                    privacyMode = PrivacyMode.OFF,
                    customRedactionPatterns = listOf("SAVE-MARKER-[0-9]+"),
                )

            // The tool worker's context, snapshotted BEFORE the save, exactly as ChatPanel builds it
            // on the EDT before dispatching a tool call.
            val toolContext =
                McpToolContext(
                    api = fixture.api,
                    privacyMode = PrivacyMode.STRICT,
                    determinismMode = false,
                    hostSalt = "salt",
                    toolToggles = emptyMap(),
                    unsafeEnabled = false,
                    unsafeTools = emptySet(),
                    enabledUnsafeTools = emptySet(),
                    limiter = McpRequestLimiter(4),
                    edition = BurpSuiteEdition.COMMUNITY_EDITION,
                    maxBodyBytes = 262_144,
                )

            // Park the save AFTER Redaction.setCustomPatterns has published, so the interleave under
            // test is the real one: a pre-save snapshot mode paired with a post-save pattern list.
            val patternsPublished = CountDownLatch(1)
            val release = CountDownLatch(1)
            whenever(fixture.passiveAiScanner.setEnabled(any())).thenAnswer {
                patternsPublished.countDown()
                release.await(20, TimeUnit.SECONDS)
                null
            }

            SwingUtilities.invokeAndWait { fixture.panel.applyAndSaveSettingsAsync(updated) { } }
            assertTrue(patternsPublished.await(20, TimeUnit.SECONDS), "The save never published its patterns.")

            val permit = toolContext.limiter.tryAcquire()
            // The marker is deliberately BARE. An earlier form read `token=$marker`, which STRICT's
            // built-in url/form token rule redacts on its own — the assertion below went green with
            // Redaction.setCustomPatterns deleted from the body, i.e. it proved nothing about the
            // post-save half of the E8 pairing. Measured, then fixed.
            val redacted = toolContext.redactIfNeeded(marker)
            release.countDown()
            assertTrue(settledSignal.await(25, TimeUnit.SECONDS), "The save never settled.")

            assertEquals(
                PrivacyMode.STRICT,
                toolContext.privacyMode,
                "E8: the tool worker redacts under the mode captured in its immutable snapshot. OFF " +
                    "here means the worker read live settings instead of its own context.",
            )
            assertEquals(
                PrivacyMode.OFF,
                fixture.panel.settings.privacyMode,
                "The save must really have flipped the mode, or the assertion above is vacuous.",
            )
            assertFalse(
                redacted.contains(marker),
                "E8: every reachable pairing redacts under some consistent policy. There is no state " +
                    "in which a call is redacted under no rules. Actual: $redacted",
            )
            assertTrue(
                permit,
                "E10: each call site mints its own limiter, so a save in flight must not exhaust the " +
                    "tool path's permits.",
            )
            assertFalse(
                redacted.contains("Too many concurrent MCP requests."),
                "E10: the tool path must not be throttled by a concurrent settings save.",
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Fixture
    // ---------------------------------------------------------------------------------------------

    private class Fixture(
        val panel: SettingsPanel,
        val api: MontoyaApi,
        val supervisor: AgentSupervisor,
        val mcpSupervisor: McpSupervisor,
        val passiveAiScanner: PassiveAiScanner,
    )

    /** Records every value the busy seam publishes, in order, replacing any listener already installed. */
    private fun recordBusy(panel: SettingsPanel): CopyOnWriteArrayList<Boolean> {
        val seen = CopyOnWriteArrayList<Boolean>()
        panel.setBusyListener { busy -> seen.add(busy) }
        return seen
    }

    private fun button(
        tabs: BottomTabsPanel,
        text: String,
    ): JButton? = ChatPanelTestHarness.find(tabs.root as java.awt.Container, JButton::class.java) { it.text == text }

    /**
     * Builds a real [SettingsPanel] from deep-stub collaborators, following the
     * `ChatPanelTestHarness.create` construction pattern, plus the in-memory [Preferences] fake from
     * `SettingsDefaultsPersistenceTest` so `AgentSettingsRepository.load()` has somewhere to read from.
     */
    private fun newFixture(): Fixture {
        // Built BEFORE the whenever() below: passing inMemoryPreferences() inline would build its own
        // mock while this stubbing is still open, which Mockito reports as UnfinishedStubbingException.
        val preferences = inMemoryPreferences()
        val api: MontoyaApi = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        whenever(api.persistence().preferences()).thenReturn(preferences)
        // A REAL list, not a deep stub: SettingsPanel.kt:115 calls .toTypedArray() on the result, and
        // a mocked List returns null from Collection.toArray(T[]), which JComboBox rejects with an NPE.
        val backends: BackendRegistry = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        whenever(backends.listAllBackendIds()).thenReturn(listOf("codex-cli", "ollama"))
        val supervisor: AgentSupervisor = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        val mcpSupervisor: McpSupervisor = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        val passiveAiScanner: PassiveAiScanner = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        val panel =
            SettingsPanel(
                api = api,
                backends = backends,
                supervisor = supervisor,
                audit = mock<AuditLogger>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
                mcpSupervisor = mcpSupervisor,
                passiveAiScanner = passiveAiScanner,
                activeAiScanner = mock<ActiveAiScanner>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
            )
        panels.add(panel)
        return Fixture(panel, api, supervisor, mcpSupervisor, passiveAiScanner)
    }

    /** Copied in shape from `SettingsDefaultsPersistenceTest.inMemoryPreferences`. */
    private fun inMemoryPreferences(): Preferences {
        val strings = mutableMapOf<String, String>()
        val booleans = mutableMapOf<String, Boolean>()
        val integers = mutableMapOf<String, Int>()

        val prefs = mock<Preferences>()
        whenever(prefs.getString(any())).thenAnswer { strings[it.getArgument<String>(0)] }
        whenever(prefs.setString(any(), any())).thenAnswer {
            strings[it.getArgument<String>(0)] = it.getArgument(1)
            null
        }
        whenever(prefs.getBoolean(any())).thenAnswer { booleans[it.getArgument<String>(0)] }
        whenever(prefs.setBoolean(any(), any())).thenAnswer {
            booleans[it.getArgument<String>(0)] = it.getArgument(1)
            null
        }
        whenever(prefs.getInteger(any())).thenAnswer { integers[it.getArgument<String>(0)] }
        whenever(prefs.setInteger(any(), any())).thenAnswer {
            integers[it.getArgument<String>(0)] = it.getArgument(1)
            null
        }
        return prefs
    }
}
