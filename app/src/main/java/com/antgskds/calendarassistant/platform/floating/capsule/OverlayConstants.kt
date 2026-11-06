package com.antgskds.calendarassistant.platform.floating.capsule

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 闪念胶囊式悬浮窗的尺寸与配色。
 *
 * 参数取自声物记（Apache-2.0）的 overlay_constants.dart，保持数值一致以便复刻观感。
 * 窗口、胶囊、面板三层尺寸分离：窗口是原生硬编码的不变量，胶囊本体在窗口内按档位内缩。
 */
object OverlayConstants {
    /** 原生窗口宽高（dp）。窗口即触控面积，不随视觉档位变化。 */
    const val HANDLE_WIDTH_DP = 28
    const val HANDLE_HEIGHT_DP = 88

    /** 胶囊本体基准尺寸，按档位百分比内缩后使用。 */
    const val CAPSULE_BASE_WIDTH_DP = 24f
    const val CAPSULE_BASE_HEIGHT_DP = 80f

    /** 把手尺寸档位：100 / 75 / 50 百分比。 */
    val HANDLE_SIZE_PERCENTS = listOf(100, 75, 50)
    const val HANDLE_SIZE_DEFAULT_PERCENT = 100

    fun isMiniHandleSize(percent: Int): Boolean = percent <= HANDLE_SIZE_PERCENTS.last()

    /** 仅标准档显示竖排文字；小档放不下。 */
    fun handleShowsLabel(percent: Int, theme: HandleTheme): Boolean =
        percent >= HANDLE_SIZE_PERCENTS.first() && theme != HandleTheme.PILL3D

    fun handleInsetWidthOf(percent: Int): Float =
        CAPSULE_BASE_WIDTH_DP * percent / 100f

    fun handleInsetHeightOf(percent: Int): Float =
        CAPSULE_BASE_HEIGHT_DP * percent / 100f

    /** 双色药丸配色：上暖白 / 下绿。 */
    val HANDLE_CAPSULE_TOP_COLOR = Color(0xFFF5F6F3)
    val HANDLE_CAPSULE_BOTTOM_COLOR = Color(0xFF2E9F5C)
    /** 两半接合处的凹陷缝。 */
    val HANDLE_SEAM_COLOR = Color(0x24000000)
    /** 静置态整体透明度；拖动时提到 1.0。 */
    const val HANDLE_RESTING_OPACITY = 0.93f
    const val HANDLE_FONT_SIZE_SP = 10f
    const val HANDLE_ICON_SIZE_DP = 13f
    const val HANDLE_ICON_SIZE_MINI_DP = 9f
    const val HANDLE_LABEL = "闪记"

    /** 展开面板宽度占屏幕比例；有卡片展开时加宽。 */
    const val EXPANDED_WIDTH_RATIO = 0.72f
    const val EXPANDED_PANEL_WIDTH_RATIO = 0.92f

    /** 卡片尺寸。 */
    val CARD_HEIGHT = 46.dp
    val CARD_MIN_WIDTH = 60.dp
    val CARD_SPACING = 10.dp
    val CARD_H_PADDING = 10.dp
    /** 展开态竖直内边距（收起态为 0，靠 defaultMinSize 撑高）。 */
    val CARD_V_PADDING = 10.dp
    /** 收起态复选框与文字的间距。 */
    val CARD_CHECK_GAP = 4.dp
    /** 归档勾选框命中区（大于视觉圆，点偏也中）。 */
    val CARD_CHECK_HIT_SIZE = 24.dp
    /** 归档勾选框视觉圆直径。 */
    val CARD_CHECK_VISUAL_SIZE = 20.dp
    /** 勾选框内对勾图标尺寸。 */
    val CARD_CHECK_ICON_SIZE = 14.dp
    /** 展开态时间行字号。 */
    const val CARD_TIME_FONT_SIZE_SP = 12f
    /** 展开态时间行与正文的间距。 */
    val CARD_TIME_GAP = 4.dp
    /** 展开态正文与操作条分隔线的间距。 */
    val CARD_FOOTER_TOP_GAP = 8.dp
    /** 展开态分隔线与操作条的间距。 */
    val CARD_FOOTER_DIVIDER_GAP = 4.dp
    const val CARD_FONT_SIZE_SP = 13f
    const val CARD_COLLAPSED_FONT_SIZE_SP = 12f
    const val CARD_EXPANDED_RADIUS_DP = 16f
    const val CARD_BORDER_WIDTH_DP = 1f
    val CARD_PLAY_BUTTON_SIZE = 30.dp
    val CARD_PLAY_ICON_SIZE = 20.dp
    val CARD_PLAY_BUTTON_HIT_SIZE = 40.dp

    /** 卡片默认底色（蓝）。 */
    val DEFAULT_CARD_COLOR = Color(0xFF6F9AF0)

    /** 正文排版。 */
    const val BODY_FONT_SIZE_SP = 14f
    const val BODY_LINE_HEIGHT = 1.5f

    const val PANEL_MAX_CARDS_MIN = 6
    const val PANEL_MAX_CARDS_DEFAULT = 10
    const val MAX_VISIBLE_CARDS = PANEL_MAX_CARDS_DEFAULT

    fun panelListMaxHeightFor(cards: Int) =
        (CARD_HEIGHT + CARD_SPACING) * cards.coerceIn(PANEL_MAX_CARDS_MIN, MAX_VISIBLE_CARDS) + 8.dp

    fun panelTopOffsetFor(cards: Int) =
        PANEL_TOP_OFFSET + (CARD_HEIGHT + CARD_SPACING) *
            (PANEL_MAX_CARDS_DEFAULT - cards.coerceIn(PANEL_MAX_CARDS_MIN, MAX_VISIBLE_CARDS))

    val PANEL_LIST_MAX_HEIGHT = panelListMaxHeightFor(MAX_VISIBLE_CARDS)

    /** 把手拖动阈值（dp），低于此值视为点按。 */
    const val EDGE_SWIPE_THRESHOLD_DP = 4f

    /** 面板与卡片的补间时长（ms），与声物记 animationDuration 一致。 */
    const val ANIMATION_DURATION_MS = 200

    /** 面板宽占屏宽比例：无卡展开时（对齐声物记 expandedWidthRatio）。 */
    const val PANEL_WIDTH_RATIO_NORMAL = 0.72f

    /** 面板宽占屏宽比例：有卡展开时（对齐声物记 expandedPanelWidthRatio）。 */
    const val PANEL_WIDTH_RATIO_EXPANDED = 0.92f

    /**
     * 面板顶部下压距离（dp）。
     *
     * 声物记的窗口是全屏（FLAG_LAYOUT_NO_LIMITS），40dp 只是避让状态栏；
     * 本项目的窗口是 FLAG_LAYOUT_IN_SCREEN，不延伸状态栏，所以这里的值
     * 是纯额外下移——让工具条落到单手能碰到的位置。
     */
    val PANEL_TOP_OFFSET = 88.dp

    /** 面板滑入/滑出时长（ms），比卡片稍长，对齐声物记 panelSlideDuration。 */
    const val PANEL_SLIDE_MS = 240

    /** 面板滑入缓动：快起慢收，避免生硬。 */
    val PANEL_ENTER_EASING = androidx.compose.animation.core.FastOutSlowInEasing

    /** 面板滑出缓动：慢起快收，收起时有加速推出感。 */
    val PANEL_EXIT_EASING = androidx.compose.animation.core.FastOutLinearInEasing

    /** 卡片逐条入场的错峰间隔（ms）。 */
    const val CARD_STAGGER_MS = 40

    /** 单张卡片入场动画时长（ms）。 */
    const val CARD_ENTER_MS = 180

    /** 收起态把手自动隐藏的默认秒数（对齐声物记 autoHideDefaultSeconds）。 */
    const val HANDLE_AUTO_HIDE_DEFAULT_SECONDS = 10

    /** 自动隐藏的「永久」哨兵值（-1 避免与 0=立即 混淆）。 */
    const val HANDLE_AUTO_HIDE_NEVER_SECONDS = -1

    /** 自动隐藏到期后：是否默认缩成贴边竖线（声物记 edgeLineEnabled 默认 true）。 */
    const val EDGE_LINE_DEFAULT_ENABLED = true

    /** 竖线窗口宽（dp）：透明触摸缓冲，视觉线只有 4dp。 */
    val EDGE_LINE_WINDOW_WIDTH_DP = 20.dp

    /** 竖线窗口高（dp）。 */
    val EDGE_LINE_HEIGHT_DP = 64.dp

    /** 竖线视觉宽（dp）：≈1mm，只管画。 */
    val EDGE_LINE_VISUAL_WIDTH_DP = 4.dp

    /**
     * 收起后把手回位的总时长（ms）。
     *
     * 缩窗时窗口 frame 从「面板尺寸」跳到「28×88 停靠缘」——Compose 感知不到
     * 原生 resize 完成的时刻，所以用时间轴分段模拟声物记的做法：
     * 前 60%（300ms）把手完全透明，等窗口 frame 移完；后 40%（180ms）从停靠缘
     * 向内滑入同时渐显。
     */
    const val HANDLE_REVEAL_TOTAL_MS = 480

    /** 渐显开始前的静默时长（等窗口 frame 移动完成）。 */
    const val HANDLE_REVEAL_DELAY_MS = 300

    /** 渐显 + 滑入的时长。 */
    const val HANDLE_REVEAL_FADE_MS = 180

    /** 录音胶囊每秒增长，达到上限后不再变宽。 */
    val RECORD_BAR_BASE_WIDTH = 124.dp
    val RECORD_BAR_GROWTH_PER_SECOND = 40.dp
    val RECORD_BAR_MAX_WIDTH = 300.dp
    val RECORD_BAR_EDGE_MARGIN = 12.dp
    val RECORD_BAR_SAVING_WIDTH = 140.dp

    /** 录音胶囊高度。 */
    val RECORD_BAR_HEIGHT = 44.dp

    /** 停止按钮贴屏端的完整命中区。 */
    val RECORD_STOP_SIZE = 44.dp
    val RECORD_STOP_VISUAL_SIZE = 28.dp

    /** 停止图标尺寸。 */
    val RECORD_STOP_ICON_SIZE = 16.dp

    /** 录音计时前的红点。 */
    val RECORD_DOT_COLOR = Color(0xFFFF5252)

    /** 波形柱子数量。 */
    const val RECORD_WAVE_BARS = 16

    /** 卡片划走归档的阈值（相对卡片宽度的比例）。 */
    const val CARD_SWIPE_THRESHOLD_RATIO = 0.35f

    /** 划走删除后的撤销窗口（ms）；到期落定真删。 */
    const val SWIPE_DELETE_UNDO_MS = 3000L
}

/** 把手主题。 */
enum class HandleTheme {
    /** 双色药丸（默认）：上暖白 / 下绿。 */
    DUO,

    /** 蓝紫：上卡片默认蓝 / 下灵感紫。 */
    BLUE_PURPLE,

    /** 拟物胶囊：纯造型，无图标无文字。 */
    PILL3D;

    val capsuleTopColor: Color
        get() = when (this) {
            DUO -> OverlayConstants.HANDLE_CAPSULE_TOP_COLOR
            BLUE_PURPLE -> OverlayConstants.DEFAULT_CARD_COLOR
            PILL3D -> Color(0xFFF7F8F6)
        }

    val capsuleBottomColor: Color
        get() = when (this) {
            DUO -> OverlayConstants.HANDLE_CAPSULE_BOTTOM_COLOR
            BLUE_PURPLE -> Color(0xFFAE82E4)
            PILL3D -> Color(0xFFE0524E)
        }

    /** 闪电图标色：双色药丸取下半绿（落在上半白区呼应成对），其余主题白。 */
    val iconColor: Color
        get() = when (this) {
            DUO -> capsuleBottomColor
            else -> Color.White
        }

    /** 拟物胶囊纯造型无图标。 */
    val showsIcon: Boolean
        get() = this != PILL3D

    /** 拟物胶囊额外叠高光条 + 下半暗部渐变。 */
    val isPill3d: Boolean
        get() = this == PILL3D
}
