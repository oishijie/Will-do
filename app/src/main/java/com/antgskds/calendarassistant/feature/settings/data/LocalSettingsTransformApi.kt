package com.antgskds.calendarassistant.feature.settings.data

import com.antgskds.calendarassistant.shared.query.SettingsTransformApi
import com.antgskds.calendarassistant.feature.settings.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.feature.settings.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.settings.data.model.QuickMemoRecordingDisplayMode

class LocalSettingsTransformApi : SettingsTransformApi {
    override fun applyPreferenceUpdate(
        current: MySettings,
        showTomorrow: Boolean?,
        dailySummary: Boolean?,
        dailySummaryMorningMinuteOfDay: Int?,
        dailySummaryEveningMinuteOfDay: Int?,
        liveCapsule: Boolean?,
        braceletModeEnabled: Boolean?,
        pickupAggregation: Boolean?,
        hapticFeedbackEnabled: Boolean?,
        networkSpeedCapsule: Boolean?,
        floatingWindow: Boolean?,
        advanceReminderEnabled: Boolean?,
        advanceReminderMinutes: Int?,
        transitAutoCheckInEnabled: Boolean?,
        transitAutoCheckInMinutes: Int?,
        autoArchive: Boolean?,
        recognitionMode: Int?,
        defaultEventDurationMinutes: Int?,
        useMultimodalAi: Boolean?,
        disableThinking: Boolean?,
        localSemanticEnabled: Boolean?,
        selectedLocalModelId: String?,
        floatingEventRange: Int?,
        floatingHandleSizePercent: Int?,
        floatingHandleTheme: String?,
        floatingPanelMaxCards: Int?,
        floatingFontSizeStep: Int?,
        floatingHandleAutoHideSeconds: Int?,
        floatingEdgeLineEnabled: Boolean?,
        floatingEdgeLineTapEnabled: Boolean?,
        floatingEdgeLineMarginDp: Int?,
        floatingExpandSide: String?,
        quickMemoRecordingDisplayMode: Int?,
        volumeUpLongPressEnabled: Boolean?,
        volumeUpLongPressAction: Int?,
        volumeDownLongPressEnabled: Boolean?,
        volumeDownLongPressAction: Int?,
        volumeUpDoubleTapEnabled: Boolean?,
        volumeUpDoubleTapAction: Int?,
        volumeDownDoubleTapEnabled: Boolean?,
        volumeDownDoubleTapAction: Int?,
        smsMonitoring: Boolean?,
        forceInstantCodeTimeToNow: Boolean?,
        predictiveBackEnabled: Boolean?,
        clipboardCodeRecognitionEnabled: Boolean?,
        imagePinEnabled: Boolean?,
        voiceInputEnabled: Boolean?,
        floatingVoiceLongPressEnabled: Boolean?,
        floatingTextQuickMemoAutoPinEnabled: Boolean?,
        voiceQuickMemoAutoPinEnabled: Boolean?,
        appBackgroundCardAlphaPercent: Int?,
        widgetThemeMode: Int?,
        widgetBackgroundAlpha: Float?,
        developerOptionsUnlocked: Boolean?,
        developerOptionsEnabled: Boolean?,
        developerOptionsDisabledAtMillis: Long?,
        developerSimulateRootEnabled: Boolean?,
        weatherLocationStabilityRequiredHits: Int?,
        liveNotificationTemplateMode: String?
    ): MySettings {
        var updated = current
        if (showTomorrow != null) updated = updated.copy(showTomorrowEvents = showTomorrow)
        if (dailySummary != null) updated = updated.copy(isDailySummaryEnabled = dailySummary)
        if (dailySummaryMorningMinuteOfDay != null) {
            updated = updated.copy(
                dailySummaryMorningMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(dailySummaryMorningMinuteOfDay)
            )
        }
        if (dailySummaryEveningMinuteOfDay != null) {
            updated = updated.copy(
                dailySummaryEveningMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(dailySummaryEveningMinuteOfDay)
            )
        }
        if (liveCapsule != null) updated = updated.copy(isLiveCapsuleEnabled = liveCapsule)
        if (braceletModeEnabled != null) updated = updated.copy(braceletModeEnabled = braceletModeEnabled)
        if (pickupAggregation != null) updated = updated.copy(isPickupAggregationEnabled = pickupAggregation)
        if (hapticFeedbackEnabled != null) updated = updated.copy(hapticFeedbackEnabled = hapticFeedbackEnabled)
        if (networkSpeedCapsule != null) updated = updated.copy(isNetworkSpeedCapsuleEnabled = networkSpeedCapsule)
        if (floatingWindow != null) updated = updated.copy(isFloatingWindowEnabled = floatingWindow)
        if (advanceReminderEnabled != null) updated = updated.copy(isAdvanceReminderEnabled = advanceReminderEnabled)
        if (advanceReminderMinutes != null) updated = updated.copy(advanceReminderMinutes = advanceReminderMinutes)
        if (transitAutoCheckInEnabled != null) {
            updated = updated.copy(transitAutoCheckInEnabled = transitAutoCheckInEnabled)
        }
        if (transitAutoCheckInMinutes != null) {
            updated = updated.copy(
                transitAutoCheckInMinutes = MySettings.normalizeTransitAutoCheckInMinutes(transitAutoCheckInMinutes)
            )
        }
        if (autoArchive != null) updated = updated.copy(autoArchiveEnabled = autoArchive)
        if (recognitionMode != null) updated = updated.copy(recognitionMode = MySettings.normalizeRecognitionMode(recognitionMode))
        if (defaultEventDurationMinutes != null) updated = updated.copy(defaultEventDurationMinutes = defaultEventDurationMinutes)
        // 兼容旧调用方的参数，已下架的文本模式不能被重新启用。
        if (useMultimodalAi != null) updated = updated.copy(useMultimodalAi = true)
        if (disableThinking != null) updated = updated.copy(disableThinking = disableThinking)
        if (localSemanticEnabled != null) updated = updated.copy(isLocalSemanticEnabled = localSemanticEnabled)
        if (selectedLocalModelId != null) updated = updated.copy(selectedLocalModelId = selectedLocalModelId)
        if (floatingEventRange != null) updated = updated.copy(floatingEventRange = floatingEventRange)
        if (floatingHandleSizePercent != null) updated = updated.copy(
            floatingHandleSizePercent = if (floatingHandleSizePercent in listOf(100, 75, 50)) floatingHandleSizePercent else 100
        )
        if (floatingHandleTheme != null) updated = updated.copy(
            floatingHandleTheme = if (floatingHandleTheme in listOf("DUO", "BLUE_PURPLE", "PILL3D")) floatingHandleTheme else "DUO"
        )
        if (floatingPanelMaxCards != null) updated = updated.copy(
            floatingPanelMaxCards = floatingPanelMaxCards.coerceIn(6, 10)
        )
        if (floatingFontSizeStep != null) updated = updated.copy(
            floatingFontSizeStep = floatingFontSizeStep.coerceIn(-2, 2)
        )
        if (floatingHandleAutoHideSeconds != null) updated = updated.copy(
            floatingHandleAutoHideSeconds = floatingHandleAutoHideSeconds
        )
        if (floatingEdgeLineEnabled != null) updated = updated.copy(
            floatingEdgeLineEnabled = floatingEdgeLineEnabled
        )
        if (floatingEdgeLineTapEnabled != null) updated = updated.copy(
            floatingEdgeLineTapEnabled = floatingEdgeLineTapEnabled
        )
        if (floatingEdgeLineMarginDp != null) updated = updated.copy(
            floatingEdgeLineMarginDp = if (floatingEdgeLineMarginDp in listOf(0, 4, 8)) floatingEdgeLineMarginDp else 0
        )
        if (floatingExpandSide != null) updated = updated.copy(floatingExpandSide = sanitizeFloatingSide(floatingExpandSide))
        if (quickMemoRecordingDisplayMode != null) {
            updated = updated.copy(
                quickMemoRecordingDisplayMode = QuickMemoRecordingDisplayMode.normalize(quickMemoRecordingDisplayMode)
            )
        }
        if (volumeUpLongPressEnabled != null) updated = updated.copy(volumeUpLongPressEnabled = volumeUpLongPressEnabled)
        if (volumeUpLongPressAction != null) updated = updated.copy(volumeUpLongPressAction = volumeUpLongPressAction.coerceIn(0, 7))
        if (volumeDownLongPressEnabled != null) updated = updated.copy(volumeDownLongPressEnabled = volumeDownLongPressEnabled)
        if (volumeDownLongPressAction != null) updated = updated.copy(volumeDownLongPressAction = volumeDownLongPressAction.coerceIn(0, 7))
        if (volumeUpDoubleTapEnabled != null) updated = updated.copy(volumeUpDoubleTapEnabled = volumeUpDoubleTapEnabled)
        if (volumeUpDoubleTapAction != null) updated = updated.copy(volumeUpDoubleTapAction = volumeUpDoubleTapAction.coerceIn(0, 7))
        if (volumeDownDoubleTapEnabled != null) updated = updated.copy(volumeDownDoubleTapEnabled = volumeDownDoubleTapEnabled)
        if (volumeDownDoubleTapAction != null) updated = updated.copy(volumeDownDoubleTapAction = volumeDownDoubleTapAction.coerceIn(0, 7))
        if (smsMonitoring != null) updated = updated.copy(isSmsMonitoringEnabled = smsMonitoring)
        if (forceInstantCodeTimeToNow != null) updated = updated.copy(forceInstantCodeTimeToNow = forceInstantCodeTimeToNow)
        if (predictiveBackEnabled != null) updated = updated.copy(predictiveBackEnabled = predictiveBackEnabled)
        if (imagePinEnabled != null) updated = updated.copy(imagePinEnabled = imagePinEnabled)
        if (clipboardCodeRecognitionEnabled != null) updated = updated.copy(clipboardCodeRecognitionEnabled = clipboardCodeRecognitionEnabled)
        if (voiceInputEnabled != null) updated = updated.copy(voiceInputEnabled = voiceInputEnabled)
        if (floatingVoiceLongPressEnabled != null) updated = updated.copy(floatingVoiceLongPressEnabled = floatingVoiceLongPressEnabled)
        if (floatingTextQuickMemoAutoPinEnabled != null) {
            updated = updated.copy(floatingTextQuickMemoAutoPinEnabled = floatingTextQuickMemoAutoPinEnabled)
        }
        if (voiceQuickMemoAutoPinEnabled != null) {
            updated = updated.copy(voiceQuickMemoAutoPinEnabled = voiceQuickMemoAutoPinEnabled)
        }
        if (appBackgroundCardAlphaPercent != null) {
            updated = updated.copy(
                appBackgroundCardAlphaPercent = MySettings.normalizeAppBackgroundCardAlphaPercent(appBackgroundCardAlphaPercent)
            )
        }
        if (widgetThemeMode != null) updated = updated.copy(widgetThemeMode = widgetThemeMode.coerceIn(0, 2))
        if (widgetBackgroundAlpha != null) updated = updated.copy(widgetBackgroundAlpha = widgetBackgroundAlpha.coerceIn(0.6f, 1f))
        if (developerOptionsUnlocked != null) updated = updated.copy(developerOptionsUnlocked = developerOptionsUnlocked)
        if (developerOptionsEnabled != null) updated = updated.copy(developerOptionsEnabled = developerOptionsEnabled)
        if (developerOptionsDisabledAtMillis != null) updated = updated.copy(developerOptionsDisabledAtMillis = developerOptionsDisabledAtMillis)
        if (developerSimulateRootEnabled != null) updated = updated.copy(developerSimulateRootEnabled = developerSimulateRootEnabled)
        if (weatherLocationStabilityRequiredHits != null) {
            updated = updated.copy(weatherLocationStabilityRequiredHits = weatherLocationStabilityRequiredHits.coerceIn(1, 3))
        }
        if (liveNotificationTemplateMode != null) {
            updated = updated.copy(liveNotificationTemplateMode = LiveNotificationTemplateMode.normalize(liveNotificationTemplateMode))
        }

        return updated.copy(
            volumeUpLongPressAction = updated.volumeUpLongPressAction.coerceIn(0, 7),
            volumeDownLongPressAction = updated.volumeDownLongPressAction.coerceIn(0, 7),
            volumeUpDoubleTapAction = updated.volumeUpDoubleTapAction.coerceIn(0, 7),
            volumeDownDoubleTapAction = updated.volumeDownDoubleTapAction.coerceIn(0, 7)
        )
    }

    private fun sanitizeFloatingSide(side: String): String {
        return if (side == "LEFT") "LEFT" else "RIGHT"
    }
}
