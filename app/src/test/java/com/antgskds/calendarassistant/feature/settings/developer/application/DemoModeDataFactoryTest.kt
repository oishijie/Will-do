package com.antgskds.calendarassistant.feature.settings.developer.application

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoModeDataFactoryTest {
    private val today = LocalDate.of(2026, 9, 21)

    @Test
    fun createsEnoughReadOnlyContentForEveryDemoPage() {
        val events = DemoModeDataFactory.events(today)
        val weather = DemoModeDataFactory.weather(today)

        assertTrue(events.size >= 25)
        assertTrue(events.all { (it.id ?: 0L) < 0L })
        assertEquals(6, DemoModeDataFactory.quickMemos(today).size)
        assertEquals(24, weather.hourlyForecast.size)
        assertEquals(7, weather.dailyForecast.size)
        assertTrue(weather.alerts.isNotEmpty())
        assertTrue(weather.riskAlerts.isNotEmpty())
    }

    @Test
    fun demoVoiceUsesOnlyASyntheticPlaybackIdentifier() {
        val memos = DemoModeDataFactory.quickMemos(today)
        val voice = memos.first { it.type == com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType.VOICE }
        assertEquals(DemoModeDataFactory.QUICK_MEMO_AUDIO_PATH, voice.audioPath)
        assertTrue(voice.audioPath!!.startsWith("demo://"))
        assertTrue(voice.audioDurationMs > 0L)
        assertTrue(voice.bodyText.contains("演示语音"))
        assertTrue(memos.all { (it.id ?: 0L) < 0L })
    }
}
