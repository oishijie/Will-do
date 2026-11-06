package com.antgskds.calendarassistant.feature.settings.preference.ui.contract

import com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager
import com.antgskds.calendarassistant.feature.schedule.api.model.CalendarSyncManager
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import kotlinx.coroutines.flow.StateFlow

interface PreferenceUiController {
    val settings: StateFlow<MySettings>
    val syncStatus: StateFlow<CalendarSyncManager.SyncStatus>
    val availableSyncCalendars: StateFlow<List<CalendarManager.CalendarInfo>>

    fun refreshSyncStatus()
    fun refreshSyncCalendars()
    fun toggleCalendarSync(enabled: Boolean)
    fun enableCalendarSyncAndSyncNow(callback: suspend (Result<Unit>) -> Unit = {})
    fun updateSourceCalendars(ids: List<Long>, callback: suspend (Result<Unit>) -> Unit = {})
    fun updateSyncIntervalSeconds(seconds: Int, callback: suspend (Result<Unit>) -> Unit = {})
    fun hasDuplicateAdvanceReminder(minutes: Int): Boolean
    fun updateUiSize(size: Int)
    fun updateScreenshotDelay(delay: Long)
    fun setRememberCalendarViewMode(enabled: Boolean)
    fun setHomeCalendarViewMode(mode: String)
    fun updateQuickMemoAutoStop(enabled: Boolean? = null, seconds: Int? = null)
    fun updateDailySummaryTimes(morningMinuteOfDay: Int? = null, eveningMinuteOfDay: Int? = null, onUpdated: () -> Unit = {})
    fun updatePreference(
        showTomorrow: Boolean? = null, dailySummary: Boolean? = null, liveCapsule: Boolean? = null,
        pickupAggregation: Boolean? = null, hapticFeedbackEnabled: Boolean? = null,
        networkSpeedCapsule: Boolean? = null,
        floatingWindow: Boolean? = null, advanceReminderEnabled: Boolean? = null,
        advanceReminderMinutes: Int? = null, transitAutoCheckInEnabled: Boolean? = null,
        transitAutoCheckInMinutes: Int? = null, autoArchive: Boolean? = null,
        recognitionMode: Int? = null, defaultEventDurationMinutes: Int? = null,
        useMultimodalAi: Boolean? = null, disableThinking: Boolean? = null,
        floatingEventRange: Int? = null, floatingExpandSide: String? = null,
        floatingHandleSizePercent: Int? = null,
        floatingHandleTheme: String? = null,
        floatingHandleAutoHideSeconds: Int? = null,
        floatingEdgeLineEnabled: Boolean? = null,
        volumeUpLongPressEnabled: Boolean? = null,
        volumeUpLongPressAction: Int? = null, smsMonitoring: Boolean? = null,
        volumeDownLongPressEnabled: Boolean? = null, volumeDownLongPressAction: Int? = null,
        volumeUpDoubleTapEnabled: Boolean? = null, volumeUpDoubleTapAction: Int? = null,
        volumeDownDoubleTapEnabled: Boolean? = null, volumeDownDoubleTapAction: Int? = null,
        quickMemoRecordingDisplayMode: Int? = null,
        voiceInputEnabled: Boolean? = null, floatingVoiceLongPressEnabled: Boolean? = null,
        floatingTextQuickMemoAutoPinEnabled: Boolean? = null,
        voiceQuickMemoAutoPinEnabled: Boolean? = null
    )
}
