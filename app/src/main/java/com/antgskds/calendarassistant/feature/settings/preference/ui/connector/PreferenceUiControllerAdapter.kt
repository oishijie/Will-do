package com.antgskds.calendarassistant.feature.settings.preference.ui.connector

import com.antgskds.calendarassistant.feature.settings.preference.ui.contract.PreferenceUiController
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel

class PreferenceUiControllerAdapter(private val viewModel: SettingsViewModel) : PreferenceUiController {
    override val settings = viewModel.settings
    override val syncStatus = viewModel.syncStatus
    override val availableSyncCalendars = viewModel.availableSyncCalendars

    override fun refreshSyncStatus() = viewModel.refreshSyncStatus()
    override fun refreshSyncCalendars() = viewModel.refreshSyncCalendars()
    override fun toggleCalendarSync(enabled: Boolean) = viewModel.toggleCalendarSync(enabled)
    override fun enableCalendarSyncAndSyncNow(callback: suspend (Result<Unit>) -> Unit) = viewModel.enableCalendarSyncAndSyncNow(callback)
    override fun updateSourceCalendars(ids: List<Long>, callback: suspend (Result<Unit>) -> Unit) = viewModel.updateSourceCalendars(ids, callback)
    override fun updateSyncIntervalSeconds(seconds: Int, callback: suspend (Result<Unit>) -> Unit) = viewModel.updateSyncIntervalSeconds(seconds, callback)
    override fun hasDuplicateAdvanceReminder(minutes: Int) = viewModel.hasDuplicateAdvanceReminder(minutes)
    override fun updateUiSize(size: Int) = viewModel.updateUiSize(size)
    override fun updateScreenshotDelay(delay: Long) = viewModel.updateScreenshotDelay(delay)
    override fun setRememberCalendarViewMode(enabled: Boolean) {
        viewModel.setRememberCalendarViewMode(enabled)
    }

    override fun setHomeCalendarViewMode(mode: String) {
        viewModel.setHomeCalendarViewMode(mode)
    }
    override fun updateQuickMemoAutoStop(enabled: Boolean?, seconds: Int?) =
        viewModel.updateQuickMemoAutoStop(enabled = enabled, seconds = seconds)
    override fun updateDailySummaryTimes(morningMinuteOfDay: Int?, eveningMinuteOfDay: Int?, onUpdated: () -> Unit) =
        viewModel.updateDailySummaryTimes(morningMinuteOfDay = morningMinuteOfDay, eveningMinuteOfDay = eveningMinuteOfDay, onUpdated = onUpdated)
    override fun updatePreference(
        showTomorrow: Boolean?, dailySummary: Boolean?, liveCapsule: Boolean?, pickupAggregation: Boolean?,
        hapticFeedbackEnabled: Boolean?, networkSpeedCapsule: Boolean?,
        floatingWindow: Boolean?, advanceReminderEnabled: Boolean?, advanceReminderMinutes: Int?,
        transitAutoCheckInEnabled: Boolean?, transitAutoCheckInMinutes: Int?, autoArchive: Boolean?,
        recognitionMode: Int?, defaultEventDurationMinutes: Int?,
        useMultimodalAi: Boolean?, disableThinking: Boolean?, floatingEventRange: Int?,
        floatingExpandSide: String?, floatingHandleSizePercent: Int?,
        floatingHandleTheme: String?,
        floatingHandleAutoHideSeconds: Int?,
        floatingEdgeLineEnabled: Boolean?,
        volumeUpLongPressEnabled: Boolean?, volumeUpLongPressAction: Int?, smsMonitoring: Boolean?,
        volumeDownLongPressEnabled: Boolean?, volumeDownLongPressAction: Int?,
        volumeUpDoubleTapEnabled: Boolean?, volumeUpDoubleTapAction: Int?,
        volumeDownDoubleTapEnabled: Boolean?, volumeDownDoubleTapAction: Int?,
        quickMemoRecordingDisplayMode: Int?, voiceInputEnabled: Boolean?,
        floatingVoiceLongPressEnabled: Boolean?, floatingTextQuickMemoAutoPinEnabled: Boolean?,
        voiceQuickMemoAutoPinEnabled: Boolean?
    ) = viewModel.updatePreference(
        showTomorrow = showTomorrow, dailySummary = dailySummary, liveCapsule = liveCapsule,
        pickupAggregation = pickupAggregation, hapticFeedbackEnabled = hapticFeedbackEnabled,
        networkSpeedCapsule = networkSpeedCapsule,
        floatingWindow = floatingWindow, advanceReminderEnabled = advanceReminderEnabled,
        advanceReminderMinutes = advanceReminderMinutes,
        transitAutoCheckInEnabled = transitAutoCheckInEnabled,
        transitAutoCheckInMinutes = transitAutoCheckInMinutes,
        autoArchive = autoArchive,
        recognitionMode = recognitionMode, defaultEventDurationMinutes = defaultEventDurationMinutes,
        useMultimodalAi = useMultimodalAi, disableThinking = disableThinking,
        floatingEventRange = floatingEventRange, floatingExpandSide = floatingExpandSide,
        floatingHandleSizePercent = floatingHandleSizePercent,
        floatingHandleTheme = floatingHandleTheme,
        floatingHandleAutoHideSeconds = floatingHandleAutoHideSeconds,
        floatingEdgeLineEnabled = floatingEdgeLineEnabled,
        volumeUpLongPressEnabled = volumeUpLongPressEnabled,
        volumeUpLongPressAction = volumeUpLongPressAction, smsMonitoring = smsMonitoring,
        volumeDownLongPressEnabled = volumeDownLongPressEnabled,
        volumeDownLongPressAction = volumeDownLongPressAction,
        volumeUpDoubleTapEnabled = volumeUpDoubleTapEnabled,
        volumeUpDoubleTapAction = volumeUpDoubleTapAction,
        volumeDownDoubleTapEnabled = volumeDownDoubleTapEnabled,
        volumeDownDoubleTapAction = volumeDownDoubleTapAction,
        quickMemoRecordingDisplayMode = quickMemoRecordingDisplayMode,
        voiceInputEnabled = voiceInputEnabled,
        floatingVoiceLongPressEnabled = floatingVoiceLongPressEnabled,
        floatingTextQuickMemoAutoPinEnabled = floatingTextQuickMemoAutoPinEnabled,
        voiceQuickMemoAutoPinEnabled = voiceQuickMemoAutoPinEnabled
    )
}
