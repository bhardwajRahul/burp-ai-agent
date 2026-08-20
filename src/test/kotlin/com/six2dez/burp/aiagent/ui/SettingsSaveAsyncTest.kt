package com.six2dez.burp.aiagent.ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.persistence.Preferences
import com.six2dez.burp.aiagent.audit.AuditLogger
import com.six2dez.burp.aiagent.backends.BackendRegistry
import com.six2dez.burp.aiagent.mcp.McpSupervisor
import com.six2dez.burp.aiagent.scanner.ActiveAiScanner
import com.six2dez.burp.aiagent.scanner.PassiveAiScanner
import com.six2dez.burp.aiagent.supervisor.AgentSupervisor
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * SC4 / E7 / E8 — the Settings save path.
 *
 * **Naming constraint (hard).** `build.gradle.kts` excludes `*IntegrationTest`, `*ConcurrencyTest`,
 * `*BackpressureTest`, `*RestartPolicyTest` and `*SupervisionTest` under `-PexcludeHeavyTests=true`,
 * which is exactly what `.github/workflows/build.yml` passes. `SettingsSaveAsyncTest` is the approved
 * name; any of those five suffixes would make this suite silently stop running on the PR gate.
 *
 * This class opens with the A2 spike: research found no `Toolkit` / `Desktop` / `JFileChooser` /
 * `getRootFrame` / `GraphicsEnvironment` / `ImageIO` call in [SettingsPanel]'s construction path, but
 * that was inference from absence. [settingsPanelIsHeadlesslyConstructible] settles it by execution.
 */
class SettingsSaveAsyncTest {
    /**
     * A2 — is a real [SettingsPanel] constructible under `-Djava.awt.headless=true`?
     *
     * Everything the rest of this suite asserts is built on a real panel, so this is checked first and
     * on its own. It asserts nothing about saving: construction succeeding, and `currentSettings()`
     * returning a settings object read back off the real Swing components, is the whole claim.
     */
    @Test
    fun settingsPanelIsHeadlesslyConstructible() {
        val panel = newSettingsPanel()

        assertNotNull(
            panel.currentSettings(),
            "A2: a real SettingsPanel must be constructible headlessly and must read its own " +
                "Swing components back through currentSettings().",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Fixture
    // ---------------------------------------------------------------------------------------------

    /**
     * Builds a real [SettingsPanel] from deep-stub collaborators, following the
     * `ChatPanelTestHarness.create` construction pattern, plus the in-memory [Preferences] fake from
     * `SettingsDefaultsPersistenceTest` so `AgentSettingsRepository.load()` has somewhere to read from.
     */
    private fun newSettingsPanel(): SettingsPanel {
        // Built BEFORE the whenever() below: passing inMemoryPreferences() inline would build its own
        // mock while this stubbing is still open, which Mockito reports as UnfinishedStubbingException.
        val preferences = inMemoryPreferences()
        val api: MontoyaApi = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        whenever(api.persistence().preferences()).thenReturn(preferences)
        // A REAL list, not a deep stub: SettingsPanel.kt:115 calls .toTypedArray() on the result, and
        // a mocked List returns null from Collection.toArray(T[]), which JComboBox rejects with an NPE.
        val backends: BackendRegistry = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        whenever(backends.listAllBackendIds()).thenReturn(listOf("codex-cli", "ollama"))
        return SettingsPanel(
            api = api,
            backends = backends,
            supervisor = mock<AgentSupervisor>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
            audit = mock<AuditLogger>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
            mcpSupervisor = mock<McpSupervisor>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
            passiveAiScanner = mock<PassiveAiScanner>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
            activeAiScanner = mock<ActiveAiScanner>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
        )
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
