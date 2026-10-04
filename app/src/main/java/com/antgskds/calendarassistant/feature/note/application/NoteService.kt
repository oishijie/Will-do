package com.antgskds.calendarassistant.feature.note.application

import com.antgskds.calendarassistant.feature.note.domain.model.NoteDocument
import com.antgskds.calendarassistant.feature.note.data.local.NoteEntity
import com.antgskds.calendarassistant.feature.note.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSource
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSummaryState

class NoteService(
    private val repository: NoteRepository,
    private val appScope: CoroutineScope
) {
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes: StateFlow<List<NoteEntity>> = _notes.asStateFlow()

    fun start() {
        appScope.launch(Dispatchers.IO) {
            repository.notes.collect { list ->
                _notes.value = list
            }
        }
    }

    suspend fun getNote(id: Long): NoteEntity? = withContext(Dispatchers.IO) {
        repository.getNote(id)
    }

    suspend fun saveNote(
        id: Long?,
        title: String,
        document: NoteDocument,
        createdAt: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        repository.saveNote(id, title, document, createdAt)
    }

    suspend fun toggleTodo(noteId: Long, paragraphId: String) = withContext(Dispatchers.IO) {
        val note = repository.getNote(noteId) ?: return@withContext
        val document = note.document()
        val updatedDocument = document.copy(
            paragraphs = document.paragraphs.map { paragraph ->
                if (paragraph.id == paragraphId) {
                    paragraph.copy(checked = !paragraph.checked)
                } else {
                    paragraph
                }
            }
        )
        repository.saveNote(note.id, note.title, updatedDocument, note.createdAt)
    }

    suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        repository.deleteNote(id)
    }

    suspend fun setPinned(noteId: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        repository.setPinned(noteId, pinned)
    }

    /**
     * 收藏一条闪记。
     *
     * @return 条目 id；返回 null 表示该链接已在闪记中，未重复创建。
     */
    suspend fun createFlashNote(
        source: FlashNoteSource,
        content: String,
        summaryState: String = FlashNoteSummaryState.PENDING
    ): Long? = withContext(Dispatchers.IO) {
        if (repository.findFlashNoteByUrl(source.url) != null) return@withContext null
        repository.createFlashNote(source, content, summaryState)
    }

    suspend fun updateFlashSummary(noteId: Long, summary: String): Boolean = withContext(Dispatchers.IO) {
        repository.updateFlashSummary(noteId, summary)
    }

    /**
     * 收藏一条纯文本闪记。
     *
     * 去重按正文完全一致判断：用户重复选中同一段文字收藏时不应产生第二条。
     *
     * @return 条目 id；正文为空或已收藏过则返回 null。
     */
    suspend fun createTextFlashNote(text: String, title: String? = null): Long? =
        withContext(Dispatchers.IO) {
            val normalized = text.trim()
            if (normalized.isEmpty()) return@withContext null
            if (repository.findFlashNoteByContent(normalized) != null) return@withContext null
            repository.createTextFlashNote(normalized, title = title)
        }

    /**
     * 收藏一条图片闪记。
     *
     * 按附件相对路径去重；图片不做 OCR，摘要状态直接记为 [FlashNoteSummaryState.SKIPPED]。
     *
     * @return 条目 id；附件路径为空或已收藏过则返回 null。
     */
    suspend fun createImageFlashNote(
        attachmentPath: String,
        attachmentName: String,
        attachmentMime: String,
        sourceUrl: String? = null,
        siteName: String? = null
    ): Long? = withContext(Dispatchers.IO) {
        if (attachmentPath.isBlank()) return@withContext null
        if (repository.findFlashNoteByAttachment(attachmentPath) != null) return@withContext null
        repository.createImageFlashNote(
            attachmentPath = attachmentPath,
            attachmentName = attachmentName,
            attachmentMime = attachmentMime,
            sourceUrl = sourceUrl,
            siteName = siteName
        )
    }
}
