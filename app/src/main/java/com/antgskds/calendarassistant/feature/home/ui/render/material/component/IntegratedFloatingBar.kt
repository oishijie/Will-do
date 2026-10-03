package com.antgskds.calendarassistant.feature.home.ui.render.material.component

import com.antgskds.calendarassistant.shared.ui.material.component.AppFloatingActionSurface

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey
import com.antgskds.calendarassistant.feature.home.domain.homeEntryLabel
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.app.ui.theme.material.background.rememberAppBackgroundStylePalette

// 统一高度设定为 80dp（图标 + 底部小字需要更充裕的纵向空间）
val IntegratedFloatingBarHeight = 80.dp
val IntegratedFloatingBarExtraHeight = 4.dp
val IntegratedFloatingBarShadowPadding = 20.dp
val IntegratedFloatingBarToastGap = 12.dp
val IntegratedFloatingBarVisualHeight =
    IntegratedFloatingBarHeight + IntegratedFloatingBarExtraHeight + IntegratedFloatingBarShadowPadding
// 注意：这个值通常用于外部布局
val IntegratedFloatingBarBottomSpacing = 0.dp

// --- Hydrogen 核心配色 ---
val HydrogenBg = Color(0xFFF1F1EA)
val HydrogenIndicator = Color(0xFFE2E2D5)
val HydrogenContent = Color(0xFF44473E)
val HydrogenFab = Color(0xFF4B5541)
val HydrogenFabIcon = Color(0xFFF1F1EA)

/** FAB 与展开操作区相对底栏顶部的间距。 */
val IntegratedFloatingBarFabGap = 12.dp

/**
 * 常驻悬浮底栏（Hydrogen 风格）。
 *
 * 结构：底部大胶囊 = 四个「图标 + 小字」标签均匀分布；「＋」为独立圆钮悬浮在底栏右上方，
 * 点击后向左横向撑开 [搜索/图片/新建] 操作胶囊，「＋」旋转为「✕」。
 *
 * 选中态：整 tab 填充 [secondaryContainer] 圆角胶囊，图标与文字使用 [onSecondaryContainer]。
 * 「＋」圆钮使用 [primary] 实底，与选中态呼应。
 */
@Composable
fun IntegratedFloatingBar(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    navItems: List<String>,
    selectedPageKey: String,
    onPageClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onImageClick: () -> Unit,
    onEditClick: () -> Unit,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val backgroundPalette = rememberAppBackgroundStylePalette(
        enabled = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        cardAlphaPercent = MySettings.normalizeAppBackgroundCardAlphaPercent(cardAlphaPercent)
    )
    val barSurfaceAlpha = MySettings.normalizeAppBackgroundCardAlphaPercent(cardAlphaPercent) / 100f

    val navBg = if (backgroundMode) {
        backgroundPalette.surface.copy(alpha = barSurfaceAlpha)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val navContent = if (backgroundMode) {
        backgroundPalette.content
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val unselectedContent = if (backgroundMode) {
        backgroundPalette.content.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }
    val selectedContainer = if (backgroundMode) {
        backgroundPalette.accent
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val selectedContent = if (backgroundMode) {
        backgroundPalette.accentContent
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val fabBg = if (backgroundMode) backgroundPalette.accent else MaterialTheme.colorScheme.primary
    val fabIcon = if (backgroundMode) backgroundPalette.accentContent else MaterialTheme.colorScheme.onPrimary

    val navShape = RoundedCornerShape(percent = 50)
    val fabShape = RoundedCornerShape(24.dp)
    val menuShape = RoundedCornerShape(26.dp)
    val tabShape = RoundedCornerShape(percent = 50)
    val navElevation = if (backgroundMode) 0.dp else 6.dp
    val navHeight = IntegratedFloatingBarHeight + IntegratedFloatingBarExtraHeight
    val fabSize = 56.dp
    // 横向：让 FAB 中心对齐「设置」tab 的中心线（底栏四等分后最右一格的中心）。
    // 48dp 是按 360~412dp 屏宽算出的折中值，视觉上稳定落在设置按钮正上方。
    val fabRowEnd = 48.dp
    // 纵向：与底栏保持一段明显留白，FAB 呈悬浮态而非贴边。
    val fabRowBottom = IntegratedFloatingBarShadowPadding + navHeight + 28.dp

    val todayIcon = painterResource(R.drawable.floatingbar_today)
    val allIcon = painterResource(R.drawable.floatingbar_all)
    val quickMemoIcon = painterResource(R.drawable.ic_stat_quickmemo)

    val normalizedNavItems = navItems.distinct().filter {
        it == HomeEntryKey.TODAY || it == HomeEntryKey.ALL ||
            it == HomeEntryKey.NOTE || it == HomeEntryKey.SETTINGS
    }

    val effectiveSelectedKey = effectiveSelectedKey(selectedPageKey, normalizedNavItems)

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fabRotation"
    )

    // FAB 形态：圆角方形（参考稿）。收起时圆角约为边长 30%，展开时略微收方，
    // 配合 45° 旋转换成 ✕，整体始终保持方形轮廓而非正圆。
    val fabCorner by animateDpAsState(
        targetValue = if (isExpanded) 12.dp else 17.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fabCorner"
    )
    val fabMorphShape = RoundedCornerShape(fabCorner)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 展开时的全屏透明遮罩：点击空白处收起
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onExpandedChange(false) }
            )
        }

        // 主胶囊：四个「图标 + 小字」tab 均匀分布，撑满整行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = IntegratedFloatingBarShadowPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppFloatingActionSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(navHeight),
                shape = navShape,
                containerColor = navBg,
                elevation = navElevation
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    normalizedNavItems.forEach { key ->
                        HydrogenNavTab(
                            icon = painterIconForPageKey(key, todayIcon, allIcon, quickMemoIcon),
                            vectorIcon = vectorIconForPageKey(key),
                            label = homeEntryLabel(key),
                            isSelected = effectiveSelectedKey == key,
                            selectedContainer = selectedContainer,
                            selectedContent = selectedContent,
                            unselectedContent = unselectedContent,
                            onClick = {
                                haptics.click()
                                onPageClick(key)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 右下 FAB 列：纵向菜单从 FAB 上方向上弹出（设置页不显示）
        if (effectiveSelectedKey != HomeEntryKey.SETTINGS) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = fabRowEnd)
                .padding(bottom = fabRowBottom),
            horizontalAlignment = Alignment.End
        ) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                    expandFrom = Alignment.Bottom
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                    shrinkTowards = Alignment.Bottom
                ) + fadeOut()
            ) {
                AppFloatingActionSurface(
                    shape = menuShape,
                    containerColor = navBg,
                    elevation = navElevation
                ) {
                    Column(
                        modifier = Modifier
                            .width(196.dp)
                            .padding(vertical = 6.dp)
                    ) {
                        HydrogenFabMenuItem(
                            icon = Icons.Default.Search,
                            label = "搜索日程",
                            iconContainer = selectedContainer,
                            iconTint = selectedContent,
                            labelColor = navContent,
                            onClick = onSearchClick
                        )
                        FabMenuDivider()
                        HydrogenFabMenuItem(
                            icon = Icons.Default.Image,
                            label = "图片识别",
                            iconContainer = selectedContainer,
                            iconTint = selectedContent,
                            labelColor = navContent,
                            onClick = onImageClick
                        )
                        FabMenuDivider()
                        HydrogenFabMenuItem(
                            icon = Icons.Default.Edit,
                            label = "新建日程",
                            iconContainer = selectedContainer,
                            iconTint = selectedContent,
                            labelColor = navContent,
                            onClick = onEditClick
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            HydrogenFabPlus(
                rotation = rotation,
                shape = fabMorphShape,
                containerColor = fabBg,
                iconColor = fabIcon,
                contentDescription = if (isExpanded) "收起" else "展开操作",
                elevation = navElevation,
                onClick = {
                    haptics.click()
                    onExpandedChange(!isExpanded)
                },
                modifier = Modifier.size(fabSize)
            )
        }
        }
    }
}

/** 选中态计算：不在列表内时回落到今日日程。 */
private fun effectiveSelectedKey(selectedPageKey: String, navItems: List<String>): String =
    if (selectedPageKey in navItems) selectedPageKey else HomeEntryKey.TODAY

private fun painterIconForPageKey(
    key: String,
    todayIcon: androidx.compose.ui.graphics.painter.Painter,
    allIcon: androidx.compose.ui.graphics.painter.Painter,
    quickMemoIcon: androidx.compose.ui.graphics.painter.Painter,
): androidx.compose.ui.graphics.painter.Painter? = when (key) {
    HomeEntryKey.TODAY -> todayIcon
    HomeEntryKey.ALL -> allIcon
    HomeEntryKey.NOTE -> quickMemoIcon
    else -> null
}

private fun vectorIconForPageKey(key: String): ImageVector? = when (key) {
    HomeEntryKey.SETTINGS -> Icons.Outlined.Settings
    else -> null
}

/**
 * 底栏标签：图标 + 底部小字，选中时填充整 tab 高度的圆角胶囊。
 * 内容垂直居中于固定高度内，任何字体缩放下都不会被裁切。
 */
@Composable
private fun HydrogenNavTab(
    icon: androidx.compose.ui.graphics.painter.Painter?,
    vectorIcon: ImageVector?,
    label: String,
    isSelected: Boolean,
    selectedContainer: Color,
    selectedContent: Color,
    unselectedContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()
    val contentColor = if (isSelected) selectedContent else unselectedContent
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .clickable { haptics.click(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .fillMaxWidth()
                    .padding(horizontal = 3.dp)
                    .background(selectedContainer, RoundedCornerShape(percent = 50))
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TabIcon(icon, vectorIcon, contentColor)
            Spacer(modifier = Modifier.size(3.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun TabIcon(
    icon: androidx.compose.ui.graphics.painter.Painter?,
    vectorIcon: ImageVector?,
    tint: Color
) {
    if (icon != null) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    } else if (vectorIcon != null) {
        Icon(
            imageVector = vectorIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** FAB 展开菜单条目之间的细分隔线，参考稿的分组处理。 */
@Composable
private fun FabMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp, end = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 1.dp,
    )
}

/** 展开态操作胶囊内的图标按钮。 */
@Composable
private fun HydrogenFabAction(
    vectorIcon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable { haptics.click(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = vectorIcon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 独立悬浮的「＋」圆钮，展开时旋转为「✕」并渐变为圆角方块。与主胶囊同材质。 */
@Composable
private fun HydrogenFabPlus(
    rotation: Float,
    shape: Shape,
    containerColor: Color,
    iconColor: Color,
    contentDescription: String,
    elevation: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()
    AppFloatingActionSurface(
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        elevation = elevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { haptics.click(); onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier
                    .size(26.dp)
                    .rotate(rotation)
            )
        }
    }
}

/** FAB 展开菜单的纵向条目：淡色圆底图标 + 粗体标签（参考稿样式）。 */
@Composable
private fun HydrogenFabMenuItem(
    icon: ImageVector,
    label: String,
    iconContainer: Color,
    iconTint: Color,
    labelColor: Color,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { haptics.click(); onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(iconContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor
        )
    }
}
