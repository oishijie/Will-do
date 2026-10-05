package com.antgskds.calendarassistant.feature.settings.about.ui.contract

data class AboutUiState(
    val versionName: String,
    val hasDonated: Boolean,
    val developerOptionsUnlocked: Boolean,
    val hapticFeedbackEnabled: Boolean,
    val hasAppUpdate: Boolean = false,
)

sealed interface AboutUiAction {
    data object OpenGithub : AboutUiAction
    data object OpenDonate : AboutUiAction
    data object UnlockDeveloperOptions : AboutUiAction
}
