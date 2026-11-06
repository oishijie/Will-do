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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.antgskds.calendarassistant.feature.settings.developer.application.DemoModeDataFactory

private const val TEXT_QUICK_MEMO_ID_PREFIX = "TEXT_QUICK_MEMO_"

@Composable
fun QuickMemoPage(
    viewModel: MainViewModel,
    searchQuery: String = "",
    uiSize: Int = 2,
    extraBottomPadding: Dp = 0.dp,
    onOpenDetail: (Long) -> Unit = {},
    onPendingDeleteChange: (QuickMemoEntity?) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val quickMemoFolders by viewModel.quickMemoFolders.collectAsState()
    val quickMemoBrowser by viewModel.quickMemoBrowser.collectAsState()
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

    // 文件夹筛选在 Connector 层算好再下发（对齐上游 visibleMemos）：
    // 下游只负责渲染，不必再依赖 state 里的 folderId 自己过滤。
    val visibleMemos = remember(displayedMemos, quickMemoBrowser.folderId) {
        val folderId = quickMemoBrowser.folderId
        displayedMemos.filter { memo ->
            when (folderId) {
                null -> true
                "" -> memo.folderId == null
                else -> memo.folderId == folderId
            }
        }
    }
    val visibleIds = remember(visibleMemos) { visibleMemos.mapNotNull { it.id }.toSet() }
    // 选中的条目可能因切换文件夹而移出可见集，剔除它们，避免工具栏计数与实际不符。
    LaunchedEffect(visibleIds) {
        val current = viewModel.quickMemoBrowser.value
        if (current.selectionMode && current.selectedIds.any { it !in visibleIds }) {
            viewModel.updateQuickMemoBrowser(
                current.copy(selectedIds = current.selectedIds.intersect(visibleIds))
            )
        }
    }

    // quickMemoBrowser 必须进 key：多选模式与文件夹筛选都写在这个流里，
    // 漏掉它时 remember 会一直返回旧 state，列表既不进多选也不跟着文件夹变。
    val state = remember(
        displayedMemos,
        suggestions,
        displayedPlayback,
        capsuleUiState,
        demoModeEnabled,
        quickMemoBrowser
    ) {
        QuickMemoListUiState(
            memos = visibleMemos,
            suggestions = if (demoModeEnabled) emptyList() else suggestions,
            playbackState = displayedPlayback,
            pinnedMemoId = activeTextQuickMemoId(capsuleUiState),
            folders = quickMemoFolders,
            selectedFolderId = quickMemoBrowser.folderId,
            selectionMode = quickMemoBrowser.selectionMode,
            selectedMemoIds = quickMemoBrowser.selectedIds
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
    val scope = rememberCoroutineScope()
    val exportFiles = remember(context) { com.antgskds.calendarassistant.platform.quickmemo.QuickMemoExportFiles(context) }
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
        onExport = { id ->
            val memo = displayedMemos.firstOrNull { it.id == id }
            if (memo == null || demoModeEnabled) return@QuickMemoDetailScreen
            scope.launch {
                val snapshot = com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoExportSnapshot(
                    memo = memo,
                    reminders = reminders.filter { it.quickMemoId == id },
                )
                try {
                    val prepared = withContext(Dispatchers.IO) { exportFiles.prepare(snapshot) }
                    withContext(Dispatchers.Main) { exportFiles.share(prepared) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "导出失败", Toast.LENGTH_SHORT).show()
                }
            }
        },
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
        is QuickMemoUiAction.OpenDetail -> onOpenDetail(action.memoId)
        is QuickMemoUiAction.RequestDelete -> onPendingDeleteChange(action.memo)
        is QuickMemoUiAction.Delete -> viewModel.archiveQuickMemo(action.memoId)
        is QuickMemoUiAction.ToggleTodoCompletion -> viewModel.toggleQuickMemoTodoCompletion(action.memoId)
        is QuickMemoUiAction.MarkTodo -> viewModel.markQuickMemoTodo(action.memoId)
        is QuickMemoUiAction.RemoveTodo -> viewModel.removeQuickMemoTodo(action.memoId)
        is QuickMemoUiAction.ToggleAudio -> viewModel.toggleAudioPlayback(action.audioPath)
        is QuickMemoUiAction.UpdateBody -> viewModel.updateQuickMemoBody(action.memoId, action.body)
        is QuickMemoUiAction.UpdateTitle -> viewModel.updateQuickMemoTitle(action.memoId, action.title)
        is QuickMemoUiAction.ExitSelection -> viewModel.resetQuickMemoBrowser()
        is QuickMemoUiAction.SelectFolder ->
            viewModel.updateQuickMemoBrowser(
                (viewModel.quickMemoBrowser.value).copy(
                    folderId = action.folderId,
                    selectionMode = false,
                    selectedIds = emptySet()
                )
            )
        is QuickMemoUiAction.EnterSelectionMode ->
            viewModel.updateQuickMemoBrowser(
                viewModel.quickMemoBrowser.value.copy(
                    selectionMode = true,
                    selectedIds = setOf(action.memoId)
                )
            )
        is QuickMemoUiAction.ToggleMemoSelection -> {
            val current = viewModel.quickMemoBrowser.value
            val selected = current.selectedIds
            val next = if (action.memoId in selected) selected - action.memoId else selected + action.memoId
            // 保留多选模式（对齐上游）：取消勾选最后一项时工具栏仍在，
            // 用户可以继续勾选或点全选，不会被意外踢出多选。
            viewModel.updateQuickMemoBrowser(
                current.copy(selectionMode = true, selectedIds = next)
            )
        }
        is QuickMemoUiAction.SelectAllMemos ->
            viewModel.updateQuickMemoBrowser(
                viewModel.quickMemoBrowser.value.copy(selectedIds = action.memoIds.toSet())
            )
        is QuickMemoUiAction.MoveSelectedMemos -> {
            val ids = viewModel.quickMemoBrowser.value.selectedIds.toList()
            viewModel.moveQuickMemos(ids, action.folderId) { result ->
                result.onFailure { error ->
                    Toast.makeText(context, error.message ?: "移动失败", Toast.LENGTH_SHORT).show()
                }
            }
            viewModel.resetQuickMemoBrowser()
        }
        is QuickMemoUiAction.MoveMemo -> {
            viewModel.moveQuickMemos(listOf(action.memoId), action.folderId) { result ->
                result.onFailure { error ->
                    Toast.makeText(context, error.message ?: "移动失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
        is QuickMemoUiAction.DeleteSelectedMemos -> {
            val ids = viewModel.quickMemoBrowser.value.selectedIds
            viewModel.archiveQuickMemos(ids)
            viewModel.resetQuickMemoBrowser()
        }
        is QuickMemoUiAction.CreateFolder -> viewModel.createQuickMemoFolder(action.name)
        is QuickMemoUiAction.RenameFolder -> viewModel.renameQuickMemoFolder(action.folderId, action.name)
        is QuickMemoUiAction.DeleteFolder -> viewModel.deleteQuickMemoFolder(action.folderId)
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
