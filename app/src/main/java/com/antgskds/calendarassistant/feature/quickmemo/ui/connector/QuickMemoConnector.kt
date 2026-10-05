package com.antgskds.calendarassistant.feature.quickmemo.ui.connector

import android.content.Context
import android.widget.Toast
import com.antgskds.calendarassistant.feature.quickmemo.application.audio.AudioPlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleType
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoDetailUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoListUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoUiAction
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.QuickMemoDetailScreen
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.QuickMemoScreen
import com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoAutoStopPolicy
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.FlashNoteListEntry
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.MemoStreamSegment
import com.antgskds.calendarassistant.feature.note.data.local.NoteEntity
import com.antgskds.calendarassistant.feature.note.data.attachment.NoteAttachmentStore
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraph
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphType
import com.antgskds.calendarassistant.feature.note.domain.model.plainTextContent
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSource
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSummaryState
import com.antgskds.calendarassistant.feature.note.flash.FlashNoteNavigator
import com.antgskds.calendarassistant.feature.settings.developer.application.DemoModeDataFactory

/**
 * 闪记条目列表：把 notes 表压成列表展示所需的最小信息，并按关键词过滤。
 *
 * 只收录闪记（有 SOURCE_LINK 段落）；历史迁移来的普通便签仍走设置页的独立列表。
 */
internal data class FlashNoteStream(
    val entries: List<FlashNoteListEntry>,
    val idByKey: Map<String, Long>,
    val byKey: Map<String, FlashNoteListEntry>
)

internal fun buildFlashNoteStream(
    notes: List<NoteEntity>,
    searchQuery: String
): FlashNoteStream {
    val flashNotes = notes.filter { it.document().isFlashNote }

    val entries = flashNotes.mapIndexed { index, note ->
        val document = note.document()
        val key = note.id?.let { "note:$it" } ?: "transient:${note.updatedAt}:$index"
        val url = document.flashSourceUrl()
        val summary = document.flashSummaryParagraph()?.text.orEmpty()
        val imageParagraph = document.paragraphs.firstOrNull { it.type == NoteParagraphType.IMAGE }
        val preview = document.paragraphs
            .asSequence()
            .filterNot {
                it.type == NoteParagraphType.SUMMARY ||
                    it.type == NoteParagraphType.SOURCE_LINK ||
                    it.type == NoteParagraphType.TODO ||
                    it.type == NoteParagraphType.IMAGE
            }
            .map { it.plainTextContent().trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        key to FlashNoteListEntry(
            key = key,
            title = note.displayTitle,
            previewText = preview.takeIf { it.isNotBlank() },
            summaryText = summary.takeIf {
                it.isNotBlank() && !FlashNoteSummaryState.isPlaceholder(it)
            },
            summaryPending = document.flashSummaryPending(),
            summaryFailed = document.flashSummaryFailed(),
            siteName = document.flashSourceParagraph()?.attachmentName?.takeIf { it.isNotBlank() },
            url = url,
            requiresClipboard = url.isNotBlank() &&
                FlashNoteSource(url = url).requiresClipboardRelay,
            pinned = note.pinnedAt != null,
            updatedAt = note.updatedAt,
            imageRelativePath = imageParagraph?.attachmentPath.orEmpty(),
            imageDisplayName = imageParagraph?.attachmentName.orEmpty()
        )
    }.sortedByDescending { it.second.updatedAt }

    val filtered = if (searchQuery.isBlank()) {
        entries
    } else {
        entries.filter { (_, entry) ->
            entry.title.contains(searchQuery, ignoreCase = true) ||
                entry.previewText?.contains(searchQuery, ignoreCase = true) == true ||
                entry.summaryText?.contains(searchQuery, ignoreCase = true) == true ||
                entry.siteName?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    return FlashNoteStream(
        entries = filtered.map { it.second },
        idByKey = filtered.mapNotNull { (key, entry) ->
            entry.key.removePrefix("note:").toLongOrNull()?.let { key to it }
        }.toMap(),
        byKey = filtered.associate { it }
    )
}

private const val TEXT_QUICK_MEMO_ID_PREFIX = "TEXT_QUICK_MEMO_"

@Composable
fun QuickMemoPage(
    viewModel: MainViewModel,
    searchQuery: String = "",
    uiSize: Int = 2,
    extraBottomPadding: Dp = 0.dp,
    onOpenDetail: (Long) -> Unit = {},
    onPendingDeleteChange: (QuickMemoEntity?) -> Unit = {},
    onRequestDeleteFlashNote: (String) -> Unit = {},
    onOpenFlashNoteDetail: (Long) -> Unit = {},
    /**
     * 当前分段。为 null 时本组件自持状态；外部传入则由调用方持有，
     * 这样底栏 FAB 能知道处于哪个分段并给出对应操作。
     */
    segment: MemoStreamSegment? = null,
    onSegmentChange: ((MemoStreamSegment) -> Unit)? = null,
    hapticEnabled: Boolean = true
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val capsuleUiState by viewModel.capsuleUiState.collectAsState()
    val mainUiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val demoModeEnabled = mainUiState.settings.developerOptionsEnabled &&
        mainUiState.settings.developerDemoModeEnabled
    val displayedMemos = remember(quickMemos, demoModeEnabled, mainUiState.today) {
        if (demoModeEnabled) DemoModeDataFactory.quickMemos(mainUiState.today) else quickMemos
    }
    var demoVoicePlaying by remember(demoModeEnabled) { mutableStateOf(true) }
    val displayedPlayback = if (demoModeEnabled) AudioPlaybackState(DemoModeDataFactory.QUICK_MEMO_AUDIO_PATH, demoVoicePlaying) else playbackState

    // 闪记与速记共用同一个底栏 Tab，用分段切换；分段状态可由外部持有以便 FAB 读取。
    var internalSegmentKey by rememberSaveable { mutableStateOf(MemoStreamSegment.QUICK_MEMO.name) }
    val segment = segment ?: MemoStreamSegment.fromKey(internalSegmentKey)
    val setSegment: (MemoStreamSegment) -> Unit = onSegmentChange ?: { internalSegmentKey = it.name }

    val flashConnection = remember(notes, searchQuery) {
        buildFlashNoteStream(notes, searchQuery)
    }

    val state = remember(
        displayedMemos,
        suggestions,
        displayedPlayback,
        capsuleUiState,
        demoModeEnabled,
        segment,
        flashConnection
    ) {
        QuickMemoListUiState(
            memos = displayedMemos,
            suggestions = if (demoModeEnabled) emptyList() else suggestions,
            playbackState = displayedPlayback,
            pinnedMemoId = activeTextQuickMemoId(capsuleUiState),
            segment = segment,
            flashNotes = flashConnection.entries,
            flashNoteIds = flashConnection.idByKey
        )
    }

    val listContent: @Composable () -> Unit = {
        QuickMemoScreen(
            state = state,
            searchQuery = searchQuery,
            uiSize = uiSize,
            extraBottomPadding = extraBottomPadding,
            hapticEnabled = hapticEnabled,
            onAction = { action ->
                // 闪记与速记的数据层不同，动作分派前先拦掉这一组。
                when (action) {
                    is QuickMemoUiAction.SelectSegment -> {
                        setSegment(action.segment)
                        return@QuickMemoScreen
                    }

                    is QuickMemoUiAction.OpenFlashNote -> {
                        // 打开详情页：收藏的正文只有这里能看到。
                        if (!demoModeEnabled) {
                            flashConnection.idByKey[action.key]?.let { noteId ->
                                onOpenFlashNoteDetail(noteId)
                            }
                        }
                        return@QuickMemoScreen
                    }

                    is QuickMemoUiAction.OpenFlashNoteSource -> {
                        if (!demoModeEnabled) {
                            val entry = flashConnection.byKey[action.key]
                            val url = entry?.url.orEmpty()
                            if (url.isNotBlank()) {
                                FlashNoteNavigator.open(
                                    context = context,
                                    url = url,
                                    requiresClipboardRelay = entry?.requiresClipboard == true
                                )
                            } else if (!entry?.imageRelativePath.isNullOrBlank()) {
                                val paragraph = NoteParagraph(
                                    type = NoteParagraphType.IMAGE,
                                    attachmentPath = entry?.imageRelativePath.orEmpty(),
                                    attachmentName = entry?.imageDisplayName.orEmpty()
                                )
                                NoteAttachmentStore.openAttachmentIntent(context, paragraph)
                                    ?.let(context::startActivity)
                            }
                        }
                        return@QuickMemoScreen
                    }

                    is QuickMemoUiAction.RequestDeleteFlashNote -> {
                        // 交给上层弹确认框，与速记删除走同一套交互。
                        onRequestDeleteFlashNote(action.key)
                        return@QuickMemoScreen
                    }

                    is QuickMemoUiAction.ConfirmDeleteFlashNote -> {
                        if (!demoModeEnabled) {
                            flashConnection.idByKey[action.key]?.let { noteId ->
                                viewModel.deleteNote(noteId)
                            }
                        }
                        return@QuickMemoScreen
                    }

                    else -> Unit
                }
                if (demoModeEnabled && action is QuickMemoUiAction.ToggleAudio) {
                    demoVoicePlaying = !demoVoicePlaying
                    return@QuickMemoScreen
                }
                if (demoModeEnabled && action !is QuickMemoUiAction.OpenDetail) return@QuickMemoScreen
                handleQuickMemoAction(
                    action = action,
                    viewModel = viewModel,
                    context = context,
                    onOpenDetail = { memoId -> onOpenDetail(memoId) },
                    onPendingDeleteChange = onPendingDeleteChange
                )
            }
        )
    }

    listContent()
}

@Composable
fun QuickMemoDetailPage(
    memoId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    uiSize: Int = 2,
    hapticEnabled: Boolean = true,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT,
    embedded: Boolean = false,
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val reminders by viewModel.quickMemoReminders.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val capsuleUiState by viewModel.capsuleUiState.collectAsState()
    val mainUiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val demoModeEnabled = mainUiState.settings.developerOptionsEnabled &&
        mainUiState.settings.developerDemoModeEnabled
    val displayedMemos = remember(quickMemos, demoModeEnabled, mainUiState.today) {
        if (demoModeEnabled) DemoModeDataFactory.quickMemos(mainUiState.today) else quickMemos
    }
    var demoVoicePlaying by remember(demoModeEnabled) { mutableStateOf(true) }
    val displayedPlayback = if (demoModeEnabled) AudioPlaybackState(DemoModeDataFactory.QUICK_MEMO_AUDIO_PATH, demoVoicePlaying) else playbackState
    val state = remember(memoId, displayedMemos, reminders, suggestions, displayedPlayback, capsuleUiState, demoModeEnabled) {
        val memo = displayedMemos.firstOrNull { it.id == memoId }
        QuickMemoDetailUiState(
            memo = memo,
            reminders = if (demoModeEnabled) emptyList() else reminders.filter { it.quickMemoId == memoId },
            suggestions = if (demoModeEnabled) emptyList() else suggestions.filter {
                it.quickMemoId == memoId &&
                    (it.status == QuickMemoSuggestionStatus.PENDING ||
                        it.status == QuickMemoSuggestionStatus.CREATED)
            },
            playbackState = displayedPlayback,
            isPinned = memo?.id?.let { it == activeTextQuickMemoId(capsuleUiState) } == true
        )
    }

    DisposableEffect(memoId, demoModeEnabled) {
        onDispose {
            val latest = viewModel.quickMemos.value.firstOrNull { it.id == memoId }
            if (!demoModeEnabled && latest != null && isBlankTextQuickMemo(latest)) {
                viewModel.deleteQuickMemo(memoId)
            }
        }
    }

    QuickMemoDetailScreen(
        state = state,
        onBack = onBack,
        uiSize = uiSize,
        hapticEnabled = hapticEnabled,
        backgroundMode = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        cardAlphaPercent = cardAlphaPercent,
        embedded = embedded,
        autoStopDurationMs = QuickMemoAutoStopPolicy.durationMillis(mainUiState.settings),
        onAction = { action ->
            if (demoModeEnabled && action is QuickMemoUiAction.ToggleAudio) {
                demoVoicePlaying = !demoVoicePlaying
                return@QuickMemoDetailScreen
            }
            if (demoModeEnabled) {
                Toast.makeText(context, "演示模式不会保存修改，请关闭演示模式后操作真实记录", Toast.LENGTH_SHORT).show()
                return@QuickMemoDetailScreen
            }
            handleQuickMemoAction(
                action = action,
                viewModel = viewModel,
                context = context,
                onOpenDetail = {},
                onPendingDeleteChange = {}
            )
        }
    )
}

private fun handleQuickMemoAction(
    action: QuickMemoUiAction,
    viewModel: MainViewModel,
    context: Context,
    onOpenDetail: (Long) -> Unit,
    onPendingDeleteChange: (QuickMemoEntity?) -> Unit
) {
    when (action) {
        // 分段与闪记动作在 QuickMemoPage 的 onAction 里已先行处理，
        // 走到这里说明是演示模式或误入，此处按无操作处理避免误改速记。
        is QuickMemoUiAction.SelectSegment,
        is QuickMemoUiAction.OpenFlashNote,
        is QuickMemoUiAction.OpenFlashNoteSource,
        is QuickMemoUiAction.RequestDeleteFlashNote,
        is QuickMemoUiAction.ConfirmDeleteFlashNote -> Unit
        is QuickMemoUiAction.OpenDetail -> onOpenDetail(action.memoId)
        is QuickMemoUiAction.RequestDelete -> onPendingDeleteChange(action.memo)
        is QuickMemoUiAction.Delete -> viewModel.deleteQuickMemo(action.memoId)
        is QuickMemoUiAction.ToggleTodoCompletion -> viewModel.toggleQuickMemoTodoCompletion(action.memoId)
        is QuickMemoUiAction.MarkTodo -> viewModel.markQuickMemoTodo(action.memoId)
        is QuickMemoUiAction.RemoveTodo -> viewModel.removeQuickMemoTodo(action.memoId)
        is QuickMemoUiAction.ToggleAudio -> viewModel.toggleAudioPlayback(action.audioPath)
        is QuickMemoUiAction.UpdateBody -> viewModel.updateQuickMemoBody(action.memoId, action.body)
        is QuickMemoUiAction.SaveReminder ->
            viewModel.saveQuickMemoReminder(
                action.memoId,
                action.reminderId,
                action.reminderAt,
                action.reminderRRule
            ) { result ->
                result.onFailure { error ->
                    Toast.makeText(context, error.message ?: "提醒设置失败", Toast.LENGTH_SHORT).show()
                }
            }
        is QuickMemoUiAction.DeleteReminder ->
            viewModel.deleteQuickMemoReminder(action.reminderId) { result ->
                result.onFailure { error ->
                    Toast.makeText(context, error.message ?: "提醒删除失败", Toast.LENGTH_SHORT).show()
                }
            }
        is QuickMemoUiAction.AttachImage ->
            viewModel.attachImageToQuickMemo(action.memoId, action.imagePath, action.onResult)
        is QuickMemoUiAction.RemoveImage ->
            viewModel.removeImageFromQuickMemo(action.memoId, action.onResult)
        is QuickMemoUiAction.AttachVoice ->
            viewModel.attachVoiceToQuickMemo(
                action.memoId,
                action.audioPath,
                action.durationMs,
                action.onResult
            )
        is QuickMemoUiAction.RetryTranscription -> viewModel.retryQuickMemoTranscription(action.memoId)
        is QuickMemoUiAction.CreateSuggestionEvent ->
            viewModel.createEventFromQuickMemoSuggestion(action.suggestionId)
        is QuickMemoUiAction.TogglePinned -> {
            if (action.isPinned) {
                viewModel.clearPinnedQuickMemo(action.memoId) { result ->
                    result
                        .onSuccess { Toast.makeText(context, "已移除挂起", Toast.LENGTH_SHORT).show() }
                        .onFailure {
                            Toast.makeText(context, it.message ?: "移除挂起失败", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                viewModel.pinQuickMemo(action.memoId) { result ->
                    result
                        .onSuccess { Toast.makeText(context, "已挂起到胶囊", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "挂起失败", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}

private fun activeTextQuickMemoId(state: CapsuleUiState): Long? {
    val active = state as? CapsuleUiState.Active ?: return null
    return active.capsules.firstOrNull { it.type == CapsuleType.TEXT_QUICK_MEMO }
        ?.id
        ?.removePrefix(TEXT_QUICK_MEMO_ID_PREFIX)
        ?.toLongOrNull()
}

private fun isBlankTextQuickMemo(memo: QuickMemoEntity): Boolean =
    memo.type == QuickMemoType.TEXT &&
        memo.bodyText.isBlank() &&
        memo.imagePath.isNullOrBlank() &&
        memo.audioPath.isNullOrBlank()
