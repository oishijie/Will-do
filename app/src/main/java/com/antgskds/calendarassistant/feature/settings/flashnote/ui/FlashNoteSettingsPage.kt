package com.antgskds.calendarassistant.feature.settings.flashnote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.material.settings.SwitchSettingItem

/**
 * 随手记二级设置页。
 *
 * 与「随口记」并列：随口记管的是录音、悬浮入口与转写模型，随手记管的是
 * 收藏类条目自身的行为。目前只有 AI 摘要一项——随手记多为随手写下的一两句话，
 * 默认不调模型，避免在用户没察觉的情况下消耗额度。
 */
@Composable
fun FlashNoteSettingsPage(viewModel: SettingsViewModel, uiSize: Int = 2) {
    val settings by viewModel.settings.collectAsState()
    val bottomInset = LocalAppPageBottomPadding.current

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
    )
    val cardTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )

    CompositionLocalProvider(
        LocalAppHapticsEnabled provides settings.hapticFeedbackEnabled,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("随手记", style = sectionTitleStyle)

            AppSettingsCard {
                SwitchSettingItem(
                    title = "AI 摘要",
                    subtitle = "保存随手记时调用模型生成摘要；关闭后只保留原文，不消耗模型额度",
                    checked = settings.flashNoteAiSummaryEnabled,
                    onCheckedChange = { enabled -> viewModel.setFlashNoteAiSummaryEnabled(enabled) },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )
            }
        }
    }
}
