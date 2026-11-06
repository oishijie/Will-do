package com.antgskds.calendarassistant.feature.quickmemo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoLink
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoLinkRefreshPolicy
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickMemoDao {
    @Query("SELECT * FROM quick_memo_folders ORDER BY createdAt ASC")
    fun observeFolders(): Flow<List<QuickMemoFolderEntity>>

    @Query("SELECT * FROM quick_memo_folders ORDER BY createdAt ASC")
    suspend fun getAllFolders(): List<QuickMemoFolderEntity>

    @Query("SELECT * FROM quick_memo_folders WHERE id = :id LIMIT 1")
    suspend fun getFolder(id: String): QuickMemoFolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: QuickMemoFolderEntity): Long

    @Query("UPDATE quick_memos SET folder_id = :folderId, updated_at = :now WHERE id IN (:ids)")
    suspend fun assignFolder(ids: List<Long>, folderId: String?, now: Long): Int

    @Query("UPDATE quick_memos SET folder_id = NULL, updated_at = :now WHERE folder_id = :folderId")
    suspend fun unassignFolder(folderId: String, now: Long)

    @Query("UPDATE quick_memo_folders SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renameFolder(id: String, name: String, now: Long): Int

    @Query("DELETE FROM quick_memo_folders WHERE id = :id")
    suspend fun deleteFolderRow(id: String)

    @Transaction
    suspend fun deleteFolder(id: String, now: Long) {
        unassignFolder(id, now)
        deleteFolderRow(id)
    }

    @Transaction
    suspend fun moveToFolder(ids: List<Long>, folderId: String?, now: Long): Int {
        require(folderId == null || getFolder(folderId) != null) { "文件夹不存在" }
        return assignFolder(ids.distinct(), folderId, now)
    }

    @Query("UPDATE quick_memos SET title = :title, updated_at = :now WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, now: Long)

    @Query("SELECT * FROM quick_memos WHERE link_key = :key LIMIT 1")
    suspend fun findLink(key: String): QuickMemoEntity?

    @Transaction
    suspend fun insertLinkIfAbsent(memo: QuickMemoEntity): Long {
        val key = requireNotNull(memo.linkKey)
        val saved = findLink(key) ?: return insertQuickMemo(memo)
        val link = QuickMemoLink(requireNotNull(memo.sourceUrl), "", "", key)
        if (QuickMemoLinkRefreshPolicy.needsRefresh(saved.sourceUrl, link)) {
            refreshLinkSource(
                requireNotNull(saved.id),
                link.url,
                QuickMemoLinkRefreshPolicy.refreshedBody(saved.bodyText, saved.sourceUrl, link.url),
                memo.updatedAt
            )
        }
        return requireNotNull(saved.id)
    }

    @Query("UPDATE quick_memos SET source_url = :url, body_text = :body, updated_at = :now WHERE id = :id")
    suspend fun refreshLinkSource(id: Long, url: String, body: String, now: Long)

    @Query("UPDATE quick_memos SET body_text = :body, updated_at = :now WHERE id = :id")
    suspend fun updateBody(id: Long, body: String, now: Long)

    @Query("UPDATE quick_memos SET title = :title, body_text = :body, updated_at = :now WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, body: String, now: Long): Int

    // 回收站条目（deleted_at 非空）与归档条目（archived_at 非空）不进主列表
    @Query("SELECT * FROM quick_memos WHERE deleted_at IS NULL AND archived_at IS NULL ORDER BY sort_rank ASC, updated_at DESC")
    fun observeQuickMemos(): Flow<List<QuickMemoEntity>>

    /** 归档列表：按归档时间倒序。 */
    @Query("SELECT * FROM quick_memos WHERE archived_at IS NOT NULL AND deleted_at IS NULL ORDER BY archived_at DESC")
    fun observeArchivedQuickMemos(): Flow<List<QuickMemoEntity>>

    @Query("SELECT * FROM quick_memos WHERE archived_at IS NOT NULL AND deleted_at IS NULL ORDER BY archived_at DESC")
    suspend fun getArchivedQuickMemos(): List<QuickMemoEntity>

    /** 回收站列表：按删除时间倒序。 */
    @Query("SELECT * FROM quick_memos WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeDeletedQuickMemos(): Flow<List<QuickMemoEntity>>

    @Query("SELECT * FROM quick_memos WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    suspend fun getDeletedQuickMemos(): List<QuickMemoEntity>

    /** 归档（可恢复）：保留内容，不出现在主列表。 */
    @Query("UPDATE quick_memos SET archived_at = :at, updated_at = :at WHERE id = :id")
    suspend fun markQuickMemoArchived(id: Long, at: Long)

    /** 从归档恢复到主列表。 */
    @Query("UPDATE quick_memos SET archived_at = NULL, updated_at = :at WHERE id = :id")
    suspend fun restoreQuickMemoArchived(id: Long, at: Long)

    /** 软删除：进回收站。 */
    @Query("UPDATE quick_memos SET deleted_at = :at, updated_at = :at WHERE id = :id")
    suspend fun markQuickMemoDeleted(id: Long, at: Long)

    /** 从回收站恢复。 */
    @Query("UPDATE quick_memos SET deleted_at = NULL, updated_at = :at WHERE id = :id")
    suspend fun restoreQuickMemo(id: Long, at: Long)

    /** 彻底删除（含音频等物理数据清理由上层负责）。 */
    @Query("DELETE FROM quick_memos WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun purgeQuickMemo(id: Long)

    /** 清空回收站。 */
    @Query("DELETE FROM quick_memos WHERE deleted_at IS NOT NULL")
    suspend fun purgeAllDeletedQuickMemos()

    /** 彻底删除单条已归档（物理文件清理由上层负责）。 */
    @Query("DELETE FROM quick_memos WHERE id = :id AND archived_at IS NOT NULL")
    suspend fun purgeArchivedQuickMemo(id: Long)

    /** 清空已归档。 */
    @Query("DELETE FROM quick_memos WHERE archived_at IS NOT NULL")
    suspend fun purgeAllArchivedQuickMemos()

    /** 已归档的提醒（清空时一并取消调度）。 */
    @Query("SELECT * FROM quick_memo_reminders WHERE quick_memo_id IN (SELECT id FROM quick_memos WHERE archived_at IS NOT NULL)")
    suspend fun getArchivedQuickMemoReminders(): List<QuickMemoReminderEntity>

    @Query("SELECT * FROM quick_memo_reminders ORDER BY trigger_at ASC, id ASC")
    fun observeReminders(): Flow<List<QuickMemoReminderEntity>>

    @Query("SELECT * FROM quick_memo_suggestions ORDER BY created_at DESC")
    fun observeSuggestions(): Flow<List<QuickMemoSuggestionEntity>>

    @Query("SELECT * FROM quick_memos WHERE id = :id LIMIT 1")
    suspend fun getQuickMemo(id: Long): QuickMemoEntity?

    @Query("SELECT * FROM quick_memos WHERE deleted_at IS NULL AND archived_at IS NULL ORDER BY created_at ASC")
    suspend fun getAllQuickMemos(): List<QuickMemoEntity>

    @Query("SELECT * FROM quick_memo_reminders ORDER BY trigger_at ASC, id ASC")
    suspend fun getAllReminders(): List<QuickMemoReminderEntity>

    @Query("SELECT * FROM quick_memo_reminders WHERE quick_memo_id = :quickMemoId ORDER BY trigger_at ASC, id ASC")
    suspend fun getRemindersForMemo(quickMemoId: Long): List<QuickMemoReminderEntity>

    @Query("SELECT * FROM quick_memo_reminders WHERE id = :id LIMIT 1")
    suspend fun getReminder(id: Long): QuickMemoReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: QuickMemoReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: QuickMemoReminderEntity)

    @Query("DELETE FROM quick_memo_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long): Int

    @Query("DELETE FROM quick_memo_reminders WHERE quick_memo_id = :quickMemoId")
    suspend fun deleteRemindersForMemo(quickMemoId: Long): Int

    @Query("SELECT * FROM quick_memos WHERE type = 'VOICE' AND transcription_status IN ('PENDING', 'PROCESSING') ORDER BY created_at ASC")
    suspend fun getUnfinishedVoiceMemos(): List<QuickMemoEntity>

    @Query("UPDATE quick_memos SET transcription_status = 'FAILED', updated_at = :updatedAt WHERE type = 'VOICE' AND transcription_status = 'PROCESSING'")
    suspend fun markProcessingVoiceMemosFailed(updatedAt: Long): Int

    @Query("UPDATE quick_memos SET transcription_status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTranscriptionStatus(id: Long, status: String, updatedAt: Long): Int

    @Query("UPDATE quick_memos SET transcription_status = :status, body_text = :bodyText, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTranscriptionStatusAndBody(id: Long, status: String, bodyText: String, updatedAt: Long): Int

    @Query("SELECT MIN(sort_rank) FROM quick_memos")
    suspend fun getMinSortRank(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickMemo(memo: QuickMemoEntity): Long

    @Update
    suspend fun updateQuickMemo(memo: QuickMemoEntity)

    @Delete
    suspend fun deleteQuickMemo(memo: QuickMemoEntity)

    @Query("DELETE FROM quick_memos WHERE id = :id")
    suspend fun deleteQuickMemoById(id: Long)

    @Query("DELETE FROM quick_memo_suggestions")
    suspend fun deleteAllSuggestions()

    @Query("DELETE FROM quick_memos")
    suspend fun deleteAllQuickMemos()

    @Transaction
    suspend fun deleteAllQuickMemoData() {
        deleteAllSuggestions()
        deleteAllQuickMemos()
    }

    @Query("UPDATE quick_memos SET sort_rank = :sortRank WHERE id = :id")
    suspend fun updateSortRank(id: Long, sortRank: Long)

    @Transaction
    suspend fun updateSortRanks(ids: List<Long>) {
        ids.forEachIndexed { index, id ->
            updateSortRank(id, index.toLong() * 1_000L)
        }
    }

    @Query("SELECT * FROM quick_memo_suggestions WHERE quick_memo_id = :quickMemoId ORDER BY created_at DESC")
    fun observeSuggestionsForMemo(quickMemoId: Long): Flow<List<QuickMemoSuggestionEntity>>

    @Query("SELECT * FROM quick_memo_suggestions WHERE quick_memo_id = :quickMemoId ORDER BY created_at DESC")
    suspend fun getSuggestionsForMemo(quickMemoId: Long): List<QuickMemoSuggestionEntity>

    @Query("SELECT * FROM quick_memo_suggestions ORDER BY created_at ASC")
    suspend fun getAllSuggestions(): List<QuickMemoSuggestionEntity>

    @Query("SELECT * FROM quick_memo_suggestions WHERE id = :id LIMIT 1")
    suspend fun getSuggestion(id: Long): QuickMemoSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: QuickMemoSuggestionEntity): Long

    @Update
    suspend fun updateSuggestion(suggestion: QuickMemoSuggestionEntity)

    @Query("DELETE FROM quick_memo_suggestions WHERE quick_memo_id = :quickMemoId")
    suspend fun deleteSuggestionsForMemo(quickMemoId: Long)

    @Query("DELETE FROM quick_memo_suggestions WHERE id IN (:ids)")
    suspend fun deleteSuggestionsByIds(ids: List<Long>): Int
}
