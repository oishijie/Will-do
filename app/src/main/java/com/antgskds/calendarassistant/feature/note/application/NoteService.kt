package com.antgskds.calendarassistant.feature.note.application

import com.antgskds.calendarassistant.feature.note.domain.model.NoteDocument
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphType
import com.antgskds.calendarassistant.feature.note.domain.model.plainTextContent
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
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.NoopSpeechTranscriber
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.SpeechTranscriber
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.TranscriptionResult
import com.antgskds.calendarassistant.shared.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi

class NoteService(
    private val repository: NoteRepository,
    private val appScope: CoroutineScope,
    /**
     * 语音转写器。闪记的语音转写复用速记同一套 ASR（[SpeechTranscriber]），
     * 但转写结果落notes 表且**不触发日程建议**，与速记行为区分开。
     */
    private val speechTranscriber: SpeechTranscriber = NoopSpeechTranscriber(),
    /**
     * 实况通知命令口：把一条随手记挂到状态栏。可空——测试与不需要胶囊的环境可不注入。
     */
    private val capsuleCommandApi: CapsuleCommandApi? = null,
    /**
     * 设置读取口：新建随手记时据此决定摘要段落落 PENDING 还是 SKIPPED。可空——不注入时按关闭处理。
     */
    private val settingsQueryApi: SettingsQueryApi? = null,
) {
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes: StateFlow<List<NoteEntity>> = _notes.asStateFlow()

    /** 随手记 AI 摘要是否开启。关闭时新建条目直接落 SKIPPED，不会停在「摘要生成中」。 */
    fun aiSummaryEnabled(): Boolean =
        settingsQueryApi?.settings?.value?.flashNoteAiSummaryEnabled ?: false

    /** 新建随手记时摘要段落的初始状态：开启则排队生成，关闭则直接标记不生成。 */
    private fun defaultFlashSummaryState(): String =
        if (aiSummaryEnabled()) FlashNoteSummaryState.PENDING else FlashNoteSummaryState.SKIPPED

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

    /**
     * 把一条随手记挂到实况通知（常驻，等用户点通知上的「移除」）。
     *
     * 展示文本取**正文首段**——随手记正文可能是多段长文，整段塞进胶囊会把状态栏撑爆；
     * 正文为空时退回标题。
     *
     * 注意不能直接读 [NoteEntity.plainText] 的首行：随手记的段落结构固定是
     * 「SUMMARY 摘要 → 正文 → 可能的 SOURCE_LINK」，而 `plainText` 会把 SUMMARY
     * 也按顺序拼进去。正文首段前面永远压着那个摘要段落（内容为 `__PENDING__`
     * 这类占位符或 AI 摘要），直接取首行会把这个状态串当成笔记内容挂上胶囊。
     * 所以这里按段落类型筛出真正的正文。
     */
    suspend fun pinFlashNote(noteId: Long): Boolean = withContext(Dispatchers.IO) {
        val note = repository.getNote(noteId) ?: return@withContext false
        val text = note.document().paragraphs
            .asSequence()
            .filterNot {
                it.type == NoteParagraphType.SUMMARY ||
                    it.type == NoteParagraphType.SOURCE_LINK
            }
            .map { it.plainTextContent().trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: note.title.trim()
        if (text.isBlank()) return@withContext false
        capsuleCommandApi?.showTextFlashNote(noteId, text)
        true
    }

    /** 取消随手记的实况挂起。同一时刻只挂一条，直接清即可。 */
    suspend fun clearPinnedFlashNote(): Boolean = withContext(Dispatchers.IO) {
        capsuleCommandApi?.clearTextFlashNote()
        true
    }

    /**
     * 覆盖随手记的正文。
     *
     * 走 [NoteDocument.withFloatingText]：只替换「浮动纯文本行」，
     * 摘要 / 图片 / 原文链接等结构化段落原样保留——
     * 否则在详情页改一次正文，就会把 AI 摘要段落和图片段落一起冲掉。
     */
    suspend fun updateFlashNoteBody(noteId: Long, body: String) = withContext(Dispatchers.IO) {
        val note = repository.getNote(noteId) ?: return@withContext
        repository.saveNote(
            note.id,
            note.title,
            note.document().withFloatingText(body),
            note.createdAt
        )
    }

    /**
     * 覆盖随手记标题（详情页内联编辑，防抖后调用）。
     *
     * 只动 title 字段，段落结构与 [updateFlashNoteBody] 一致地原样回写，
     * 避免编辑标题时把摘要/图片/原文链接段落冲掉。标题长度由仓储统一裁剪。
     */
    suspend fun updateFlashNoteTitle(noteId: Long, title: String) = withContext(Dispatchers.IO) {
        val note = repository.getNote(noteId) ?: return@withContext
        repository.saveNote(
            note.id,
            title,
            note.document(),
            note.createdAt
        )
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
        summaryState: String? = null
    ): Long? = withContext(Dispatchers.IO) {
        if (repository.findFlashNoteByUrl(source.url) != null) return@withContext null
        repository.createFlashNote(source, content, summaryState ?: defaultFlashSummaryState())
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
    suspend fun createTextFlashNote(
        text: String,
        title: String? = null,
        summaryState: String? = null
    ): Long? =
        withContext(Dispatchers.IO) {
            val normalized = text.trim()
            if (normalized.isEmpty()) return@withContext null
            if (repository.findFlashNoteByContent(normalized) != null) return@withContext null
            repository.createTextFlashNote(
                text = normalized,
                summaryState = summaryState ?: defaultFlashSummaryState(),
                title = title
            )
        }

    /**
     * 收藏一条语音闪记。
     *
     * 音频先落notes 附件目录再入库；转写文本由 [updateFlashVoiceText] 事后回填。
     */
    suspend fun createVoiceFlashNote(
        audioRelativePath: String,
        displayName: String,
        audioDurationMs: Long
    ): Long? = withContext(Dispatchers.IO) {
        repository.createVoiceFlashNote(audioRelativePath, displayName, audioDurationMs)
    }

    /** 回填语音转写文本，并设置摘要状态。 */
    suspend fun updateFlashVoiceText(
        noteId: Long,
        text: String,
        summaryState: String = FlashNoteSummaryState.SKIPPED
    ): Boolean = withContext(Dispatchers.IO) {
        repository.updateFlashVoiceText(noteId, text, summaryState)
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
    /**
     * 语音闪记的转写入口。
     *
     * 音频入库后跑一次 ASR，把转写文本回填到同一条闪记。失败时只标记摘要为
     * FAILED，不删除条目——音频本身仍有价值。
     *
     * @param audioAbsolutePath 音频的绝对路径（转写器需要可读的真实路径）。
     * @param onTranscribed 转写成功回调，参数为识别文本。
     */
    suspend fun transcribeVoiceFlashNote(
        noteId: Long,
        audioAbsolutePath: String,
        onTranscribed: (String) -> Unit = {}
    ) {
        when (val result = speechTranscriber.transcribe(audioAbsolutePath)) {
            is TranscriptionResult.Success -> {
                val text = result.text.trim()
                withContext(Dispatchers.IO) {
                    if (text.isEmpty()) {
                        repository.updateFlashVoiceText(noteId, "", FlashNoteSummaryState.FAILED)
                    } else {
                        repository.updateFlashVoiceText(noteId, text, FlashNoteSummaryState.SKIPPED)
                    }
                }
                if (text.isNotEmpty()) onTranscribed(text)
            }

            is TranscriptionResult.Failure -> {
                withContext(Dispatchers.IO) {
                    repository.updateFlashVoiceText(noteId, "", FlashNoteSummaryState.FAILED)
                }
            }
        }
    }
}
