package com.antgskds.calendarassistant.feature.settings.shell.ui.contract

data class SettingsDetailUiState(
    val initialRoute: String,
    val uiSize: Int,
    val backgroundEnabled: Boolean,
    val backgroundMiuiBlurEnabled: Boolean,
    val backgroundCardAlphaPercent: Int,
    val pageHasAppBackground: Boolean,
    val isDarkMode: Boolean,
    val hasAppUpdate: Boolean,
    val hapticEnabled: Boolean,
    val navigationPredictiveBackEnabled: Boolean,
    val confirmationPredictiveBackEnabled: Boolean,
    val archiveCount: Int,
)

sealed interface SettingsDetailUiAction {
    data object ExitSettings : SettingsDetailUiAction
    data object Logout : SettingsDetailUiAction
    data class SetDarkMode(val isDark: Boolean) : SettingsDetailUiAction
    data object ClearArchives : SettingsDetailUiAction
}
