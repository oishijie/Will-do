package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundGlassSurface
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundCardAlphaPercent
import com.antgskds.calendarassistant.app.ui.theme.material.background.rememberAppBackgroundStylePalette
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarExtraHeight
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarShadowPadding
import com.antgskds.calendarassistant.feature.note.data.attachment.NoteAttachmentStore
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraph
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphType
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.FlashNoteListEntry
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppPageScaffold
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBar
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBarBackButton
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 正文停止输入多久后落库。避免逐字符重建 document 段落。 */
private const val BODY_SAVE_DEBOUNCE_MS = 600L

/**
 * 随手记详情内容。
 *
 * 列表点击默认跳原文，但收藏的正文留在本地——详情页是查看正文的唯一入口，
 * 否则用户收藏后除了摘要几十字就再也读不到原文。
 *
 * 正文与随口记对齐为**内联可编辑**：点开即输入框，不设显式「编辑」按钮；
 * 草稿仅存本地，停手 600ms 后再写库。
 */
@Composable
internal fun FlashNoteDetailContent(
    entry: FlashNoteListEntry,
    hapticEnabled: Boolean,
    onOpenSource: () -> Unit,
    onSaveTitle: (String) -> Unit,
    onSaveBody: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scrollState = rememberScrollState()

    // 草稿以 entry.key（`note:<id>`）为锚，条目不变就不重置——
    // 否则保存后 notes 刷新会把光标位置和正在输入的内容一起冲掉。
    var draftTitle by remember(entry.key) { mutableStateOf(entry.title) }
    var draftBody by remember(entry.key) { mutableStateOf(entry.previewText.orEmpty()) }

    // 标题防抖落库。注意 entry.title 取的是 displayTitle，空标题会回显「无标题」；
    // 只要用户不动它就不写库（下面的相等判断挡住了）。
    LaunchedEffect(draftTitle) {
        if (draftTitle == entry.title) return@LaunchedEffect
        delay(BODY_SAVE_DEBOUNCE_MS)
        onSaveTitle(draftTitle)
    }

    // 防抖落库：只在草稿与已保存正文不一致时才写，停手 600ms 生效。
    LaunchedEffect(draftBody) {
        if (draftBody == entry.previewText.orEmpty()) return@LaunchedEffect
        delay(BODY_SAVE_DEBOUNCE_MS)
        onSaveBody(draftBody)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 112.dp + LocalAppPageBottomPadding.current)
    ) {
        if (entry.imageRelativePath.isNotBlank()) {
            FlashNoteDetailImage(
                relativePath = entry.imageRelativePath,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }

        // 标题与正文同样内联可编辑：点开即输入框，停手 600ms 落库。
        BasicTextField(
            value = draftTitle,
            onValueChange = { draftTitle = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (draftTitle.isBlank()) {
                        Text(
                            text = "无标题",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryColor.copy(alpha = 0.55f)
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (!entry.siteName.isNullOrBlank()) {
            Text(
                text = entry.siteName,
                style = MaterialTheme.typography.labelMedium,
                color = secondaryColor,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 摘要块：区分「生成中/失败」与真实摘要，视觉上与正文拉开层次。
        val summaryBlock = when {
            entry.summaryPending -> "摘要生成中…"
            entry.summaryFailed -> "未生成摘要"
            entry.summaryText.isNullOrBlank() -> null
            else -> entry.summaryText
        }
        if (summaryBlock != null) {
            val isPlaceholder = entry.summaryPending || entry.summaryFailed
            Text(
                text = summaryBlock,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) {
                    secondaryColor.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier
                    .padding(top = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(14.dp)
            )
        }

        // 正文：内联可编辑。空态占位沿用原「图片闪记不生成摘要」的说明，
        // 避免那句提示在改成输入框后丢掉。
        val bodyPlaceholder = if (entry.imageRelativePath.isNotBlank() && summaryBlock == null) {
            "图片闪记不生成摘要，可在此补充说明…"
        } else {
            "点击输入正文…"
        }
        BasicTextField(
            value = draftBody,
            onValueChange = { draftBody = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .defaultMinSize(minHeight = 72.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (draftBody.isBlank()) {
                        Text(
                            text = bodyPlaceholder,
                            color = secondaryColor.copy(alpha = 0.55f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (entry.url.isNotBlank()) {
            Row(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.confirm()
                        onOpenSource()
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (entry.requiresClipboard) Icons.Rounded.ContentCopy else Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (entry.requiresClipboard) "复制链接去微信打开" else "打开原文",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * 闪记详情页容器：顶栏（标题 + 返回）+ 内容 + 底部悬浮栏。
 *
 * 底部栏沿用随口记详情页那套（[QuickMemoActionButton] + 同一组悬浮栏高度常量），
 * 保证两个详情页的悬浮栏完全同形。
 */
@Composable
internal fun FlashNoteDetailScreen(
    entry: FlashNoteListEntry,
    /** 该条是否正挂在实况通知上。由胶囊真实状态驱动，不在此处维护本地副本。 */
    isPinned: Boolean,
    onBack: () -> Unit,
    hapticEnabled: Boolean,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    onOpenSource: () -> Unit,
    onSaveTitle: (String) -> Unit = {},
    onSaveBody: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onTogglePin: (Boolean) -> Unit = {}
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val context = LocalContext.current

    var showDeleteConfirm by remember { mutableStateOf(false) }

    AppBackgroundStyleTheme(enabled = backgroundMode, miuiBlurEnabled = miuiBlurEnabled) {
        val pageContainerColor =
            if (backgroundMode) Color.Transparent else MaterialTheme.colorScheme.background

        AppPageScaffold(
            containerColor = pageContainerColor,
            contentMaxWidth = 760.dp,
            edgeToEdgeContent = true,
            topBar = {
                AppTopBar(
                    title = "随手记",
                    containerColor = pageContainerColor,
                    navigationIcon = {
                        AppTopBarBackButton(onClick = {
                            haptics.click()
                            onBack()
                        })
                    }
                )
            }
        ) {
            // ⚠️ 这层 Box 是必需的，不能省。
            // AppPageScaffold 的内层 Box 带 propagateMinConstraints = true（为让短页面纵向居中），
            // 会把 minHeight = 整页高 传下来；此时 .height(84.dp) 会在约束求交时被 coerceIn
            // 抬回整页高，悬浮栏会被撑成占满屏幕的巨型胶囊。
            // 套一层普通 Box（propagateMinConstraints 默认 false）即可让尺寸约束重新生效。
            // 随口记详情页 QuickMemoPage.kt:1108 也是同样的结构。
            Box(modifier = Modifier.fillMaxSize()) {
                FlashNoteDetailContent(
                    entry = entry,
                    hapticEnabled = hapticEnabled,
                    onOpenSource = onOpenSource,
                    onSaveTitle = onSaveTitle,
                    onSaveBody = onSaveBody,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                )

                FlashNoteDetailBottomBar(
                    isPinned = isPinned,
                    onPinClick = {
                        haptics.confirm()
                        onTogglePin(!isPinned)
                    },
                    onDeleteClick = {
                        haptics.click()
                        showDeleteConfirm = true
                    },
                    onCopyClick = {
                        val text = entry.previewText.orEmpty()
                        if (text.isNotBlank()) {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("随手记正文", text))
                            haptics.confirm()
                        }
                    },
                    backgroundMode = backgroundMode,
                    miuiBlurEnabled = miuiBlurEnabled,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这条随手记？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    haptics.confirm()
                    onDelete()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 随手记详情页底部悬浮栏。
 *
 * 与 [MaterialQuickMemoDetailBottomBar] 同形：同一组高度常量、同一个
 * [QuickMemoActionButton]、同样的圆形胶囊与投影。只换按钮集合——
 * 随手记可用的是「挂实况 / 复制正文 / 删除」，不像随口记还有待办、插图与录音。
 */
@Composable
private fun FlashNoteDetailBottomBar(
    isPinned: Boolean,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCopyClick: () -> Unit,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundPalette = rememberAppBackgroundStylePalette(
        enabled = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled
    )
    val barHeight = IntegratedFloatingBarHeight + IntegratedFloatingBarExtraHeight
    val itemWidth = 72.dp
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barSurfaceAlpha = MySettings.normalizeAppBackgroundCardAlphaPercent(
        LocalAppBackgroundCardAlphaPercent.current
    ) / 100f
    val containerColor = if (backgroundMode) {
        backgroundPalette.surface.copy(alpha = barSurfaceAlpha)
    } else if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val indicatorColor = if (backgroundMode) {
        backgroundPalette.accent
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (backgroundMode) {
        backgroundPalette.content
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val disabledColor = contentColor.copy(alpha = 0.38f)

    Surface(
        modifier = modifier
            .padding(bottom = LocalAppPageBottomPadding.current)
            .padding(horizontal = 16.dp)
            .padding(bottom = IntegratedFloatingBarShadowPadding)
            .height(barHeight),
        shape = CircleShape,
        color = if (backgroundMode) Color.Transparent else containerColor,
        contentColor = contentColor,
        shadowElevation = if (backgroundMode) 0.dp else 6.dp
    ) {
        val barContent: @Composable () -> Unit = {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                QuickMemoActionButton(
                    icon = Icons.Rounded.NotificationsActive,
                    contentDescription = if (isPinned) "取消实况挂起" else "挂到实况",
                    isActive = isPinned,
                    width = itemWidth,
                    indicatorColor = indicatorColor,
                    contentColor = contentColor,
                    disabledColor = disabledColor,
                    onClick = onPinClick
                )
                QuickMemoActionButton(
                    icon = Icons.Rounded.ContentCopy,
                    contentDescription = "复制正文",
                    isActive = false,
                    enabled = true,
                    width = itemWidth,
                    indicatorColor = indicatorColor,
                    contentColor = contentColor,
                    disabledColor = disabledColor,
                    onClick = onCopyClick
                )
                QuickMemoActionButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    isActive = false,
                    enabled = true,
                    width = itemWidth,
                    indicatorColor = indicatorColor,
                    contentColor = contentColor,
                    disabledColor = disabledColor,
                    onClick = onDeleteClick
                )
            }
        }
        if (backgroundMode) {
            AppBackgroundGlassSurface(
                enabled = true,
                miuiBlurEnabled = miuiBlurEnabled,
                modifier = Modifier.fillMaxHeight(),
                shape = CircleShape,
                surfaceColor = containerColor,
            ) {
                barContent()
            }
        } else {
            barContent()
        }
    }
}

/** 详情页大图。解码失败时显示占位提示而不是空白。 */
@Composable
private fun FlashNoteDetailImage(
    relativePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(relativePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(relativePath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val file = NoteAttachmentStore.fileForRelativePath(context, relativePath)
                if (!file.exists()) return@runCatching null
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }

    val image = bitmap
    if (image == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "图片加载失败",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Image(
        bitmap = image,
        contentDescription = "随手记图片",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable {
                val paragraph = NoteParagraph(
                    type = NoteParagraphType.IMAGE,
                    attachmentPath = relativePath
                )
                NoteAttachmentStore.openAttachmentIntent(context, paragraph)?.let(context::startActivity)
            }
            .padding(6.dp)
    )
}
