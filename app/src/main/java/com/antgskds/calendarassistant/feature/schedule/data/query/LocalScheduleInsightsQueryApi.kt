package com.antgskds.calendarassistant.feature.schedule.data.query

import com.antgskds.calendarassistant.shared.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.*

class LocalScheduleInsightsQueryApi : ScheduleInsightsQueryApi {
    override fun hasDuplicateAdvanceReminder(events: List<Event>, minutes: Int): Boolean {
        return events.any { event ->
            event.reminderMinutes.any { it <= minutes }
        }
    }

    override fun findNextRecurringInstance(
        events: List<Event>,
        parentEventId: Long,
        nowMillis: Long
    ): Event? {
        return events
            .filter { it.parentId == parentEventId }
            .filter { it.startMillis >= nowMillis }
            .minByOrNull { it.startMillis }
    }
}
