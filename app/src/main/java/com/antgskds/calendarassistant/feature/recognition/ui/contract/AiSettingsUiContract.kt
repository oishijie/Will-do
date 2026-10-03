package com.antgskds.calendarassistant.feature.recognition.ui.contract

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

data class AiSettingsUiState(
    val settings: MySettings,
)

sealed interface AiSettingsUiAction {
    data class SaveMultimodalModel(val key: String, val name: String, val url: String) : AiSettingsUiAction
    data class SetAgentAccess(val enabled: Boolean) : AiSettingsUiAction
    data class SetThirdPartyAgentAccess(val enabled: Boolean) : AiSettingsUiAction
    data class SetAgentConnectionManagement(val enabled: Boolean) : AiSettingsUiAction
    data class SetRecognitionMode(val mode: Int) : AiSettingsUiAction
    data class SetDisableThinking(val enabled: Boolean) : AiSettingsUiAction
}
