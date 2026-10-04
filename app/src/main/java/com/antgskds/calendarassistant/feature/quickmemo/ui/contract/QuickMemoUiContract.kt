package com.antgskds.calendarassistant.feature.quickmemo.ui.contract

import com.antgskds.calendarassistant.feature.quickmemo.application.audio.AudioPlaybackState
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoReminderEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionEntity

data class QuickMemoListUiState(
    val memos: List<QuickMemoEntity>,
    val suggestions: List<QuickMemoSuggestionEntity>,
    val playbackState: AudioPlaybackState,
    val pinnedMemoId: Long?,
    /** 当前展示的分段：速记或闪记。默认速记，保持既有行为不变。 */
    val segment: MemoStreamSegment = MemoStreamSegment.QUICK_MEMO,
    /** 闪记条目（notes 表）。仅在闪记分段使用。 */
    val flashNotes: List<FlashNoteListEntry> = emptyList(),
    /** 闪记条目 id → notes 表主键，用于跳转与删除。 */
    val flashNoteIds: Map<String, Long> = emptyMap()
)

/**
 * 闪记在速记页的列表条目。
 *
 * 速记与闪记底层模型不同（`QuickMemoEntity` 是扁平记录，`NoteDocument` 是段落流），
 * 强行合成同一个实体只会让两边都变复杂，因此这里只抽出「列表展示所需的最小信息」。
 */
data class FlashNoteListEntry(
    val key: String,
    val title: String,
    val previewText: String?,
    val summaryText: String?,
    val summaryPending: Boolean,
    val summaryFailed: Boolean,
    val siteName: String?,
    val url: String,
    val requiresClipboard: Boolean,
    val pinned: Boolean,
    val updatedAt: Long,
    /** 图片闪记的原图相对路径（`filesDir` 下）；非图片闪记为空串。 */
    val imageRelativePath: String = "",
    /** 图片闪记的展示名，用作标题。 */
    val imageDisplayName: String = ""
)

data class QuickMemoDetailUiState(
    val memo: QuickMemoEntity?,
    val reminders: List<QuickMemoReminderEntity>,
    val suggestions: List<QuickMemoSuggestionEntity>,
    val playbackState: AudioPlaybackState,
    val isPinned: Boolean
)

sealed interface QuickMemoUiAction {
    /** 切换速记 / 闪记分段。 */
    data class SelectSegment(val segment: MemoStreamSegment) : QuickMemoUiAction
    /** 点击闪记条目：打开详情页（正文、摘要、图片都在里面）。 */
    data class OpenFlashNote(val key: String) : QuickMemoUiAction
    /** 在详情页里点「打开原文」/「复制链接」。 */
    data class OpenFlashNoteSource(val key: String) : QuickMemoUiAction
    /** 请求删除闪记条目；由上层弹确认框后回[ConfirmDeleteFlashNote]。 */
    data class RequestDeleteFlashNote(val key: String) : QuickMemoUiAction
    /** 用户已在确认框点「删除」，真正落库删除。 */
    data class ConfirmDeleteFlashNote(val key: String) : QuickMemoUiAction
    data class OpenDetail(val memoId: Long) : QuickMemoUiAction
    data class RequestDelete(val memo: QuickMemoEntity) : QuickMemoUiAction
    data class Delete(val memoId: Long) : QuickMemoUiAction
    data class ToggleTodoCompletion(val memoId: Long) : QuickMemoUiAction
    data class MarkTodo(val memoId: Long) : QuickMemoUiAction
    data class RemoveTodo(val memoId: Long) : QuickMemoUiAction
    data class TogglePinned(val memoId: Long, val isPinned: Boolean) : QuickMemoUiAction
    data class ToggleAudio(val audioPath: String?) : QuickMemoUiAction
    data class UpdateBody(val memoId: Long, val body: String) : QuickMemoUiAction
    data class SaveReminder(
        val memoId: Long,
        val reminderId: Long?,
        val reminderAt: Long?,
        val reminderRRule: String
    ) : QuickMemoUiAction
    data class DeleteReminder(val reminderId: Long) : QuickMemoUiAction
    data class AttachImage(
        val memoId: Long,
        val imagePath: String,
        val onResult: (Result<Unit>) -> Unit
    ) : QuickMemoUiAction
    data class RemoveImage(
        val memoId: Long,
        val onResult: (Result<Unit>) -> Unit
    ) : QuickMemoUiAction
    data class AttachVoice(
        val memoId: Long,
        val audioPath: String,
        val durationMs: Long,
        val onResult: (Result<Unit>) -> Unit
    ) : QuickMemoUiAction
    data class RetryTranscription(val memoId: Long) : QuickMemoUiAction
    data class CreateSuggestionEvent(val suggestionId: Long) : QuickMemoUiAction
}
