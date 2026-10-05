package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.note.data.attachment.NoteAttachmentStore
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.FlashNoteListEntry
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.MemoStreamSegment
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppSwipeReveal
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 类型筛选：全部 / 随口记 / 随手记。
 *
 * 做成小胶囊而不是撑满整行的分段控件——面板里一行就能放下，不占垂直空间；
 * 选中项用主题色实底，点选立即生效，并带条目数避免切过去才发现是空的。
 */
@Composable
internal fun MemoStreamFilterBar(
    current: MemoStreamSegment,
    memoCount: Int,
    flashCount: Int,
    onSelect: (MemoStreamSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()
    val options = listOf(
        Triple(MemoStreamSegment.ALL, "全部", memoCount + flashCount),
        Triple(MemoStreamSegment.QUICK_MEMO, "随口记", memoCount),
        Triple(MemoStreamSegment.FLASH_NOTE, "随手记", flashCount)
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (segment, label, count) ->
            val selected = segment == current
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                    .clickable {
                        haptics.selection()
                        onSelect(segment)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (count > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 闪记列表行：摘要优先，其次预览文本，底部标注来源与时间。
 *
 * 视觉沿用速记列表的克制风格（无卡片背景、靠间距分隔），避免两个分段割裂；
 * 交互同样用左滑露出操作按钮，与速记保持一致的手感。
 */
@Composable
internal fun FlashNoteListRow(
    entry: FlashNoteListEntry,
    hapticEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDeleteRequest: () -> Unit,
    uiSize: Int = 2,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    var isRevealed by remember(entry.key) { mutableStateOf(false) }
    val actionButtonSize = when (uiSize) {
        1 -> 48.dp
        2 -> 52.dp
        else -> 56.dp
    }

    AppSwipeReveal(
        isRevealed = isRevealed,
        actionWidth = 96.dp,
        onRevealedChange = { isRevealed = it },
        modifier = modifier,
        identity = entry.key,
        revealThreshold = 0.28f,
        hapticThreshold = 0.5f,
        dragOverflowPx = 32f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
        hapticEnabled = hapticEnabled,
        actions = { close ->
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                FlashNoteSwipeActionIcon(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    backgroundColor = MaterialTheme.colorScheme.error,
                    size = actionButtonSize,
                    onClick = {
                        haptics.longPress()
                        onDeleteRequest()
                        close()
                    }
                )
            }
        }
    ) { swipeModifier, revealProgress, close ->
        FlashNoteRowContent(
            entry = entry,
            hapticEnabled = hapticEnabled,
            onClick = {
                if (revealProgress > 0f) {
                    close()
                } else {
                    haptics.click()
                    onClick()
                }
            },
            onLongPress = {
                haptics.confirm()
                onLongPress()
            },
            modifier = swipeModifier
        )
    }
}

@Composable
private fun FlashNoteRowContent(
    entry: FlashNoteListEntry,
    hapticEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(vertical = 10.dp)
    ) {
        if (entry.imageRelativePath.isNotBlank()) {
            FlashNoteThumbnail(
                relativePath = entry.imageRelativePath,
                hapticEnabled = hapticEnabled,
                onClick = onClick,
                onLongPress = onLongPress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(bottom = 8.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (entry.pinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已置顶",
                    tint = secondaryColor,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(14.dp)
                )
            }
        }

        val summaryLine = when {
            entry.summaryPending -> "摘要生成中…"
            entry.summaryFailed -> "未生成摘要"
            !entry.summaryText.isNullOrBlank() -> entry.summaryText
            !entry.previewText.isNullOrBlank() -> entry.previewText
            else -> null
        }

        if (summaryLine != null) {
            Text(
                text = summaryLine,
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.summaryPending || entry.summaryFailed) {
                    secondaryColor.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (!entry.siteName.isNullOrBlank()) {
            Text(
                text = entry.siteName,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * 图片闪记的缩略图。
 *
 * 缩略图解码放在后台线程：列表滚动时若在主线程读文件会掉帧。
 * 加载失败不显示占位块，避免列表出现空白洞。
 */
@Composable
private fun FlashNoteThumbnail(
    relativePath: String,
    hapticEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(relativePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(relativePath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val file = NoteAttachmentStore.fileForRelativePath(context, relativePath)
                if (!file.exists()) return@runCatching null
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }

    val image = bitmap ?: return
    val haptics = rememberAppHaptics(hapticEnabled)

    Image(
        bitmap = image,
        contentDescription = "闪记图片",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    haptics.click()
                    onClick()
                },
                onLongClick = {
                    haptics.confirm()
                    onLongPress()
                }
            )
    )
}

@Composable
internal fun FlashNoteEmptyState(
    searching: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (searching) "未找到相关闪记" else "还没有闪记",
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (searching) {
                "换个关键词试试。"
            } else {
                "在浏览器或微信里点「分享 → 闪记」即可收藏网页与文章。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
    }
}

/** 闪记左滑操作按钮。视觉与速记的 [QuickMemoPage] 内同名组件一致。 */
@Composable
private fun FlashNoteSwipeActionIcon(
    icon: ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    size: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = backgroundColor,
            modifier = Modifier.size(22.dp)
        )
    }
}
