package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme
import com.antgskds.calendarassistant.feature.note.data.attachment.NoteAttachmentStore
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraph
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphType
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.FlashNoteListEntry
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppPageScaffold
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBar
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBarBackButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 闪记详情内容。
 *
 * 列表点击默认跳原文，但收藏的正文留在本地——详情页是查看正文的唯一入口，
 * 否则用户收藏后除了摘要几十字就再也读不到原文。
 */
@Composable
internal fun FlashNoteDetailContent(
    entry: FlashNoteListEntry,
    hapticEnabled: Boolean,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val context = LocalContext.current
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 48.dp)
    ) {
        if (entry.imageRelativePath.isNotBlank()) {
            FlashNoteDetailImage(
                relativePath = entry.imageRelativePath,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }

        Text(
            text = entry.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp)
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

        if (!entry.previewText.isNullOrBlank()) {
            Text(
                text = entry.previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 18.dp)
            )
        } else if (entry.imageRelativePath.isNotBlank() && summaryBlock == null) {
            Text(
                text = "图片闪记不生成摘要，可点击上方图片查看原图。",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryColor,
                modifier = Modifier.padding(top = 18.dp)
            )
        }

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
 * 闪记详情页容器：顶栏（标题 + 返回）+ 内容。
 */
@Composable
internal fun FlashNoteDetailScreen(
    entry: FlashNoteListEntry,
    onBack: () -> Unit,
    hapticEnabled: Boolean,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    onOpenSource: () -> Unit
) {
    val haptics = rememberAppHaptics(hapticEnabled)

    AppBackgroundStyleTheme(enabled = backgroundMode, miuiBlurEnabled = miuiBlurEnabled) {
        val pageContainerColor =
            if (backgroundMode) Color.Transparent else MaterialTheme.colorScheme.background

        AppPageScaffold(
            containerColor = pageContainerColor,
            contentMaxWidth = 760.dp,
            edgeToEdgeContent = true,
            topBar = {
                AppTopBar(
                    title = "闪记",
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
            FlashNoteDetailContent(
                entry = entry,
                hapticEnabled = hapticEnabled,
                onOpenSource = onOpenSource,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            )
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
        contentDescription = "闪记图片",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable {
                val paragraph = NoteParagraph(
                    type = com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphType.IMAGE,
                    attachmentPath = relativePath
                )
                NoteAttachmentStore.openAttachmentIntent(context, paragraph)?.let(context::startActivity)
            }
            .padding(6.dp)
    )
}
