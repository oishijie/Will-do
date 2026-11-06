package com.antgskds.calendarassistant.feature.quickmemo.domain.model

enum class QuickMemoVoiceCaptureStatus {
    IDLE,
    RECORDING,
    CONFIRMING,
    SAVING,
    TRANSCRIBING,
    SAVED,
    TOO_SHORT,
    ERROR
}

data class QuickMemoVoiceCaptureState(
    val status: QuickMemoVoiceCaptureStatus = QuickMemoVoiceCaptureStatus.IDLE,
    val tempAudioPath: String? = null,
    val durationMs: Long = 0L,
    val message: String = "",
    /** TRANSCRIBING 期间跟踪的闪记 id，用于比对转写终态。 */
    val memoId: Long? = null
) {
    val isActive: Boolean get() = status != QuickMemoVoiceCaptureStatus.IDLE
}
