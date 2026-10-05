package com.antgskds.calendarassistant.feature.settings.shell.ui.render.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.feature.settings.shell.ui.render.material.component.SettingsEntryItem
import com.antgskds.calendarassistant.feature.settings.shell.ui.render.material.component.SettingsSectionCard
import com.antgskds.calendarassistant.feature.settings.shell.ui.render.material.component.SettingsSectionLabel
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics

/**
 * 功能设置二级页。
 *
 * 把与具体功能相关的配置（模型、天气、随口记、悬浮入口、日程与通知）从常规设置里
 * 收拢到一页，避免设置首页被功能入口淹没；本页只做入口聚合，各项配置仍在原页面维护。
 */
@Composable
fun FeatureHubSettingsPage(
    glassMode: Boolean,
    onNavigate: (SettingsDestination) -> Unit,
) {
    val haptics = rememberAppHaptics()
    val scrollState = rememberScrollState()
    val cardShape = RoundedCornerShape(28.dp)
    val cardColor = if (glassMode) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    }
    val chevronTint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp),
        ) {
            SettingsSectionLabel("功能")
            SettingsSectionCard(cardColor = cardColor, cardShape = cardShape) {
                SettingsEntryItem(
                    title = "模型与连接",
                    subtitle = "AI 模型与识别策略",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.AI) }
                )
                SettingsEntryItem(
                    title = "天气",
                    subtitle = "天气 API 与展示设置",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.Weather) }
                )
                SettingsEntryItem(
                    title = "随口记",
                    subtitle = "语音转写与录音展示",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.QuickMemo) }
                )
                SettingsEntryItem(
                    title = "随手记",
                    subtitle = "AI 摘要等条目行为",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.FlashNote) }
                )
                SettingsEntryItem(
                    title = "悬浮与侧边栏",
                    subtitle = "悬浮窗、侧边栏与悬浮球",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.FloatingEntry) }
                )
                SettingsEntryItem(
                    title = "日程与通知",
                    subtitle = "日历同步、提醒与通知",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.ScheduleNotification) }
                )
            }
        }
    }
}
