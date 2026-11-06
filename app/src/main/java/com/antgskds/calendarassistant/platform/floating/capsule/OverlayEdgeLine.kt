package com.antgskds.calendarassistant.platform.floating.capsule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 自动隐藏后的贴边竖线（把手瘦身版）。
 *
 * 对齐声物记 _buildEdgeLine：窗口宽 20dp 是透明触摸缓冲，视觉线只有 4dp，
 * 贴停靠缘绘制、垂直居中；渐变深→浅（屏内端深、贴缘端浅），
 * 无论垫在白底还是黑底，总有一端可见。
 *
 * 点按回把手，朝屏幕内侧滑动直接展开面板。
 */
@Composable
fun OverlayEdgeLine(
    dockLeft: Boolean,
    onTap: () -> Unit,
    onSwipeInward: () -> Unit,
    tapEnabled: Boolean = true,
    marginDp: Int = 0,
    modifier: Modifier = Modifier,
) {
    // 屏内端深灰 → 贴缘端浅灰；方向随停靠侧镜像
    val gradient = if (dockLeft) {
        Brush.horizontalGradient(
            listOf(Color(0xD9707070), Color(0xD9B0B0B0))
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color(0xD9B0B0B0), Color(0xD9707070))
        )
    }

    Box(
        modifier = modifier
            .width(OverlayConstants.EDGE_LINE_WINDOW_WIDTH_DP)
            .height(OverlayConstants.EDGE_LINE_HEIGHT_DP)
            .pointerInput(dockLeft, onSwipeInward) {
                var expandOnRelease = false
                val thresholdPx = OverlayConstants.EDGE_SWIPE_THRESHOLD_DP.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { expandOnRelease = false },
                    onDragEnd = {
                        if (expandOnRelease) onSwipeInward()
                        expandOnRelease = false
                    },
                    onDragCancel = { expandOnRelease = false },
                    onHorizontalDrag = { change, dragAmount ->
                        if ((if (dockLeft) dragAmount else -dragAmount) > thresholdPx) {
                            expandOnRelease = true
                        }
                        change.consume()
                    },
                )
            }
            .clickable(
                enabled = tapEnabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onTap() },
        contentAlignment = if (dockLeft) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .width(OverlayConstants.EDGE_LINE_VISUAL_WIDTH_DP)
                .fillMaxHeight()
                .offset(x = (if (dockLeft) marginDp else -marginDp).dp)
                .background(gradient, RoundedCornerShape(2.dp)),
        )
    }
}
