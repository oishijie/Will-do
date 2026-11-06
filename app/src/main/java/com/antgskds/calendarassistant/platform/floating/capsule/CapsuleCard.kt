package com.antgskds.calendarassistant.platform.floating.capsule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 闪念胶囊式卡片。
 *
 * 尺寸策略对齐声物记 overlay_diary_card：**用估算的展开高度**驱动补间，
 * 不做运行时测量（参考实现同样是 _estimateExpandedHeight 估算 + 过估偏置）。
 *
 * 为什么不能测量：卡片在收起态只有 46dp 高，任何「测出展开高度」的隐藏层
 * 都会被这层高度约束住，量到的永远只有 46dp，展开就完全不变形。放进无约束
 * 容器又会在 measure 阶段写状态，触发 measure→recompose 循环。
 *
 * 所以这里用 rememberTextMeasurer 预先算出正文精确高度（文字排版是确定的），
 * 再叠加时间行、调色板、操作条的固定高度得出展开总高。
 */
@Composable
fun CapsuleCard(
    text: String,
    expanded: Boolean,
    expandedWidth: Dp,
    collapsedWidth: Dp,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    dockLeft: Boolean = false,
    fontSizeStep: Int = 0,
    backgroundColor: Color = OverlayConstants.DEFAULT_CARD_COLOR,
    archived: Boolean = false,
    timeText: String? = null,
    checkable: Boolean = false,
    checked: Boolean = false,
    onCheckChange: (Boolean) -> Unit = {},
    palette: List<Color>? = null,
    paletteSelectedIndex: Int = -1,
    onPaletteSelect: (Int) -> Unit = {},
    onSwipeDismiss: (() -> Unit)? = null,
    todoDone: Boolean = false,
    onTapOverride: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val textMeasurer = rememberTextMeasurer()

    // 宽度防御：极窄窗口下面板宽度可能小于卡片最小宽
    val safeExpanded = expandedWidth.coerceAtLeast(OverlayConstants.CARD_MIN_WIDTH)
    val safeCollapsed = collapsedWidth.coerceAtLeast(OverlayConstants.CARD_MIN_WIDTH)

    // 展开态总高（估算，文字部分由 TextMeasurer 精确量出）
    // measure 必须显式传 density/layoutDirection/fontFamilyResolver：TextMeasurer.measure
    // 的默认 density 是 Density(1f)，在 2.75 倍屏上量出来的高度只有实际的 1/2.75，
    // 展开卡会被算矮，底部 footer 直接被 clipToBounds 裁掉。
    val expandedHeight: Dp = remember(
        text, timeText, safeExpanded, palette?.size, footer != null, archived, fontSizeStep, checkable,
        density, layoutDirection, fontFamilyResolver,
    ) {
        // 正文可用宽要扣掉左右内边距和左右边框（对齐参考实现
        // _estimateExpandedHeight 的 maxWidth - 2*cardHPadding - 2*cardBorderWidth）：
        // 少扣边框会让可用宽偏大、行数算少、展开高偏低。
        val contentWidthPx = with(density) {
            (safeExpanded -
                (OverlayConstants.CARD_H_PADDING + OverlayConstants.CARD_BORDER_WIDTH_DP.dp) * 2)
                .toPx()
        }.toInt().coerceAtLeast(1)
        // 展开态内联勾选框占首行宽度（参考实现用 WidgetSpan 占位）：
        // 测量时同扣，否则首行可用宽偏大、行数算少导致展开高偏低。
        val bodyWidthPx = with(density) {
            (contentWidthPx.toDp() - if (checkable) {
                OverlayConstants.CARD_CHECK_HIT_SIZE + OverlayConstants.CARD_CHECK_GAP
            } else 0.dp).toPx()
        }.toInt().coerceAtLeast(1)

        val bodyStyle = TextStyle(
            fontSize = (OverlayConstants.BODY_FONT_SIZE_SP + fontSizeStep).sp,
            lineHeight = ((OverlayConstants.BODY_FONT_SIZE_SP + fontSizeStep) * OverlayConstants.BODY_LINE_HEIGHT).sp,
        )
        val timeStyle = TextStyle(fontSize = (OverlayConstants.CARD_TIME_FONT_SIZE_SP + fontSizeStep).sp)

        // 参考实现展开卡片只有 cardHPadding，没有垂直内边距（垂直间距由时间行/
        // 操作条自身撑开）。原来上下各 10dp 会让操作条下方多出一截空白。
        var total = OverlayConstants.CARD_V_PADDING.value

        val timeH = if (!timeText.isNullOrBlank()) with(density) {
            textMeasurer.measure(
                text = timeText,
                style = timeStyle,
                maxLines = 1,
                constraints = Constraints(maxWidth = contentWidthPx),
                layoutDirection = layoutDirection,
                density = density,
                fontFamilyResolver = fontFamilyResolver,
            ).size.height.toDp().value
        } else 0f
        total += maxOf(timeH, 32f) + OverlayConstants.CARD_TIME_GAP.value

        val bodyH = with(density) {
            textMeasurer.measure(
                text = text,
                style = bodyStyle,
                constraints = Constraints(maxWidth = bodyWidthPx),
                layoutDirection = layoutDirection,
                density = density,
                fontFamilyResolver = fontFamilyResolver,
            ).size.height.toDp().value
        }
        total += bodyH

        if (!palette.isNullOrEmpty()) {
            total += 10f + 24f
        }
        if (footer != null) {
            total += OverlayConstants.CARD_FOOTER_TOP_GAP.value +
                1f + // 分隔线
                OverlayConstants.CARD_FOOTER_DIVIDER_GAP.value +
                32f // 操作条（「图标 + 文字」行高，非纯图标的 40dp 命中区）
        }
        // 实际展开高度只保留必要的排版余量，不把参考收卷公式的过估偏置画成底部空白。
        (total + 4f).dp
    }

    // 两个轴向共用同一时长与曲线，保证宽高同拍（声物记 cardResizeCurve = linear）
    val spec = tween<Dp>(OverlayConstants.ANIMATION_DURATION_MS, easing = LinearEasing)
    val currentWidth by animateDpAsState(
        targetValue = if (expanded) safeExpanded else safeCollapsed,
        animationSpec = spec,
        label = "capsule_card_width",
    )
    val currentHeight by animateDpAsState(
        targetValue = if (expanded) expandedHeight else OverlayConstants.CARD_HEIGHT,
        animationSpec = spec,
        label = "capsule_card_height",
    )
    val radius by animateDpAsState(
        targetValue = if (expanded) OverlayConstants.CARD_EXPANDED_RADIUS_DP.dp else OverlayConstants.CARD_HEIGHT / 2,
        animationSpec = tween(OverlayConstants.ANIMATION_DURATION_MS),
        label = "capsule_card_radius",
    )
    val expandedContentAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = OverlayConstants.ANIMATION_DURATION_MS / 2,
            delayMillis = if (expanded) OverlayConstants.ANIMATION_DURATION_MS / 2 else 0,
            easing = LinearEasing,
        ),
        label = "capsule_card_expanded_alpha",
    )
    val collapsedContentAlpha by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        animationSpec = tween(
            durationMillis = OverlayConstants.ANIMATION_DURATION_MS / 2,
            delayMillis = if (expanded) 0 else OverlayConstants.ANIMATION_DURATION_MS / 2,
            easing = LinearEasing,
        ),
        label = "capsule_card_collapsed_alpha",
    )
    val expandedInteractive = expanded && expandedContentAlpha >= 1f
    val collapsedInteractive = !expanded && collapsedContentAlpha >= 1f

    val swipeAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(radius)
    val fill = if (archived) backgroundColor.copy(alpha = 0.45f) else backgroundColor

    Box(
        modifier = modifier
            .graphicsLayer { translationX = swipeAnim.value }
            .width(currentWidth)
            .height(currentHeight)
            .clipToBounds()
            .clip(shape)
            .background(fill)
            .border(BorderStroke(OverlayConstants.CARD_BORDER_WIDTH_DP.dp, Color.White), shape)
            .pointerInput(onSwipeDismiss, dockLeft) {
                if (onSwipeDismiss == null) return@pointerInput
                val dismissSign = if (dockLeft) 1f else -1f
                val widthPx = currentWidth.toPx()
                val threshold = widthPx * OverlayConstants.CARD_SWIPE_THRESHOLD_RATIO
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val current = swipeAnim.value
                        val towardDismiss = current * dismissSign > threshold
                        val target = if (towardDismiss) dismissSign * widthPx else 0f
                        scope.launch {
                            swipeAnim.animateTo(target, tween(160, easing = LinearEasing))
                            if (towardDismiss) onSwipeDismiss()
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            swipeAnim.animateTo(0f, tween(160, easing = LinearEasing))
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { swipeAnim.snapTo(swipeAnim.value + dragAmount) }
                }
            }
            .clickable(
                enabled = collapsedInteractive,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { if (onTapOverride != null) onTapOverride() else onToggleExpanded() },
        contentAlignment = if (dockLeft) Alignment.TopStart else Alignment.TopEnd,
    ) {
        if (expanded || expandedContentAlpha > 0f) {
            Column(
                modifier = Modifier
                    .zIndex(if (expanded) 1f else 0f)
                    .wrapContentSize(
                        align = if (dockLeft) Alignment.TopStart else Alignment.TopEnd,
                        unbounded = true,
                    )
                    .requiredWidth(safeExpanded)
                    .drawWithContent {
                        val clipWidth = currentWidth.toPx().coerceIn(0f, size.width)
                        val clipHeight = currentHeight.toPx().coerceIn(0f, size.height)
                        clipRect(
                            left = if (dockLeft) 0f else size.width - clipWidth,
                            top = 0f,
                            right = if (dockLeft) clipWidth else size.width,
                            bottom = clipHeight,
                        ) { this@drawWithContent.drawContent() }
                    }
                    .graphicsLayer { alpha = expandedContentAlpha }
                    // 底部不留垂直内边距（对齐参考实现：只有 cardHPadding），
                    // 否则操作条下方会多一截空白。
                    .padding(
                        start = OverlayConstants.CARD_H_PADDING,
                        end = OverlayConstants.CARD_H_PADDING,
                        top = OverlayConstants.CARD_V_PADDING,
                    ),
            ) {
                ExpandedContent(
                    text = text,
                    timeText = timeText,
                    palette = palette,
                    paletteSelectedIndex = paletteSelectedIndex,
                    onPaletteSelect = if (expandedInteractive) onPaletteSelect else { _ -> },
                    footer = footer,
                    onCollapse = onToggleExpanded,
                    actionsEnabled = expandedInteractive,
                    fontSizeStep = fontSizeStep,
                    checkable = checkable,
                    checked = checked,
                    checkEnabled = expandedInteractive,
                    onCheckChange = onCheckChange,
                )
            }
        }
        if (collapsedContentAlpha > 0f) {
            Row(
                modifier = Modifier
                    .zIndex(if (expanded) 0f else 1f)
                    .width(safeCollapsed)
                    .height(OverlayConstants.CARD_HEIGHT)
                    .graphicsLayer { alpha = collapsedContentAlpha }
                    .padding(horizontal = OverlayConstants.CARD_H_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checkable) {
                    CapsuleCheckCircle(
                        checked = checked,
                        onCheckChange = onCheckChange,
                        enabled = collapsedInteractive,
                    )
                    Spacer(Modifier.size(OverlayConstants.CARD_CHECK_GAP))
                }
                Text(
                    text = text.replace('\n', ' '),
                    style = TextStyle(
                        fontSize = (OverlayConstants.CARD_COLLAPSED_FONT_SIZE_SP + fontSizeStep).sp,
                        color = if (archived) Color.White.copy(alpha = 0.75f) else Color.White,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (todoDone) TextDecoration.LineThrough else null,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** 展开正文内联归档勾选框的占位 id。 */
private const val EXPANDED_CHECK_INLINE_ID = "capsule-card-check"

@Composable
private fun CapsuleCheckCircle(
    checked: Boolean,
    onCheckChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(OverlayConstants.CARD_CHECK_HIT_SIZE)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onCheckChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(OverlayConstants.CARD_CHECK_VISUAL_SIZE)
                .clip(CircleShape)
                .then(
                    if (checked) Modifier.background(Color.White)
                    else Modifier.border(1.8.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "已归档",
                    tint = Color(0xFF37474F),
                    modifier = Modifier.size(OverlayConstants.CARD_CHECK_ICON_SIZE),
                )
            }
        }
    }
}

@Composable
private fun ExpandedContent(
    text: String,
    timeText: String?,
    palette: List<Color>?,
    paletteSelectedIndex: Int,
    onPaletteSelect: (Int) -> Unit,
    footer: (@Composable () -> Unit)?,
    onCollapse: () -> Unit,
    actionsEnabled: Boolean,
    fontSizeStep: Int,
    checkable: Boolean,
    checked: Boolean,
    checkEnabled: Boolean,
    onCheckChange: (Boolean) -> Unit,
) {
    // 展开态归档勾选框内联在正文首行（对齐参考实现 WidgetSpan 占位）：
    // 用 InlineTextContent 而不是外层 Row，否则后续行会被一起缩进。
    val checkPlaceholder = remember(checkable, checked, checkEnabled) {
        if (!checkable) null else InlineTextContent(
            Placeholder(
                // Placeholder 宽高是 TextUnit（随字号缩放），不能直接传 Dp
                width = (OverlayConstants.CARD_CHECK_HIT_SIZE.value +
                    OverlayConstants.CARD_CHECK_GAP.value).sp,
                height = OverlayConstants.CARD_CHECK_HIT_SIZE.value.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CapsuleCheckCircle(
                    checked = checked,
                    onCheckChange = onCheckChange,
                    enabled = checkEnabled,
                )
            }
        }
    }
    val bodyText = remember(text, checkable) {
        if (!checkable) AnnotatedString(text) else buildAnnotatedString {
            appendInlineContent(id = EXPANDED_CHECK_INLINE_ID, alternateText = "[归档]")
            append(text)
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!timeText.isNullOrBlank()) {
                Text(
                    text = timeText,
                    style = TextStyle(
                        fontSize = (OverlayConstants.CARD_TIME_FONT_SIZE_SP + fontSizeStep).sp,
                        color = Color.White.copy(alpha = 0.75f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 32.dp)
                    .clickable(enabled = actionsEnabled) { onCollapse() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ExpandLess,
                    contentDescription = "收起卡片",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(OverlayConstants.CARD_TIME_GAP))
        Text(
            text = bodyText,
            style = TextStyle(
                fontSize = (OverlayConstants.BODY_FONT_SIZE_SP + fontSizeStep).sp,
                lineHeight = ((OverlayConstants.BODY_FONT_SIZE_SP + fontSizeStep) * OverlayConstants.BODY_LINE_HEIGHT).sp,
                color = Color.White,
            ),
            inlineContent = if (checkPlaceholder != null) {
                mapOf(EXPANDED_CHECK_INLINE_ID to checkPlaceholder)
            } else {
                emptyMap()
            },
        )
        if (!palette.isNullOrEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEachIndexed { index, swatch ->
                    val selected = index == paletteSelectedIndex
                    Box(
                        modifier = Modifier
                            .size(if (selected) 24.dp else 22.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                                shape = CircleShape,
                            )
                            .clickable(
                                enabled = actionsEnabled,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onPaletteSelect(index) },
                    )
                }
            }
        }
        val content = footer
        if (content != null) {
            Spacer(Modifier.height(OverlayConstants.CARD_FOOTER_TOP_GAP))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OverlayConstants.CARD_BORDER_WIDTH_DP.dp)
                    .background(Color.White.copy(alpha = 0.3f)),
            )
            Spacer(Modifier.height(OverlayConstants.CARD_FOOTER_DIVIDER_GAP))
            // 操作条紧凑居中：图标之间 8dp，整组水平居中，不铺满整行——
            // 铺满（SpaceEvenly）会让三个图标散得很开，卡片看着又宽又空。
            Box(modifier = Modifier.fillMaxWidth().height(32.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) { content() }
                if (!actionsEnabled) {
                    Box(modifier = Modifier.matchParentSize().clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {})
                }
            }
        }
    }
}
