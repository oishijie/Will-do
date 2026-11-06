package com.antgskds.calendarassistant.platform.floating.capsule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp

/**
 * 闪念胶囊式面板。
 *
 * 宽度取窗口宽的 [OverlayConstants.EXPANDED_WIDTH_RATIO]（有卡片展开时
 * [OverlayConstants.EXPANDED_PANEL_WIDTH_RATIO]），贴停靠侧上角，高度随内容
 * 自适应——列表条目少时收缩到内容高，多时占满并在内部滚动。
 *
 * 与全屏浮层的根本区别：没有全屏遮罩，空白区域不拦截点击；关闭靠面板外的
 * 窗口外事件或把手反向拖动。
 */
@Composable
fun CapsulePanel(
    items: List<CapsuleItem>,
    expandedIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onItemAction: (CapsuleItem, CapsuleAction) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    dockLeft: Boolean = false,
    panelMaxCards: Int = OverlayConstants.PANEL_MAX_CARDS_DEFAULT,
    fontSizeStep: Int = 0,
    onDismiss: () -> Unit = {},
    /** 顶部工具条的「+」新建完成：把输入的文字落成一条闪记。 */
    onCreateNote: (String) -> Unit = {},
    /** 勾选/取消勾选卡片左侧的归档框。 */
    onCheckChange: (CapsuleItem, Boolean) -> Unit = { _, _ -> },
    /** 展开态点调色板换色。 */
    onPaletteSelect: (CapsuleItem, Int) -> Unit = { _, _ -> },
    /** 点「闹钟」时的初始时间猜测（按卡片文字识别，识别不到给兜底值）。 */
    resolveInitialTime: (CapsuleItem) -> java.time.LocalDateTime = { defaultCapsulePickerTime() },
    /** 日期时间选完确认：参数是选定时间与是否响铃。 */
    onTimeConfirmed: (CapsuleItem, java.time.LocalDateTime, Boolean) -> Unit = { _, _, _ -> },
    /** 工具条的「全部展开/收起」：参数 true = 全部展开，false = 全部收起。 */
    onToggleExpandAll: (Boolean) -> Unit = {},
    /** 工具条的「打开闪记」：跳主 App 闪记页。 */
    onOpenQuickMemo: () -> Unit = {},
    /** 卡片划走：归档或删除；null = 不启用划走。 */
    onSwipeDismiss: ((CapsuleItem) -> Unit)? = null,
    /** 划走删除后的撤销提示文案；null = 不显示撤销胶囊。 */
    undoMessage: String? = null,
    undoKey: Long = 0L,
    /** 点待办卡片主体：切换完成态。 */
    onToggleTodo: (CapsuleItem) -> Unit = {},
    /** 点「撤销」。 */
    onUndoSwipe: (Long) -> Unit = {},
    /** 撤销窗口到期，关闭对应操作的撤销入口。 */
    onSwipeDeleteCommitted: (Long) -> Unit = {},
    /** 工具条的麦克风：在悬浮窗内直接起录。 */
    onStartVoiceCapture: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // 新建输入栏：点「+」展开，提交后收起。不走 App 内页，保持悬浮窗不丢焦点。
    var noteInputVisible by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    // 日期时间选择浮层：点卡片的「闹钟」时先弹它，确认后才建日程。
    var timePickerItem by remember { mutableStateOf<CapsuleItem?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        // 关键：卡片宽度上限必须按「屏幕宽 × 比例」算，不能直接用当前窗口宽。
        // 参考实现 overlay_home.dart:2625 的 constraints.maxWidth 是屏幕宽，
        // panelWidth = 屏幕宽 × (无展开 ? 0.72 : 0.92)。
        // 当前窗口 resize 后本身就是 0.72 屏宽，直接拿 this.maxWidth 算，
        // 结果永远不变 —— 这就是“展开后卡片不变大”的根因。
        // widthPixels 是像素，直接 .dp 会把屏幕宽放大 density 倍（2.75 倍屏 → 虚高 2.75 倍）。
        // 面板宽度被窗口约束 clamp 回真实值，所以视觉上看不出来；但卡片宽度上限跟着虚高，
        // CapsuleCard 拿它去 TextMeasurer 量正文行数，行数被算少、展开高度偏低，
        // 底部操作条就被 clipToBounds 裁掉。
        val screenWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
            androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.widthPixels.toDp()
        }
        val hasExpanded = expandedIds.isNotEmpty()
        // 面板宽度 0.72↔–0.92 用补间过渡，不再瞬时重排：之前 expandedIds 一变就
        // 直接换比例，整块面板当帧变宽（跳一下）。对齐参考实现用 AnimatedContainer
        // 补间面板宽度（duration = animationDuration）。
        val panelWidthRatio by animateFloatAsState(
            targetValue = if (hasExpanded) OverlayConstants.PANEL_WIDTH_RATIO_EXPANDED
            else OverlayConstants.PANEL_WIDTH_RATIO_NORMAL,
            animationSpec = tween(OverlayConstants.ANIMATION_DURATION_MS),
            label = "capsule_panel_width_ratio",
        )
        val panelWidth = screenWidth * panelWidthRatio
        val edgeGap = 14.dp
        // 收起卡恒用窄比例，展开卡用宽比例（两者不随面板动画联动）
        val collapsedCardMaxWidth = screenWidth * OverlayConstants.PANEL_WIDTH_RATIO_NORMAL - edgeGap * 2
        val expandedCardMaxWidth = screenWidth * OverlayConstants.PANEL_WIDTH_RATIO_EXPANDED - edgeGap * 2
        // 收起卡宽度按内容自适应（对齐参考实现的 _estimateCollapsedWidth）：
        // 短文本收缩到最小宽，长文本封顶到收起卡上限。
        fun collapsedWidthFor(item: CapsuleItem): Dp {
            val maxW = collapsedCardMaxWidth.coerceAtLeast(OverlayConstants.CARD_MIN_WIDTH)
            val textWidth = with(density) {
                textMeasurer.measure(
                    text = item.text.replace('\n', ' '),
                    style = TextStyle(
                        fontSize = (OverlayConstants.CARD_COLLAPSED_FONT_SIZE_SP + fontSizeStep).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    constraints = Constraints(),
                ).size.width.toDp()
            }
            val estimated = textWidth + OverlayConstants.CARD_H_PADDING * 2 +
                OverlayConstants.CARD_BORDER_WIDTH_DP.dp * 2 +
                (if (item.checkable) OverlayConstants.CARD_CHECK_HIT_SIZE + OverlayConstants.CARD_CHECK_GAP else 0.dp) +
                4.dp
            return estimated.coerceIn(OverlayConstants.CARD_MIN_WIDTH, maxW)
        }

        // 空白区：点击关闭。不铺满整屏，只覆盖面板所在窗口。
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .clickable(indication = null, interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()) {
                    onDismiss()
                },
        )

        AnimatedVisibility(
            visible = visible,
            // 滑入：240ms 快起慢收；滑出：240ms 慢起快收（加速推出感）。
            // 两者曲线不同，对齐声物记 panelSlideDuration 的 easeOutCubic / easeInCubic。
            enter = fadeIn(
                tween(OverlayConstants.PANEL_SLIDE_MS, easing = OverlayConstants.PANEL_ENTER_EASING)
            ) + slideInHorizontally(
                animationSpec = tween(
                    OverlayConstants.PANEL_SLIDE_MS,
                    easing = OverlayConstants.PANEL_ENTER_EASING,
                ),
                initialOffsetX = { width -> if (dockLeft) -width else width },
            ),
            exit = fadeOut(
                tween(OverlayConstants.PANEL_SLIDE_MS, easing = OverlayConstants.PANEL_EXIT_EASING)
            ) + slideOutHorizontally(
                animationSpec = tween(
                    OverlayConstants.PANEL_SLIDE_MS,
                    easing = OverlayConstants.PANEL_EXIT_EASING,
                ),
                targetOffsetX = { width -> if (dockLeft) -width else width },
            ),
            modifier = Modifier
                .align(if (dockLeft) Alignment.TopStart else Alignment.TopEnd)
                .width(panelWidth),
        ) {
            Column(
                // 左右对称留边，卡片才能真的贴停靠侧：右侧展开时右缘距屏幕 14dp，
                // 左侧 14dp 只是面板内部留白，不影响对齐。
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = edgeGap,
                        end = edgeGap,
                        // 整体下移：工具条不能贴着屏幕顶，单手操作够不到
                        top = OverlayConstants.panelTopOffsetFor(panelMaxCards),
                        bottom = 14.dp,
                    ),
                // 卡片贴停靠侧对齐：左侧展开时卡片靠左，右侧展开时靠右。
                horizontalAlignment = if (dockLeft) Alignment.Start else Alignment.End,
            ) {
                // 顶部工具条：「+」新建。与声物记一致，放在列表上方左侧（停靠侧）。
                CapsuleHeader(
                    dockLeft = dockLeft,
                    itemKeys = items.map { it.key },
                    expandedIds = expandedIds,
                    onToggleExpandAll = {
                        val keys = items.map { it.key }
                        onToggleExpandAll(keys.all { it in expandedIds })
                    },
                    onCollapse = onDismiss,
                    onOpenQuickMemo = onOpenQuickMemo,
                    onStartVoiceCapture = onStartVoiceCapture,
                    onCreateNote = { noteInputVisible = !noteInputVisible },
                )
                // 划走删除后的撤销胶囊：浮在工具条与列表之间
                if (undoMessage != null) {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        contentAlignment = if (dockLeft) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        CapsuleUndoPill(
                            message = undoMessage,
                            visible = true,
                            undoWindowMs = OverlayConstants.SWIPE_DELETE_UNDO_MS,
                            undoKey = undoKey,
                            fontSizeStep = fontSizeStep,
                            onUndo = onUndoSwipe,
                            onExpired = onSwipeDeleteCommitted,
                            modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                if (noteInputVisible) {
                    CapsuleNoteInput(
                        text = noteText,
                        fontSizeStep = fontSizeStep,
                        onTextChange = { noteText = it },
                        onSubmit = {
                            val trimmed = noteText.trim()
                            if (trimmed.isNotEmpty()) {
                                onCreateNote(trimmed)
                                noteText = ""
                                noteInputVisible = false
                            }
                        },
                    )
                }
                if (items.isEmpty()) {
                    Text(
                        text = "还没有记录",
                        style = TextStyle(
                            fontSize = (13 + fontSizeStep).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                } else {
                    // 列表区限高（对齐参考实现 panelListMaxHeightFor）：列表包在
                    // ColumnScope 的 weight(1f, fill = false) 里——高度取内容与剩余
                    // 空间的最小值。条目少时收缩包住卡片，条目多时占满窗口内部滚动。
                    // 关键效果：卡片展开/收起时列表区高度只受窗口约束、不随内容跳动，
                    // 整个面板不会“跳一下”（参考实现用 Flexible + ConstrainedBox 同理）。
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = OverlayConstants.panelListMaxHeightFor(panelMaxCards)),
                    ) {
                        itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                            val expanded = item.key in expandedIds
                            // 面板展开时逐条错峰入场：每张卡延后 index×40ms 滑入并渐显，
                            // 避免整列“撑”一下同时出现的生硬感。
                            val enterProgress = remember(item.key) {
                                androidx.compose.animation.core.Animatable(0f)
                            }
                            LaunchedEffect(item.key) {
                                kotlinx.coroutines.delay(index * OverlayConstants.CARD_STAGGER_MS.toLong())
                                enterProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        OverlayConstants.CARD_ENTER_MS,
                                        easing = OverlayConstants.PANEL_ENTER_EASING,
                                    ),
                                )
                            }
                            // 每张卡片包一层 Box 贴停靠侧对齐，否则 LazyColumn 会拉成满宽居中
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val p = enterProgress.value
                                        alpha = p
                                        // 从停靠侧方向滑入（镜像）
                                        val offsetPx = 16.dp.toPx() * (1f - p)
                                        translationX = if (dockLeft) -offsetPx else offsetPx
                                    },
                                contentAlignment = if (dockLeft) Alignment.CenterStart else Alignment.CenterEnd,
                            ) {
                                CapsuleCard(
                                text = item.text,
                                expanded = expanded,
                                fontSizeStep = fontSizeStep,
                                expandedWidth = expandedCardMaxWidth,
                                collapsedWidth = collapsedWidthFor(item),
                                onToggleExpanded = { onToggleExpanded(item.key) },
                                dockLeft = dockLeft,
                                backgroundColor = item.color,
                                archived = item.archived,
                                timeText = item.timeText,
                                checkable = item.checkable,
                                checked = item.archived,
                                onCheckChange = { onCheckChange(item, it) },
                                palette = item.palette,
                                paletteSelectedIndex = item.paletteSelectedIndex,
                                onPaletteSelect = { onPaletteSelect(item, it) },
                                onSwipeDismiss = if (onSwipeDismiss == null) null else {
                                    { onSwipeDismiss(item) }
                                },
                                todoDone = item.todo && item.todoDone,
                                // 待办条目：点卡片主体切换完成态，不展开
                                onTapOverride = if (item.todo) {
                                    { onToggleTodo(item) }
                                } else null,
                                footer = item.footer?.let { footerSpec ->
                                    {
                                        footerSpec.forEach { action ->
                                            CapsuleFooterButton(
                                                label = action.label,
                                                icon = action.icon,
                                                destructive = action.destructive,
                                                fontSizeStep = fontSizeStep,
                                                onClick = {
                                                    // 「闹钟」先弹日期时间选择，其余动作直接分发。
                                                    if (action.id == "alarm") {
                                                        timePickerItem = item
                                                    } else {
                                                        onItemAction(item, action)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(bottom = OverlayConstants.CARD_SPACING),
                            )
                            }
                        }
                    }
                }
            }
        }

        // 日期时间选择浮层盖在最上层（含遮罩），确认后回调给外部落库。
        val pickingItem = timePickerItem
        if (pickingItem != null) {
            CapsuleDateTimePicker(
                initial = resolveInitialTime(pickingItem),
                eventTitle = pickingItem.text,
                onDismiss = { timePickerItem = null },
                onConfirm = { picked, enableAlarm ->
                    timePickerItem = null
                    onTimeConfirmed(pickingItem, picked, enableAlarm)
                },
            )
        }
    }
}

@Composable
private fun CapsuleNoteInput(
    text: String,
    fontSizeStep: Int,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = (13 + fontSizeStep).sp,
                color = Color(0xFF222222),
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = "记点什么…",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = (13 + fontSizeStep).sp,
                                color = Color(0xFF999999),
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
        Text(
            text = "保存",
            style = androidx.compose.ui.text.TextStyle(
                fontSize = (13 + fontSizeStep).sp,
                color = if (text.isBlank()) Color(0xFFBBBBBB) else Color(0xFF2E9F5C),
            ),
            modifier = Modifier
                .clickable(enabled = text.isNotBlank()) { onSubmit() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CapsuleHeader(
    dockLeft: Boolean,
    itemKeys: List<String>,
    expandedIds: Set<String>,
    onToggleExpandAll: () -> Unit,
    onOpenQuickMemo: () -> Unit,
    onStartVoiceCapture: () -> Unit,
    onCollapse: () -> Unit,
    onCreateNote: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = if (dockLeft) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 深色半透明全圆角工具条：面板背景透明、垫在任意应用/壁纸上，
        // 浅色图标裸放会撞白底消失，自带深底才有跨背景对比度（对齐声物记）。
        // 按钮从停靠缘向外：新增 / 全部展开收起（空列表不渲染）/ 收起。
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onCreateNote() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建闪记",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            // 全部展开/收起：空列表时无意义，不渲染（对齐参考实现）
            if (itemKeys.isNotEmpty()) {
                val allExpanded = expandedIds.containsAll(itemKeys)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onToggleExpandAll() },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                        contentDescription = if (allExpanded) "全部收起" else "全部展开",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onOpenQuickMemo() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "打开闪记",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onStartVoiceCapture() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "语音闪记",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onCollapse() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    // 收起箭头指向停靠边缘（右缘停靠朝右、左缘停靠朝左），与参考实现镜像规则一致
                    imageVector = if (dockLeft) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                    contentDescription = "收起",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CapsuleFooterButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    destructive: Boolean,
    fontSizeStep: Int,
    onClick: () -> Unit,
) {
    // 参考实现 _buildActionButton 是纯图标（40dp 命中区 + 20dp 图标），但用户
    // 明确要求保留文字标签：图标认得出、文字不费解。故保留「图标 + 文字」组合，
    // 排布仍按用户认可的紧凑居中（见 CapsuleCard 的 spacedBy(8) + CenterHorizontally）。
    val tint = if (destructive) Color(0xFFFF8A80) else Color.White
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            androidx.compose.material3.Icon(
                imageVector = it,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text = label, style = TextStyle(fontSize = (12 + fontSizeStep).sp, color = tint))
    }
}

/** 面板条目：闪记与日程统一成这一种形态。 */
data class CapsuleItem(
    val key: String,
    val text: String,
    val timeText: String? = null,
    val color: Color = OverlayConstants.DEFAULT_CARD_COLOR,
    val archived: Boolean = false,
    val footer: List<CapsuleAction>? = null,
    /** 是否显示左侧归档勾选框（日程条目显示，闪记条目不显示）。 */
    val checkable: Boolean = false,
    /** 展开态可选的换色调色板；null = 不显示调色板（闪记条目）。 */
    val palette: List<Color>? = null,
    /** 当前选中的调色板下标；-1 = 未选中任何预设色。 */
    val paletteSelectedIndex: Int = -1,
    /** 是否为待办条目（可点击划线完成）；仅闪记条目。 */
    val todo: Boolean = false,
    /** 待办是否已完成（文字加删除线）。 */
    val todoDone: Boolean = false,
)

/** 卡片底部操作。icon 为空时只显示文字。 */
data class CapsuleAction(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val destructive: Boolean = false,
)
