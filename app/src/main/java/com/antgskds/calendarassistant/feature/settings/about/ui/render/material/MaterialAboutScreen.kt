package com.antgskds.calendarassistant.feature.settings.about.ui.render.material

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiAction
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiState
import com.antgskds.calendarassistant.feature.settings.shell.ui.render.material.component.SettingsEntryItem
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.settings.ActionSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.AppSettingsDivider

/**
 * 关于页。
 *
 * 版式参考同类应用：左对齐的卡片列表——
 * 应用信息（名称 / 版本 / 描述）→ 许可协议 → 源代码 → 更新与署名。
 *
 * ⚠️ **开发者选项的隐藏入口不能丢**：连点「Will do」标题 [DEVELOPER_UNLOCK_TAP_COUNT] 次解锁，
 * 这是本页唯一入口，改动版式时务必保留该 clickable 与其提示文案。
 *
 * 本页属于 `scrollWholePage = true` 的页面，**滚动由外层骨架负责，这里绝不能再加 verticalScroll**。
 */
@Composable
fun MaterialAboutScreen(
    state: AboutUiState,
    onAction: (AboutUiAction) -> Unit,
    onNavigateToAppUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics(state.hapticFeedbackEnabled)

    var titleTapCount by remember {
        mutableIntStateOf(0)
    }

    val cardTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val chevronTint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // —— 卡片一：应用信息（连点应用名解锁开发者选项）——
        AppSettingsCard(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = "Will do",
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable {
                    haptics.click()

                    if (state.developerOptionsUnlocked) {
                        Toast.makeText(
                            context,
                            "开发者选项已解锁",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        titleTapCount++

                        val remaining = DEVELOPER_UNLOCK_TAP_COUNT - titleTapCount

                        when {
                            remaining <= 0 -> {
                                titleTapCount = 0

                                onAction(AboutUiAction.UnlockDeveloperOptions)

                                Toast.makeText(
                                    context,
                                    "开发者选项已解锁，请前往实验室查看",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }

                            titleTapCount >= 2 -> {
                                Toast.makeText(
                                    context,
                                    "再点击 $remaining 次解锁开发者选项",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "版本 ${state.versionName}",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Remember less. Do more.\n让每件事都有着落",
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // —— 卡片二：许可协议（只读，仓库根目录 LICENSE 为 GNU GPL v3）——
        AppSettingsCard(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = "许可协议",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "GNU General Public License v3.0",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // —— 卡片三：源代码 ——
        AppSettingsCard {
            SettingsEntryItem(
                title = "源代码",
                subtitle = "github.com/AIXINJUELUOAI/Will-do",
                chevronTint = chevronTint,
                onClick = {
                    haptics.click()
                    onAction(AboutUiAction.OpenGithub)
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // —— 卡片四：更新与署名 ——
        AppSettingsCard {
            ActionSettingItem(
                title = "软件更新",
                subtitle = if (state.hasAppUpdate) "有新版本可用" else "检查新版本与版本日志",
                value = "v${state.versionName}",
                enabled = true,
                onClick = {
                    haptics.click()
                    onNavigateToAppUpdate()
                },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
            )

            if (state.hasDonated) {
                AppSettingsDivider()
                AboutInfoRow(
                    text = "感谢您的支持",
                    cardSubtitleStyle = cardSubtitleStyle,
                )
            }

            AppSettingsDivider()
            AboutInfoRow(
                text = "Designed by AIXINJUELUO_AI",
                cardSubtitleStyle = cardSubtitleStyle,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** 关于页内的只读信息行（作者、致谢等），不可点击。 */
@Composable
private fun AboutInfoRow(
    text: String,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = cardSubtitleStyle)
    }
}

private const val DEVELOPER_UNLOCK_TAP_COUNT = 5
