package com.antgskds.calendarassistant.feature.settings.quickmemo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.quickmemo.data.asr.QuickMemoAsrModelStatus
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.settings.data.model.QuickMemoRecordingDisplayMode
import com.antgskds.calendarassistant.shared.ui.edition.EditionButton
import com.antgskds.calendarassistant.shared.ui.edition.EditionSwitchSliderSettingItem
import com.antgskds.calendarassistant.shared.ui.interaction.HapticValueChangeEffect
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.settings.AppSettingsDivider
import com.antgskds.calendarassistant.shared.ui.material.settings.SwitchSettingItem
import kotlin.math.roundToInt

/**
 * 随口记设置卡片。
 *
 * 从通用偏好设置抽出，供「随口记」二级页承载；行为与原通用偏好内完全一致，
 * 仅将原本私有的二选一控件与状态文案格式化收敛到本文件，避免重复实现。
 */
@Composable
fun QuickMemoPreferenceCard(
    settings: MySettings,
    asrModelStatus: QuickMemoAsrModelStatus,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    onVoiceInputEnabledChange: (Boolean) -> Unit,
    onFloatingLongPressChange: (Boolean) -> Unit,
    onRecordingDisplayModeChange: (Int) -> Unit,
    onAutoStopEnabledChange: (Boolean) -> Unit,
    onAutoStopSecondsChange: (Int) -> Unit,
    onTextAutoPinChange: (Boolean) -> Unit,
    onVoiceAutoPinChange: (Boolean) -> Unit,
    onImportAsrModel: () -> Unit,
) {
    AppSettingsCard {
        SwitchSettingItem(
            title = "随口记",
            subtitle = "随口记功能总开关",
            checked = settings.voiceInputEnabled,
            onCheckedChange = onVoiceInputEnabledChange,
            cardTitleStyle = cardTitleStyle,
            cardSubtitleStyle = cardSubtitleStyle,
        )

        AnimatedVisibility(
            visible = settings.voiceInputEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppSettingsDivider()
                SwitchSettingItem(
                    title = "悬浮窗长按随口记",
                    subtitle = "呼出悬浮窗后，再次长按音量+开始随口记录音",
                    checked = settings.floatingVoiceLongPressEnabled,
                    onCheckedChange = onFloatingLongPressChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )

                AppSettingsDivider()
                QuickMemoRecordingDisplayPreference(
                    mode = settings.quickMemoRecordingDisplayMode,
                    onModeChange = onRecordingDisplayModeChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )

                AppSettingsDivider()
                val normalizedSeconds = MySettings.normalizeQuickMemoAutoStopSeconds(
                    settings.quickMemoAutoStopSeconds,
                )
                HapticValueChangeEffect(valueKey = normalizedSeconds)
                EditionSwitchSliderSettingItem(
                    title = "自动结束录音",
                    subtitle = if (settings.quickMemoAutoStopEnabled) {
                        "录音 ${normalizedSeconds} 秒后自动保存"
                    } else {
                        "关闭时由用户手动结束并保存录音"
                    },
                    checked = settings.quickMemoAutoStopEnabled,
                    onCheckedChange = onAutoStopEnabledChange,
                    optionTitle = "自动结束时长",
                    optionSummary = "录音达到设定时长后自动保存",
                    value = normalizedSeconds.toFloat(),
                    valueText = "${normalizedSeconds} 秒",
                    onValueChange = { onAutoStopSecondsChange(it.roundToInt()) },
                    valueRange = MySettings.QUICK_MEMO_AUTO_STOP_MIN_SECONDS.toFloat()..
                        MySettings.QUICK_MEMO_AUTO_STOP_MAX_SECONDS.toFloat(),
                    steps = 0,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )

                AppSettingsDivider()
                SwitchSettingItem(
                    title = "文本随口记同步挂起",
                    subtitle = "随口记文本保存后，同步挂起到实况通知",
                    checked = settings.floatingTextQuickMemoAutoPinEnabled,
                    onCheckedChange = onTextAutoPinChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )

                AppSettingsDivider()
                SwitchSettingItem(
                    title = "语音随口记同步挂起",
                    subtitle = "随口记语音转写后，同步挂起到实况通知",
                    checked = settings.voiceQuickMemoAutoPinEnabled,
                    onCheckedChange = onVoiceAutoPinChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )

                AppSettingsDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "语音转写模型",
                            style = cardTitleStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = formatQuickMemoAsrModelStatus(asrModelStatus),
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    EditionButton(onClick = onImportAsrModel) {
                        Text(if (asrModelStatus.ready) "更换模型" else "导入模型")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMemoRecordingDisplayPreference(
    mode: Int,
    onModeChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    val normalizedMode = QuickMemoRecordingDisplayMode.normalize(mode)
    TwoOptionSettingItem(
        title = "录音展示",
        subtitle = if (normalizedMode == QuickMemoRecordingDisplayMode.FLOATING_WINDOW) {
            "所有入口录音时使用悬浮窗"
        } else {
            "所有入口录音时使用实况通知"
        },
        selectedValue = normalizedMode,
        firstValue = QuickMemoRecordingDisplayMode.LIVE_CAPSULE,
        firstLabel = "实况通知",
        secondValue = QuickMemoRecordingDisplayMode.FLOATING_WINDOW,
        secondLabel = "悬浮窗",
        onValueSelected = onModeChange,
        cardTitleStyle = cardTitleStyle,
        cardSubtitleStyle = cardSubtitleStyle,
    )
}

@Composable
private fun TwoOptionSettingItem(
    title: String,
    subtitle: String,
    selectedValue: Int,
    firstValue: Int,
    firstLabel: String,
    secondValue: Int,
    secondLabel: String,
    onValueSelected: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = cardTitleStyle)
            Text(subtitle, style = cardSubtitleStyle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedValue == firstValue) {
                Button(onClick = { haptics.selection(); onValueSelected(firstValue) }) {
                    Text(firstLabel)
                }
            } else {
                OutlinedButton(onClick = { haptics.selection(); onValueSelected(firstValue) }) {
                    Text(firstLabel)
                }
            }
            if (selectedValue == secondValue) {
                Button(onClick = { haptics.selection(); onValueSelected(secondValue) }) {
                    Text(secondLabel)
                }
            } else {
                OutlinedButton(onClick = { haptics.selection(); onValueSelected(secondValue) }) {
                    Text(secondLabel)
                }
            }
        }
    }
}

private fun formatQuickMemoAsrModelStatus(status: QuickMemoAsrModelStatus): String {
    return when {
        status.ready -> "已导入本地模型，可离线转写"
        !status.modelReady && !status.tokensReady -> "未导入模型，请依次导入 model.int8.onnx 和 tokens.txt"
        !status.modelReady -> "缺少 model.int8.onnx 或 model.onnx"
        else -> "缺少 tokens.txt"
    }
}
