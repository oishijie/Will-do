package com.antgskds.calendarassistant.platform.floating.capsule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 收起态边缘把手：闪念胶囊式双色药丸。
 *
 * 窗口恒 28×88，胶囊本体在其中按档位内缩——触控面积保持窗口整面积，
 * 视觉缩小不影响点击。
 *
 * @param dockLeft 停靠左缘；决定「向屏幕内侧滑动」的方向
 * @param sizePercent 把手尺寸档位（100 / 75 / 50）
 */
@Composable
fun OverlayHandle(
    onTap: () -> Unit,
    onSwipeInward: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    dockLeft: Boolean = false,
    sizePercent: Int = OverlayConstants.HANDLE_SIZE_DEFAULT_PERCENT,
    theme: HandleTheme = HandleTheme.DUO,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val showLabel = OverlayConstants.handleShowsLabel(sizePercent, theme)
    val capsuleWidth = OverlayConstants.handleInsetWidthOf(sizePercent).dp
    val capsuleHeight = OverlayConstants.handleInsetHeightOf(sizePercent).dp

    // 首次进入（面板收起 / 从媒体卡片回来）时播「隐藏后渐显」：
    // 前 300ms 全透明等窗口 resize 完成，后 180ms 从停靠缘向内滑入并渐显。
    // 之后再重组（拖动、换主题）不重播，避免每次交互都闪一下。
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.snapTo(0f)
        delay(OverlayConstants.HANDLE_REVEAL_DELAY_MS.toLong())
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = OverlayConstants.HANDLE_REVEAL_FADE_MS,
                easing = LinearEasing,
            ),
        )
    }
    // 滑入位移：value=0 时整块在停靠缘外侧（被窗口裁掉），value=1 归位
    val revealOffsetFraction = if (dockLeft) -(1f - reveal.value) else (1f - reveal.value)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val capsuleWidthPx = with(density) { capsuleWidth.toPx() }

    Box(
        modifier = modifier
            .size(OverlayConstants.HANDLE_WIDTH_DP.dp, OverlayConstants.HANDLE_HEIGHT_DP.dp)
            .pointerInput(dockLeft, sizePercent) {
                // 组合手势：detectTapGestures 处理点按 + 长按；
                // detectDragGestures 只在长按已发生后接管纵向拖动。
                // 两者共存时靠「长按后事件已被 tap 识别器消费」自然分流：
                // 长按未发生时拖动识别器看到的是未消费事件，不接管纵向。
                coroutineScope {
                    val armed = AtomicBoolean(false)
                    launch {
                        detectTapGestures(
                            onTap = { if (!armed.get()) onTap() },
                            onLongPress = {
                                armed.set(true)
                                dragging = true
                                onDragStart()
                            },
                        )
                    }
                    launch {
                        detectDragGestures(
                            onDragStart = {
                                if (!armed.get()) return@detectDragGestures
                                dragging = true
                            },
                            onDragEnd = {
                                if (!armed.get()) return@detectDragGestures
                                armed.set(false)
                                dragging = false
                                onDragEnd()
                            },
                            onDragCancel = {
                                if (!armed.get()) return@detectDragGestures
                                armed.set(false)
                                dragging = false
                                onDragEnd()
                            },
                        ) { change, dragAmount ->
                            val inwardAmount = if (dockLeft) dragAmount.x else -dragAmount.x
                            if (!armed.get()) {
                                // 横向内滑随时可展开；纵向忽略（避免误拖位置）
                                if (inwardAmount > OverlayConstants.EDGE_SWIPE_THRESHOLD_DP) {
                                    change.consume()
                                    onSwipeInward()
                                }
                                return@detectDragGestures
                            }
                            change.consume()
                            onDrag(dragAmount.y)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        HandleCapsule(
            width = capsuleWidth,
            height = capsuleHeight,
            theme = theme,
            showLabel = showLabel,
            mini = OverlayConstants.isMiniHandleSize(sizePercent),
            // 拖动时满不透明（拖动态视觉优先于渐显）
            alpha = if (dragging) 1f else OverlayConstants.HANDLE_RESTING_OPACITY * reveal.value,
            borderWidth = if (dragging) 1.5f else 1f,
            modifier = Modifier.graphicsLayer {
                // 向内滑入：value=0 时整块偏到停靠缘外侧
                translationX = revealOffsetFraction * capsuleWidthPx
            },
        )
    }
}

@Composable
private fun HandleCapsule(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    theme: HandleTheme,
    showLabel: Boolean,
    mini: Boolean,
    alpha: Float,
    borderWidth: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(width / 2)
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .alpha(alpha)
            .clip(shape)
            .border(borderWidth.dp, Color.White.copy(alpha = 0.9f), shape)
            .background(theme.capsuleTopColor),
    ) {
        if (theme.isPill3d) {
            Pill3dBody(width = width, height = height, shape = shape)
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 上半区（暖白），承载闪电图标
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(theme.capsuleTopColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (theme.showsIcon) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = theme.iconColor,
                            modifier = Modifier.size(
                                if (mini) OverlayConstants.HANDLE_ICON_SIZE_MINI_DP.dp
                                else OverlayConstants.HANDLE_ICON_SIZE_DP.dp
                            ),
                        )
                    }
                }
                // 中缝接缝线：两半接合处的平面投影
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(OverlayConstants.HANDLE_SEAM_COLOR),
                )
                // 下半区（绿），承载竖排文字
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(theme.capsuleBottomColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (showLabel) {
                        Text(
                            // 中文逐字竖排：每个汉字独占一行
                            text = OverlayConstants.HANDLE_LABEL.toCharArray().joinToString("\n"),
                            textAlign = TextAlign.Center,
                            fontSize = OverlayConstants.HANDLE_FONT_SIZE_SP.sp,
                            lineHeight = (OverlayConstants.HANDLE_FONT_SIZE_SP * 1.25f).sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 拟物胶囊立体感三层：中缝分界 + 下半暗部渐变 + 左侧高光条。
 * 纯造型，无图标无文字（对齐声物记 pill3d 主题）。
 */
@Composable
private fun Pill3dBody(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x14000000),
                        Color(0x33000000),
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY,
                )
            ),
    ) {
        // 中缝：50% 处的凹陷缝
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .height(1.dp)
                .background(OverlayConstants.HANDLE_SEAM_COLOR),
        )
        // 左侧高光条（圆柱反光）：竖长椭圆，顶白渐变到透明
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = width * 0.14f,
                    top = height * 0.07f,
                )
                .width(width * 0.2f)
                .height(height * 0.6f)
                .clip(RoundedCornerShape(width * 0.1f))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xA6FFFFFF), Color.Transparent),
                    )
                ),
        )
    }
}
