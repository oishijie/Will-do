package com.antgskds.calendarassistant.feature.quickmemo.ui.connector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.feature.note.data.local.NoteEntity
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSource
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSummaryState
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphType
import com.antgskds.calendarassistant.feature.note.domain.model.plainTextContent
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.FlashNoteListEntry
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.FlashNoteDetailScreen

/**
 * 闪记详情页。
 *
 * 与速记详情分开成独立页面而非复用 `QuickMemoDetailPage`：速记详情围绕
 * 「正文 + 提醒 + 语音 + 日程建议」组织，闪记是「摘要 + 正文 + 来源溯源」，
 * 强行合并会让两边都出现大量条件分支。
 */
@Composable
fun FlashNoteDetailRoute(
    noteId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    hapticEnabled: Boolean = true,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false
) {
    val notes by viewModel.notes.collectAsState()
    val note = remember(notes, noteId) {
        notes.firstOrNull { it.id == noteId } as NoteEntity?
    }
    // 挂在实况上的随手记（全局唯一）。据此还原铃铛激活态，退出重进不再复位。
    val pinnedFlashNoteId by viewModel.pinnedFlashNoteId.collectAsState()

    if (note == null) {
        FlashNoteMissingScreen(onBack = onBack)
        return
    }

    val entry = remember(note) { note.toFlashNoteEntry() }

    FlashNoteDetailScreen(
        entry = entry,
        isPinned = pinnedFlashNoteId == noteId,
        onBack = onBack,
        hapticEnabled = hapticEnabled,
        backgroundMode = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        onOpenSource = { viewModel.openFlashNoteSource(noteId) },
        onSaveTitle = { title -> viewModel.updateFlashNoteTitle(noteId, title) },
        onSaveBody = { body -> viewModel.updateFlashNoteBody(noteId, body) },
        onDelete = { viewModel.deleteNote(noteId) { onBack() } },
        onTogglePin = { pinned ->
            if (pinned) viewModel.pinFlashNote(noteId) else viewModel.clearPinnedFlashNote()
        }
    )
}

@Composable
private fun FlashNoteMissingScreen(onBack: () -> Unit) {
    FlashNoteDetailScreen(
        entry = FlashNoteListEntry(
            key = "missing",
            title = "闪记不存在或已删除",
            previewText = null,
            summaryText = null,
            summaryPending = false,
            summaryFailed = false,
            siteName = null,
            url = "",
            requiresClipboard = false,
            pinned = false,
            updatedAt = 0L
        ),
        isPinned = false,
        onBack = onBack,
        hapticEnabled = true,
        onOpenSource = {}
    )
}

/** 把notes 行转成列表/详情共用的展示模型。 */
internal fun NoteEntity.toFlashNoteEntry(): FlashNoteListEntry {
    val document = document()
    val url = document.flashSourceUrl()
    val summary = document.flashSummaryParagraph()?.text.orEmpty()
    val image = document.paragraphs.firstOrNull { it.type == NoteParagraphType.IMAGE }
    val body = document.paragraphs
        .asSequence()
        .filterNot {
            it.type == NoteParagraphType.SUMMARY ||
                it.type == NoteParagraphType.SOURCE_LINK ||
                it.type == NoteParagraphType.IMAGE
        }
        .map { it.plainTextContent().trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()

    return FlashNoteListEntry(
        key = id?.let { "note:$it" } ?: "transient:$updatedAt",
        title = displayTitle,
        previewText = body.takeIf { it.isNotBlank() },
        summaryText = summary.takeIf {
            it.isNotBlank() && !FlashNoteSummaryState.isPlaceholder(it)
        },
        summaryPending = document.flashSummaryPending(),
        summaryFailed = document.flashSummaryFailed(),
        siteName = document.flashSourceParagraph()?.attachmentName?.takeIf { it.isNotBlank() },
        url = url,
        requiresClipboard = url.isNotBlank() && FlashNoteSource(url = url).requiresClipboardRelay,
        pinned = pinnedAt != null,
        updatedAt = updatedAt,
        imageRelativePath = image?.attachmentPath.orEmpty(),
        imageDisplayName = image?.attachmentName.orEmpty()
    )
}
