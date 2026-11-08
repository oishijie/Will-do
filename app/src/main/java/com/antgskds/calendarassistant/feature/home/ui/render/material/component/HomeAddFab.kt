package com.antgskds.calendarassistant.feature.home.ui.render.material.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.theme.material.background.rememberAppBackgroundStylePalette
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppFloatingActionSurface

/** 「+」按钮尺寸：比底部导航栏的 24dp 图标大一圈，视觉上更突出。 */
val HomeAddFabSize = 56.dp

/** 按钮底边与底部导航栏视觉顶边之间的间距。 */
val HomeAddFabGap = 16.dp

/** 按钮右侧与屏幕边缘的留白，与底部导航栏的左右留白保持一致。 */
val HomeAddFabEndPadding = 24.dp

/** 按钮底边距容器底部的高度（不含系统导航栏），供调用方定位。 */
val HomeAddFabBottomOffset = IntegratedFloatingBarVisualHeight + HomeAddFabGap

/** 列表滚到底时，最后一条内容与按钮顶边之间留出的余量。 */
val HomeAddFabListGap = 8.dp

/**
 * 今日日程 / 全部日程 / 闪记三页共用的「+」新建入口。
 *
 * 浮在底部导航栏正上方，底色与投影都取同一条悬浮栏的参数（壁纸模式不投影、普通模式 6dp），
 * 让两者处在同一空间层级。
 */
@Composable
fun HomeAddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT,
) {
    val haptics = rememberAppHaptics()
    val palette = rememberAppBackgroundStylePalette(
        enabled = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        cardAlphaPercent = MySettings.normalizeAppBackgroundCardAlphaPercent(cardAlphaPercent),
    )
    // 取底栏「选中态」那一组配色：同族但读起来是个动作，不与四个 tab 抢语义。
    val containerColor = if (backgroundMode) palette.accent else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (backgroundMode) palette.accentContent else MaterialTheme.colorScheme.onSecondaryContainer
    val elevation = if (backgroundMode) 0.dp else 6.dp

    AppFloatingActionSurface(
        modifier = modifier.size(HomeAddFabSize),
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 与 AppFloatingActionButton 同口径：内置触感关掉，由调用方决定反馈时机。
                .combinedClickable(
                    role = Role.Button,
                    hapticFeedbackEnabled = false,
                    onClick = {
                        haptics.click()
                        onClick()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "新建",
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
