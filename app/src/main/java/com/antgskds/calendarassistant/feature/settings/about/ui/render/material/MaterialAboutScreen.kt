package com.antgskds.calendarassistant.feature.settings.about.ui.render.material

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiAction
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiState
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.settings.ActionSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.AppSettingsDivider

/**
 * 关于页。
 *
 * 与其他设置二级页统一为列表式版式：顶部保留品牌区（连点标题解锁开发者选项），
 * 底部为设置行；软件更新入口收进本页，不再单列二级页。
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // —— 品牌区：连点标题解锁开发者选项 ——
        Text(
            text = "Will do",
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
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

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = "v${state.versionName}",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = "Remember less. Do more.",
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = "让每件事都有着落",
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // —— 列表区 ——
        Column(modifier = Modifier.fillMaxWidth()) {
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
