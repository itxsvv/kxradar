package org.itxsvv.kxradar

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.TurnScreenOn
import kotlinx.coroutines.runBlocking
import org.itxsvv.kxradar.ui.dataStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Mirrors the key RadarSettingsService reads, to write settings the app itself would never write.
private val settingsKey = stringPreferencesKey("settings_v2")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["org.itxsvv.kxradar"])
class KarooRadarSettingsTest {
    @get:Rule val karoo = FakeKarooRule()

    @Test
    fun `stored settings that cannot be decoded fall back to defaults`() {
        runBlocking { karoo.app.dataStore.edit { it[settingsKey] = "{ not json" } }

        karoo.startRadarService()
        karoo.publishThreat(1)

        val beep = karoo.awaitEffect<PlayBeepPattern>()
        assertEquals(listOf(PlayBeepPattern.Tone(200, 100)), beep.tones)
    }

    @Test
    fun `settings with unknown keys keep the stored values`() {
        val stored =
            """
            {"threatBeep":{"frequency":555,"duration":66},
             "passedBeep":{"frequency":0,"duration":100},
             "somethingNew":true}
            """.trimIndent()
        runBlocking { karoo.app.dataStore.edit { it[settingsKey] = stored } }

        karoo.startRadarService()
        karoo.publishThreat(1)

        val beep = karoo.awaitEffect<PlayBeepPattern>()
        assertEquals(listOf(PlayBeepPattern.Tone(555, 66)), beep.tones)
    }

    @Test
    fun `a beep change made while riding is picked up`() {
        karoo.startRadarExtension()

        karoo.changeSettings(RadarSettings(threatBeep = Beep(700, 120)))

        // The service reads the new settings asynchronously, so the threat is retried until the
        // beep carries the new tone.
        val beep = karoo.awaitValue(20_000) {
            karoo.publishThreat(0)
            karoo.publishThreat(1)
            karoo.system.effects.filterIsInstance<PlayBeepPattern>()
                .firstOrNull { it.tones == listOf(PlayBeepPattern.Tone(700, 120)) }
        }
        assertEquals(listOf(PlayBeepPattern.Tone(700, 120)), beep.tones)
    }

    @Test
    fun `turning alerts off while riding stops them`() {
        karoo.startRadarExtension()

        karoo.changeSettings(RadarSettings(enabled = false))
        karoo.settle()

        karoo.publishThreat(1)

        karoo.assertNoEffect<TurnScreenOn>()
        karoo.assertNoEffect<PlayBeepPattern>()
    }

    @Test
    fun `turning alerts back on while riding resumes them`() {
        karoo.startRadarExtension()
        karoo.changeSettings(RadarSettings(enabled = false))
        karoo.settle()
        karoo.publishThreat(1)
        karoo.assertNoEffect<PlayBeepPattern>()
        val mark = karoo.effectMark()

        karoo.changeSettings(RadarSettings(enabled = true))

        // The service reads the new settings asynchronously, so the pair is retried until it has.
        val beep = karoo.awaitValue(20_000) {
            karoo.publishThreat(0)
            karoo.publishThreat(1)
            karoo.system.effects.drop(mark).filterIsInstance<PlayBeepPattern>().firstOrNull()
        }
        assertEquals(listOf(PlayBeepPattern.Tone(200, 100)), beep.tones)
    }
}
