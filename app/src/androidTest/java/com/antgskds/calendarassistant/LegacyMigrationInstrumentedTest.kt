package com.antgskds.calendarassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import com.antgskds.calendarassistant.feature.schedule.data.ScheduleStoreGateway
import com.antgskds.calendarassistant.app.runtime.migration.LegacyDataMigrationCoordinator
import com.antgskds.calendarassistant.feature.settings.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyMigrationInstrumentedTest {
    @Test
    fun manualV3ImportIsIdempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = EventsDatabase.getInstance(context).eventsDao()
        val title = "instrumented-v3-import-idempotent"
        val importId = "instrumented-v3-import-idempotent-001"
        val coordinator = LegacyDataMigrationCoordinator(
            context = context,
            calendarCenter = ScheduleStoreGateway.getInstance(context),
            settingsRepository = SettingsRepository(context)
        )

        fun cleanup() {
            (dao.getAllEventsOrTasks() + dao.getArchivedEvents())
                .filter { it.title == title || it.importId == importId }
                .mapNotNull { it.id }
                .forEach { dao.deleteEvent(it) }
        }

        cleanup()
        try {
            val backupJson = """
                {
                  "version": 3,
                  "events": [
                    {
                      "startTS": 1900000000,
                      "endTS": 1900003600,
                      "title": "$title",
                      "location": "instrumented-room",
                      "description": "manual import idempotent test",
                      "reminder1Minutes": 15,
                      "rrule": "",
                      "exdates": [],
                      "importId": "$importId",
                      "timeZone": "Asia/Shanghai",
                      "lastUpdated": 1900000000,
                      "source": "instrumented-test",
                      "color": -16776961,
                      "state": 0,
                      "tag": "general"
                    }
                  ],
                  "archivedEvents": []
                }
            """.trimIndent()

            val first = coordinator.importEventsData(backupJson).getOrThrow()
            val afterFirst = (dao.getAllEventsOrTasks() + dao.getArchivedEvents())
                .count { it.title == title || it.importId == importId }

            val second = coordinator.importEventsData(backupJson).getOrThrow()
            val afterSecond = (dao.getAllEventsOrTasks() + dao.getArchivedEvents())
                .count { it.title == title || it.importId == importId }

            assertEquals(1, first.successCount)
            assertEquals(1, afterFirst)
            assertEquals(0, second.successCount)
            assertTrue(second.skippedCount >= 1)
            assertEquals(1, afterSecond)
        } finally {
            cleanup()
        }
    }
}
