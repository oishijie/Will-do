package com.antgskds.calendarassistant.feature.note.data

import com.antgskds.calendarassistant.feature.note.domain.model.NoteDocument
import com.antgskds.calendarassistant.feature.note.domain.model.NoteDocumentCodec
import com.antgskds.calendarassistant.feature.note.data.local.NoteEntity
import com.antgskds.calendarassistant.feature.note.domain.model.NoteListStyle
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraph
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphStyle
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphType
import com.antgskds.calendarassistant.feature.note.data.local.NotesDao
import com.antgskds.calendarassistant.feature.note.domain.model.effectiveListStyle
import com.antgskds.calendarassistant.feature.note.domain.model.effectiveParagraphStyle
import com.antgskds.calendarassistant.feature.note.domain.model.withMigratedParagraphStyles
import kotlinx.coroutines.flow.Flow
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSource
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSummaryState

class NoteRepository(
    private val notesDao: NotesDao
) {
    val notes: Flow<List<NoteEntity>> = notesDao.observeNotes()

    suspend fun getNote(id: Long): NoteEntity? = notesDao.getNote(id)

    suspend fun saveNote(
        id: Long?,
        title: String,
        document: NoteDocument,
        createdAt: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        val normalizedTitle = title.trim().take(MAX_TITLE_LENGTH)
        val normalizedDocument = document.withNormalizedParagraphs()
        val entity = NoteEntity(
            id = id,
            title = normalizedTitle,
            plainText = normalizedDocument.plainText(),
            documentJson = NoteDocumentCodec.encode(normalizedDocument),
            createdAt = createdAt ?: now,
            updatedAt = now,
            pinnedAt = id?.let { notesDao.getNote(it)?.pinnedAt }
        )

        return if (id == null) {
            notesDao.insert(entity)
        } else {
            notesDao.update(entity)
            id
        }
    }

    suspend fun deleteNote(id: Long) {
        notesDao.deleteById(id)
    }

    suspend fun setPinned(noteId: Long, pinned: Boolean) {
        notesDao.updatePinnedAt(noteId, if (pinned) System.currentTimeMillis() else null)
    }

    /**
     * 按原文链接查找已有闪记，用于收藏去重。
     *
     * 闪记的链接存放在 SOURCE_LINK 段落里，需要遍历条目比对；条目量级为个人笔记规模，
     * 直接过滤即可，不额外建索引。
     */
    suspend fun findFlashNoteByUrl(url: String): NoteEntity? {
        val target = url.trim()
        if (target.isEmpty()) return null
        return notesDao.getAllNotes().firstOrNull { entity ->
            entity.document().flashSourceUrl() == target
        }
    }

    /**
     * 按正文查找已有闪记，用于纯文本收藏去重。
     *
     * 只比对闪记条目（[NoteDocument.isFlashNote]）的首个正文段落，避免误判普通便签。
     */
    suspend fun findFlashNoteByContent(content: String): NoteEntity? {
        val target = content.trim()
        if (target.isEmpty()) return null
        return notesDao.getAllNotes().firstOrNull { entity ->
            val document = entity.document()
            if (!document.isFlashNote) return@firstOrNull false
            document.paragraphs
                .firstOrNull { it.type == NoteParagraphType.TEXT }
                ?.text
                ?.trim() == target
        }
    }

    /**
     * 按附件路径查找已有图片闪记，用于图片收藏去重。
     */
    suspend fun findFlashNoteByAttachment(attachmentPath: String): NoteEntity? {
        val target = attachmentPath.trim()
        if (target.isEmpty()) return null
        return notesDao.getAllNotes().firstOrNull { entity ->
            entity.document().paragraphs.any { paragraph ->
                paragraph.type == NoteParagraphType.IMAGE && paragraph.attachmentPath.trim() == target
            }
        }
    }

    /**
     * 创建闪记条目。
     *
     * 段落顺序：摘要（占位 PENDING）→ 原文链接 → 正文（若有）。摘要置前是为了让列表
     * 卡片无需特殊处理即可读到摘要原文。
     */
    suspend fun createFlashNote(
        source: FlashNoteSource,
        content: String,
        summaryState: String = FlashNoteSummaryState.PENDING
    ): Long {
        val paragraphs = buildList {
            add(NoteParagraph(text = summaryState, type = NoteParagraphType.SUMMARY))
            add(
                NoteParagraph(
                    text = source.url,
                    type = NoteParagraphType.SOURCE_LINK,
                    attachmentName = source.siteName,
                    attachmentMime = source.kind.name
                )
            )
            val normalized = content.trim()
            if (normalized.isNotEmpty()) {
                normalized.split('\n').forEach { line ->
                    add(NoteParagraph(text = line))
                }
            }
        }
        val document = NoteDocument(paragraphs = paragraphs)
        return saveNote(
            id = null,
            title = source.articleTitle.ifBlank { source.url },
            document = document
        )
    }

    /**
     * 创建纯文本闪记。
     *
     * 段落顺序：摘要（占位 PENDING）→ 正文。没有 SOURCE_LINK 段落，
     * 靠 [NoteDocument.isFlashNote] 的 SUMMARY 判定识别为闪记。
     */
    suspend fun createTextFlashNote(
        text: String,
        summaryState: String = FlashNoteSummaryState.PENDING,
        title: String? = null
    ): Long? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return null

        val paragraphs = buildList {
            add(NoteParagraph(text = summaryState, type = NoteParagraphType.SUMMARY))
            normalized.split('\n').forEach { line ->
                add(NoteParagraph(text = line))
            }
        }
        return saveNote(
            id = null,
            title = title?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_TITLE_LENGTH)
                ?: normalized.lineSequence().first { it.isNotBlank() }.trim().take(MAX_TITLE_LENGTH),
            document = NoteDocument(paragraphs = paragraphs)
        )
    }

    /**
     * 创建图片闪记。
     *
     * 图片存进 notes 附件目录，以 IMAGE 段落引用；按你此前确认的方案**不做 OCR**，
     * 摘要留空状态。原始来源链接（若有）仍写入 SOURCE_LINK 以便溯源。
     */
    suspend fun createImageFlashNote(
        attachmentPath: String,
        attachmentName: String,
        attachmentMime: String,
        sourceUrl: String? = null,
        siteName: String? = null
    ): Long? {
        if (attachmentPath.isBlank()) return null

        val paragraphs = buildList {
            add(NoteParagraph(text = FlashNoteSummaryState.SKIPPED, type = NoteParagraphType.SUMMARY))
            val url = sourceUrl?.trim().orEmpty()
            if (url.isNotEmpty()) {
                add(
                    NoteParagraph(
                        text = url,
                        type = NoteParagraphType.SOURCE_LINK,
                        attachmentName = siteName.orEmpty(),
                        attachmentMime = FlashNoteSource(url = url).kind.name
                    )
                )
            }
            add(
                NoteParagraph(
                    text = "",
                    type = NoteParagraphType.IMAGE,
                    attachmentPath = attachmentPath,
                    attachmentName = attachmentName,
                    attachmentMime = attachmentMime
                )
            )
        }
        return saveNote(
            id = null,
            title = attachmentName.ifBlank { "图片闪记" }.take(MAX_TITLE_LENGTH),
            document = NoteDocument(paragraphs = paragraphs)
        )
    }

    /** 回填闪记摘要。非闪记条目或条目已删除时返回 false。 */
    suspend fun updateFlashSummary(noteId: Long, summary: String): Boolean {
        val entity = notesDao.getNote(noteId) ?: return false
        val current = entity.document()
        val updated = current.withFlashSummary(summary) ?: return false
        saveNote(
            id = noteId,
            title = entity.title,
            document = updated,
            createdAt = entity.createdAt
        )
        return true
    }

    companion object {
        const val MAX_TITLE_LENGTH = 80
    }
}

fun NoteDocument.withNormalizedParagraphs(): NoteDocument {
    val migrated = withMigratedParagraphStyles()
    return migrated.copy(
        paragraphs = migrated.paragraphs.map { paragraph ->
            val normalizedText = if (paragraph.type == NoteParagraphType.DIVIDER || paragraph.type == NoteParagraphType.TABLE) "" else paragraph.text.replace("\r\n", "\n").replace('\r', '\n')
            val normalizedTable = if (paragraph.type == NoteParagraphType.TABLE) paragraph.table?.normalized() else null
            val paragraphStyle = paragraph.effectiveParagraphStyle()
            paragraph.copy(
                style = paragraphStyle,
                listStyle = if (paragraphStyle == NoteParagraphStyle.CODE || paragraphStyle == NoteParagraphStyle.QUOTE || paragraph.isBlockLineForNormalize()) NoteListStyle.NONE else paragraph.effectiveListStyle(),
                text = normalizedText,
                checked = if (paragraph.type == NoteParagraphType.TODO) paragraph.checked else false,
                spans = paragraph.spans
                    .mapNotNull { span ->
                        val start = span.start.coerceIn(0, normalizedText.length)
                        val end = span.end.coerceIn(0, normalizedText.length)
                        if (start >= end || span.isEmpty()) null else span.copy(start = start, end = end)
                    },
                attachmentPath = if (paragraph.type == NoteParagraphType.IMAGE || paragraph.type == NoteParagraphType.FILE) paragraph.attachmentPath.trim() else "",
                attachmentName = if (paragraph.type == NoteParagraphType.IMAGE || paragraph.type == NoteParagraphType.FILE) paragraph.attachmentName.trim() else "",
                attachmentMime = if (paragraph.type == NoteParagraphType.IMAGE || paragraph.type == NoteParagraphType.FILE) paragraph.attachmentMime.trim() else "",
                table = normalizedTable
            )
        }
    )
}

private fun NoteParagraph.isBlockLineForNormalize(): Boolean {
    return type == NoteParagraphType.IMAGE || type == NoteParagraphType.FILE || type == NoteParagraphType.DIVIDER || type == NoteParagraphType.TABLE
}
