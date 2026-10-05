package com.antgskds.calendarassistant.feature.settings.shell.ui.render.material.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarVisualHeight
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBar

/**
 * 设置页（首页底栏「设置」入口的原地内容）。
 *
 * 视觉规范：与随口记/日程页一致的居中小字页头（AppTopBar）
 * + 分组小标签 + 大圆角卡片组；条目 = 粗黑标题 + 灰色描述 + 右侧箭头，不使用前置图标。
 */
@Composable
fun SettingsTabPage(
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
        // 与随口记/日程页一致的居中小字页头
        AppTopBar(
            title = "设置",
            containerColor = if (glassMode) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp)
        ) {

            SettingsSectionLabel("常规设置")
            SettingsSectionCard(cardColor = cardColor, cardShape = cardShape) {
                SettingsEntryItem(
                    title = "通用偏好",
                    subtitle = "显示、操作与截图",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.Preference) }
                )
                SettingsEntryItem(
                    title = "主题设置",
                    subtitle = "深色模式与主题颜色",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.Theme) }
                )
            }

            SettingsSectionLabel("功能设置")
            SettingsSectionCard(cardColor = cardColor, cardShape = cardShape) {
                SettingsEntryItem(
                    title = "功能设置",
                    subtitle = "模型、天气、随口记、随手记、悬浮与日程",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.FeatureHub) }
                )
            }

            SettingsSectionLabel("数据")
            SettingsSectionCard(cardColor = cardColor, cardShape = cardShape) {
                SettingsEntryItem(
                    title = "日程归档",
                    subtitle = "查看历史日程",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.Archives) }
                )
                SettingsEntryItem(
                    title = "数据备份",
                    subtitle = "导入导出与多设备同步",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.Backup) }
                )
            }

            SettingsSectionLabel("其他")
            SettingsSectionCard(cardColor = cardColor, cardShape = cardShape) {
                SettingsEntryItem(
                    title = "实验室",
                    subtitle = "实验性功能",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.Laboratory) }
                )
                SettingsEntryItem(
                    title = "关于软件",
                    subtitle = "版本信息与帮助",
                    chevronTint = chevronTint,
                    onClick = { haptics.click(); onNavigate(SettingsDestination.About) }
                )
            }

            // 为常驻底栏与系统导航栏预留空间
            Spacer(
                modifier = Modifier.height(
                    IntegratedFloatingBarVisualHeight + 16.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            )
        }
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSectionCard(
    cardColor: Color,
    cardShape: RoundedCornerShape,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardColor)
            .padding(vertical = 6.dp)
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(22.dp))
}

/** 条目：粗黑标题 + 灰色描述 + 右侧箭头（参考稿样式，无前置图标）。 */
@Composable
fun SettingsEntryItem(
    title: String,
    subtitle: String,
    chevronTint: Color,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showBadge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = chevronTint,
            modifier = Modifier.size(24.dp)
        )
    }
}
