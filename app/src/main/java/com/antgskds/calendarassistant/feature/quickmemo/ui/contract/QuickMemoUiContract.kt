package com.antgskds.calendarassistant.feature.quickmemo.ui.contract

import com.antgskds.calendarassistant.feature.quickmemo.application.audio.AudioPlaybackState
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoFolderEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoReminderEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionEntity

data class QuickMemoListUiState(
    val memos: List<QuickMemoEntity>,
    val suggestions: List<QuickMemoSuggestionEntity>,
    val playbackState: AudioPlaybackState,
    val pinnedMemoId: Long?,
    /** 文件夹列表，按创建时间升序。 */
    val folders: List<QuickMemoFolderEntity> = emptyList(),
    /** 当前文件夹筛选：null = 全部，空串 = 未分组，其余为文件夹 id。 */
    val selectedFolderId: String? = null,
    /** 是否处于多选模式。 */
    val selectionMode: Boolean = false,
    /** 多选模式下已选中的速记 id。 */
    val selectedMemoIds: Set<Long> = emptySet()
)

/**
 * 速记列表的浏览状态。
 *
 * 放在 ViewModel 而不是 Composable 里，是为了让「从详情页返回列表」时
 * 文件夹筛选与多选状态不重置——返回不该让用户重新找一遍位置。
 */
data class QuickMemoBrowserState(
    /** null = 全部；空串 = 未分组；其余为文件夹 id。 */
    val folderId: String? = null,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val foldersVisible: Boolean = false
)

data class QuickMemoDetailUiState(
    val memo: QuickMemoEntity?,
    val reminders: List<QuickMemoReminderEntity>,
    val suggestions: List<QuickMemoSuggestionEntity>,
    val playbackState: AudioPlaybackState,
    val isPinned: Boolean
)

sealed interface QuickMemoUiAction {
    data class OpenDetail(val memoId: Long) : QuickMemoUiAction
    data class RequestDelete(val memo: QuickMemoEntity) : QuickMemoUiAction
    data class Delete(val memoId: Long) : QuickMemoUiAction
    data class ToggleTodoCompletion(val memoId: Long) : QuickMemoUiAction
    data class MarkTodo(val memoId: Long) : QuickMemoUiAction
    data class RemoveTodo(val memoId: Long) : QuickMemoUiAction
    data class TogglePinned(val memoId: Long, val isPinned: Boolean) : QuickMemoUiAction
    data class ToggleAudio(val audioPath: String?) : QuickMemoUiAction
    data class UpdateBody(val memoId: Long, val body: String) : QuickMemoUiAction
    data class UpdateTitle(val memoId: Long, val title: String) : QuickMemoUiAction
    /** 切换文件夹筛选；[folderId] 语义同 [QuickMemoBrowserState.folderId]。 */
    data class SelectFolder(val folderId: String?) : QuickMemoUiAction
    /** 长按速记进入多选模式，并把该条计入已选。 */
    data class EnterSelectionMode(val memoId: Long) : QuickMemoUiAction
    data class ToggleMemoSelection(val memoId: Long) : QuickMemoUiAction
    data class SelectAllMemos(val memoIds: List<Long>) : QuickMemoUiAction
    data class MoveSelectedMemos(val folderId: String?) : QuickMemoUiAction
    /** 单条闪记移动到指定文件夹（null = 移出分组）。 */
    data class MoveMemo(val memoId: Long, val folderId: String?) : QuickMemoUiAction
    /** 删除多选模式下已选中的速记。 */
    data object DeleteSelectedMemos : QuickMemoUiAction
    /** 退出多选模式并清空已选。 */
    data object ExitSelection : QuickMemoUiAction
    data class CreateFolder(val name: String) : QuickMemoUiAction
    data class RenameFolder(val folderId: String, val name: String) : QuickMemoUiAction
    data class DeleteFolder(val folderId: String) : QuickMemoUiAction
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
