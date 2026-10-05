package com.antgskds.calendarassistant.feature.home.ui.connector

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.MemoStreamSegment
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.MemoInputAction
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.MemoInputBar
import com.antgskds.calendarassistant.feature.home.ui.render.material.ScheduleVoiceDialog
import com.antgskds.calendarassistant.feature.schedule.presentation.RecurringEventUtils
import com.antgskds.calendarassistant.shared.ui.material.component.LocalDetailReadOnly
import com.antgskds.calendarassistant.shared.ui.material.component.LocalDetailDirtyChanged
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.recognition.ingest.clipboard.ClipboardCodePrompt
import com.antgskds.calendarassistant.feature.schedule.domain.model.RecurringMode
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.RecognitionFailureMessageMapper
import com.antgskds.calendarassistant.feature.recognition.application.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.feature.recognition.application.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.feature.recognition.ui.feedback.RecognitionFeedbackSource
import com.antgskds.calendarassistant.shared.event.DomainEventType
import com.antgskds.calendarassistant.shared.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.shared.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.shared.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.settings.developer.application.DemoModeDataFactory
import kotlinx.coroutines.launch
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.*
import com.antgskds.calendarassistant.feature.home.domain.fixedHomeBottomItems
import com.antgskds.calendarassistant.feature.home.domain.swipeableHomeItems
import androidx.activity.compose.BackHandler
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBar
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarToastGap
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarVisualHeight
import com.antgskds.calendarassistant.feature.home.ui.contract.HomeShellUiState
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.feature.settings.shell.ui.render.material.component.SettingsTabPage
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalToast
import com.antgskds.calendarassistant.feature.home.ui.connector.HomePageRoute
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.dialog.*
import com.antgskds.calendarassistant.shared.ui.material.dialog.*
import com.antgskds.calendarassistant.feature.schedule.ui.render.AddEventDialog
import com.antgskds.calendarassistant.feature.home.ui.render.HomeScreenContent
import com.antgskds.calendarassistant.feature.home.ui.render.HomeActionDialog
import com.antgskds.calendarassistant.feature.home.ui.render.editionHomeEntries
import com.antgskds.calendarassistant.shared.ui.material.component.AppModalBottomSheet
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import java.time.LocalDate
import kotlin.math.roundToInt
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipboardManager
import android.content.Context
import com.antgskds.calendarassistant.feature.note.data.attachment.NoteAttachmentStore
import com.antgskds.calendarassistant.feature.quickmemo.data.audio.QuickMemoAudioRecorder
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.TranscriptionResult
import com.antgskds.calendarassistant.shared.util.ImageImportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RecurringEditSession(
    val parentEventId: Long?,
    val occurrenceTs: Long,
    val nextOccurrenceText: String?,
    val editHint: String
)

private data class RecurringEditCommitSession(
    val parentId: Long,
    val occurrenceTs: Long,
    val patch: com.antgskds.calendarassistant.feature.schedule.application.model.EventPatch,
    val attachments: List<EventAttachment> = emptyList()
)

/** 编辑上下文：记录当前编辑弹窗要操作的目标类型 */
private sealed class EditContext {
    object NewEvent : EditContext()
    data class SingleEvent(val eventId: Long) : EditContext()
    data class RecurringOccurrence(val parentId: Long, val occurrenceTs: Long) : EditContext()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    pickupTimestamp: Long = 0L, // 【修改 1】参数改为 Long
    openEventId: Long? = null,
    openEventRequestId: Long = 0L,
    selectedPageKey: String = HomeEntryKey.TODAY,
    clipboardPrompt: ClipboardCodePrompt? = null,
    onConfirmClipboardPrompt: () -> Unit = {},
    onDismissClipboardPrompt: () -> Unit = {},
    onSelectedPageKeyChange: (String) -> Unit = {},
    onOpenWeatherDetail: () -> Unit = {},
    onOpenQuickMemoDetail: (Long) -> Unit = {},
    onOpenFlashNoteDetail: (Long) -> Unit = {},
    onNavigateToSettings: (SettingsDestination) -> Unit
) {
    val app = LocalContext.current.applicationContext as App
    // 从 settings 读取主题状态
    val settings by settingsViewModel.settings.collectAsState()
    val uiState by mainViewModel.uiState.collectAsState()
    val appUpdateUiState by mainViewModel.appUpdateUiState.collectAsState()
    val quickMemos by mainViewModel.quickMemos.collectAsState()
    val demoModeEnabled = uiState.settings.developerOptionsEnabled &&
        uiState.settings.developerDemoModeEnabled
    val quickMemoCount = if (demoModeEnabled) {
        DemoModeDataFactory.quickMemos(uiState.today).size
    } else {
        quickMemos.size
    }

    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(app) {
        app.domainEventBus
            .eventsOfType<RecognitionFailedEvent>(DomainEventType.RECOGNITION_FAILED)
            .collect { event ->
                val payload = event.payload
                val isHomeSource =
                    payload.sourceType == RecognitionFeedbackSource.HOME_SOURCE_TYPE &&
                        payload.sourceId == RecognitionFeedbackSource.HOME_SOURCE_ID
                if (!isHomeSource) return@collect

                val message = RecognitionFailureMessageMapper.userMessage(payload)
                showToast(message, ToastType.ERROR)
            }
    }

    LaunchedEffect(app) {
        app.domainEventBus
            .eventsOfType<IngestSucceededEvent>(DomainEventType.INGEST_SUCCEEDED)
            .collect { event ->
                val payload = event.payload
                val isHomeSource =
                    payload.sourceType == RecognitionFeedbackSource.HOME_SOURCE_TYPE &&
                        payload.sourceId == RecognitionFeedbackSource.HOME_SOURCE_ID
                if (!isHomeSource) return@collect

                val message = when {
                    payload.createdCount <= 0 -> "已处理，无新增"
                    payload.createdCount == 1 -> "已添加 1 个事件"
                    else -> "已添加 ${payload.createdCount} 个事件"
                }
                showToast(message, ToastType.SUCCESS)
            }
    }

    LaunchedEffect(app) {
        app.domainEventBus
            .eventsOfType<IngestFailedEvent>(DomainEventType.INGEST_FAILED)
            .collect { event ->
                val payload = event.payload
                val isHomeSource =
                    payload.sourceType == RecognitionFeedbackSource.HOME_SOURCE_TYPE &&
                        payload.sourceId == RecognitionFeedbackSource.HOME_SOURCE_ID
                if (!isHomeSource) return@collect

                showToast(payload.message.ifBlank { "保存失败" }, ToastType.ERROR)
            }
    }

    // 状态管理
    var isScheduleExpanded by remember { mutableStateOf(false) } // 首页下滑展开状态
    var scheduleProgress by remember { mutableFloatStateOf(0f) }
    var scheduleOffsetPx by remember { mutableFloatStateOf(0f) }
    var isActionExpanded by remember { mutableStateOf(false) }
    var searchRequestId by remember { mutableIntStateOf(0) }
    var imageRequestId by remember { mutableIntStateOf(0) }

    // 常驻底栏固定入口：今日日程 / 全部日程 / 随口记 / 设置
    val homeBottomItems = remember { editionHomeEntries(fixedHomeBottomItems) }
    val pagerPageOrder = remember { editionHomeEntries(swipeableHomeItems) }
    val effectiveSelectedPageKey = if (selectedPageKey in homeBottomItems) selectedPageKey else HomeEntryKey.TODAY

    LaunchedEffect(homeBottomItems, selectedPageKey) {
        if (selectedPageKey !in homeBottomItems) {
            onSelectedPageKeyChange(HomeEntryKey.TODAY)
        }
    }

    // 弹窗状态管理
    var detailDirty by remember { mutableStateOf(false) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var editDraft by remember { mutableStateOf<com.antgskds.calendarassistant.feature.schedule.application.model.EditDraft?>(null) }
    var editContext by remember { mutableStateOf<EditContext?>(null) }
    var eventToEdit by remember { mutableStateOf<Event?>(null) }
    var draftEventToAdd by remember { mutableStateOf<Event?>(null) }
    var recurringEditSession by remember { mutableStateOf<RecurringEditSession?>(null) }
    var recurringEditCommitSession by remember { mutableStateOf<RecurringEditCommitSession?>(null) }
    var scheduleItemToDelete by remember { mutableStateOf<ScheduleDisplayItem?>(null) }
    var selectedQuickMemoAction by remember { mutableStateOf<QuickMemoEntity?>(null) }
    var selectedFlashNoteKey by remember { mutableStateOf<String?>(null) }
    // ===== 键盘上方的记事输入栏 =====
    // 目标区：null = 收起。点「添加随口记」→ QUICK_MEMO，点「添加随手记」→ FLASH_NOTE。
    var memoInputTarget by remember { mutableStateOf<MemoStreamSegment?>(null) }
    var memoInputText by remember { mutableStateOf("") }
    // 随口记那支录音器：是否在录、是否在转写。
    var isMemoRecording by remember { mutableStateOf(false) }
    var isMemoVoiceTranscribing by remember { mutableStateOf(false) }
    // 日程页「语音识别」：录音弹窗开关 + 是否正在转写落库。
    var isScheduleVoiceDialogOpen by remember { mutableStateOf(false) }
    var isScheduleVoiceSaving by remember { mutableStateOf(false) }
    var memoSegment by rememberSaveable { mutableStateOf(MemoStreamSegment.ALL) }
    val memoRecorder = remember(app) { QuickMemoAudioRecorder(app) }

    // ===== 输入栏的语音：录音 → 本地转写 → 文字填进输入框 =====
    // 不直接入库：转写结果先落进输入框，用户可以改，改完再按发送。

    fun startMemoVoice() {
        if (isMemoRecording || isMemoVoiceTranscribing) return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { memoRecorder.start() } }
                .onSuccess {
                    isMemoRecording = true
                    showToast("正在录音，再点一次结束", ToastType.INFO)
                }
                .onFailure { showToast(it.message ?: "录音失败", ToastType.ERROR) }
        }
    }

    fun stopMemoVoiceToText() {
        if (!isMemoRecording || isMemoVoiceTranscribing) return
        scope.launch {
            isMemoVoiceTranscribing = true
            val recorded = runCatching { withContext(Dispatchers.IO) { memoRecorder.stop() } }.getOrNull()
            isMemoRecording = false
            if (recorded == null || recorded.durationMs < QuickMemoAudioRecorder.MIN_RECORDING_MS) {
                recorded?.path?.let { path -> runCatching { java.io.File(path).delete() } }
                isMemoVoiceTranscribing = false
                showToast("录音太短", ToastType.ERROR)
                return@launch
            }
            try {
                when (val result = app.speechTranscriber.transcribe(recorded.path)) {
                    is TranscriptionResult.Success -> {
                        val text = result.text.trim()
                        if (text.isNotEmpty()) {
                            memoInputText = if (memoInputText.isBlank()) text
                            else memoInputText.trimEnd() + " " + text
                        }
                    }

                    is TranscriptionResult.Failure ->
                        showToast(result.message.ifBlank { "语音识别失败" }, ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast(e.message ?: "语音识别失败", ToastType.ERROR)
            } finally {
                runCatching { java.io.File(recorded.path).delete() }
                isMemoVoiceTranscribing = false
            }
        }
    }

    /** 收起输入栏。正在录就直接丢弃（用户主动关 = 取消），避免录音器悬空。 */
    fun closeMemoInput() {
        if (isMemoRecording) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { memoRecorder.stop() } }
                    .getOrNull()?.path?.let { path -> runCatching { java.io.File(path).delete() } }
                isMemoRecording = false
            }
        }
        memoInputTarget = null
        memoInputText = ""
    }

    /** 发送输入框里的文字，存进当前目标区。 */
    fun submitMemoInput() {
        val target = memoInputTarget ?: return
        val text = memoInputText.trim()
        if (text.isEmpty()) return
        if (target == MemoStreamSegment.FLASH_NOTE) {
            mainViewModel.createTextFlashNote(text) { outcome ->
                outcome
                    .onSuccess { showToast("已保存随手记", ToastType.SUCCESS) }
                    .onFailure { showToast(it.message ?: "保存失败", ToastType.ERROR) }
            }
        } else {
            mainViewModel.createTextQuickMemo(text)
            showToast("已保存随口记", ToastType.SUCCESS)
        }
        closeMemoInput()
    }

    // ===== 日程页「语音识别」：录音 → 本地转写 → 交给识别链路建日程 =====
    // 复用随口记那支录音器（同一时刻只会有一处录音）；转写走 App 里共享的 ASR 实例，
    // 不再为这条链路单独加载一份模型。
    fun stopScheduleVoice() {
        if (isScheduleVoiceSaving) return
        scope.launch {
            isScheduleVoiceSaving = true
            val recorded = runCatching { withContext(Dispatchers.IO) { memoRecorder.stop() } }.getOrNull()
            if (recorded == null || recorded.durationMs < QuickMemoAudioRecorder.MIN_RECORDING_MS) {
                recorded?.path?.let { path -> runCatching { java.io.File(path).delete() } }
                isScheduleVoiceSaving = false
                isScheduleVoiceDialogOpen = false
                showToast("录音太短", ToastType.ERROR)
                return@launch
            }
            try {
                when (val transcribed = app.speechTranscriber.transcribe(recorded.path)) {
                    is TranscriptionResult.Failure ->
                        showToast(transcribed.message, ToastType.ERROR)

                    is TranscriptionResult.Success -> {
                        if (!settings.isRecognitionConfigReady()) {
                            showToast(settings.recognitionConfigMissingMessage(), ToastType.ERROR)
                        } else {
                            when (
                                val analysis = app.recognitionCenter.parseUserText(
                                    text = transcribed.text,
                                    settings = settings,
                                    context = app,
                                    sourceType = "voice",
                                    sourceId = "home_schedule_voice",
                                    ingestRequested = true
                                )
                            ) {
                                is AnalysisResult.Success ->
                                    showToast(analysis.feedback(), ToastType.SUCCESS)

                                is AnalysisResult.Empty ->
                                    showToast(analysis.message, ToastType.INFO)

                                is AnalysisResult.Failure ->
                                    showToast(analysis.failure.fullMessage(), ToastType.ERROR)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                showToast(e.message ?: "语音识别失败", ToastType.ERROR)
            } finally {
                runCatching { java.io.File(recorded.path).delete() }
                isScheduleVoiceSaving = false
                isScheduleVoiceDialogOpen = false
            }
        }
    }

    /** 速记选图：复制进 quick_memos/images 后直接存成图片速记。 */
    val memoImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        ImageImportUtils.createQuickMemoImageFile(app).also { file ->
                            check(ImageImportUtils.copyUriToFile(app, uri, file)) { "图片读取失败" }
                        }
                    }
                }
                    .onSuccess { file ->
                        mainViewModel.createImageQuickMemo(file.absolutePath) {
                            showToast("已保存速记", ToastType.SUCCESS)
                        }
                    }
                    .onFailure { showToast(it.message ?: "保存图片失败", ToastType.ERROR) }
            }
        }
    }

    /** 闪记选图：落进 notes 附件目录。 */
    val noteImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { NoteAttachmentStore.copyFromUri(app, uri) } }
                    .onSuccess { stored ->
                        mainViewModel.createImageFlashNote(
                            attachmentPath = stored.relativePath,
                            attachmentName = stored.displayName,
                            attachmentMime = stored.mimeType
                        )
                        showToast("已收藏图片", ToastType.SUCCESS)
                    }
                    .onFailure { showToast(it.message ?: "保存图片失败", ToastType.ERROR) }
            }
        }
    }

    /** 打开输入栏：目标区决定这条内容存进随口记还是随手记。 */
    fun openMemoInput(target: MemoStreamSegment) {
        memoInputText = ""
        memoInputTarget = target
    }

    /**
     * 「实况通知」：把输入框这条存进目标区，同时挂到状态栏**常驻**
     * （不自动消失，用户点通知上的「移除」才收）。两区都走这条，只是落库位置不同。
     *
     * 注意：局部函数必须先定义后使用，不要再挪到 [handleMemoInputAction] 之后。
     */
    fun saveAndPinCapsule(target: MemoStreamSegment) {
        val text = memoInputText.trim()
        if (text.isEmpty()) {
            showToast("先写点什么再上实况通知", ToastType.INFO)
            return
        }
        if (target == MemoStreamSegment.FLASH_NOTE) {
            mainViewModel.createTextFlashNote(text) { created ->
                created
                    .onSuccess { noteId ->
                        mainViewModel.pinFlashNote(noteId) { pinned ->
                            pinned
                                .onSuccess { showToast("已保存随手记并挂上实况通知", ToastType.SUCCESS) }
                                .onFailure { showToast(it.message ?: "挂实况通知失败", ToastType.ERROR) }
                        }
                    }
                    .onFailure { showToast(it.message ?: "保存失败", ToastType.ERROR) }
            }
        } else {
            mainViewModel.createTextQuickMemo(text) { memoId ->
                mainViewModel.pinQuickMemo(memoId, pinned = true) { pinned ->
                    pinned
                        .onSuccess { showToast("已保存随口记并挂上实况通知", ToastType.SUCCESS) }
                        .onFailure { showToast(it.message ?: "挂实况通知失败", ToastType.ERROR) }
                }
            }
        }
        closeMemoInput()
    }

    /**
     * 输入栏底部那排按钮：图片 / 链接 / 实况通知 / 语音。
     * 两区按钮完全一致（原先随口记缺的「链接」已统一补齐）。
     */
    fun handleMemoInputAction(id: String) {
        val target = memoInputTarget ?: return
        when (id) {
            "image" -> if (target == MemoStreamSegment.FLASH_NOTE) {
                noteImagePicker.launch("image/*")
            } else {
                memoImagePicker.launch("image/*")
            }

            "link" -> {
                val clip = readClipboardText(app)
                if (clip.isNullOrBlank()) {
                    // 部分系统限制读剪贴板，不要卡住用户——输入框就在上面，让他直接粘。
                    showToast("读不到剪贴板内容，直接粘进输入框吧", ToastType.INFO)
                } else {
                    mainViewModel.createFlashNoteFromUrl(clip)
                    showToast("已保存链接", ToastType.SUCCESS)
                    closeMemoInput()
                }
            }

            "capsule" -> saveAndPinCapsule(target)

            "voice" -> if (isMemoRecording) stopMemoVoiceToText() else startMemoVoice()
        }
    }

    var showClearQuickMemosConfirm by remember { mutableStateOf(false) }
    var dialogAttachments by remember { mutableStateOf<List<EventAttachment>>(emptyList()) }
    var currentDialogSessionId by remember { mutableStateOf(0L) }
    var pendingAddDialog by remember { mutableStateOf(false) }
    var addDialogRequestId by remember { mutableIntStateOf(0) }
    var lastOpenEventRequestId by remember { mutableLongStateOf(0L) }
    val dialogDelayMs = 240L

    LaunchedEffect(pendingAddDialog) {
        if (!pendingAddDialog) return@LaunchedEffect
        kotlinx.coroutines.delay(dialogDelayMs)
        if (!showAddEventDialog && eventToEdit == null) {
            showAddEventDialog = true
        }
        pendingAddDialog = false
    }

    LaunchedEffect(quickMemoCount) {
        if (quickMemoCount <= 0) showClearQuickMemosConfirm = false
    }

    fun beginEdit(event: Event) {
        pendingAddDialog = false
        draftEventToAdd = null
        if (event.isRecurring) {
            val nextInstance = mainViewModel.findNextRecurringInstance(event)
            val previewEvent = if (nextInstance != null) {
                nextInstance
            } else if (!event.idString.isNullOrBlank()) {
                event.copy(
                    id = null,
                    rrule = "",
                    parentId = event.id ?: 0L
                )
            } else {
                null
            }
            val instanceKey = previewEvent?.idString
            if (previewEvent == null || instanceKey.isNullOrBlank()) {
                eventToEdit = null
                recurringEditSession = null
                showToast("未找到可编辑的下次实例")
            } else {
                eventToEdit = previewEvent
                recurringEditSession = RecurringEditSession(
                    parentEventId = event.id,
                    occurrenceTs = previewEvent.startTS,
                    nextOccurrenceText = RecurringEventUtils.formatMillis(event.startMillis),
                    editHint = "本次修改将应用到下次实例，并脱离重复系列"
                )
            }
        } else if (event.parentId != 0L) {
            // 虚拟展开实例或已有的子实例（exception instance）
            val parentEvent = mainViewModel.findRecurringParent(event)
            if (parentEvent == null) {
                eventToEdit = null
                recurringEditSession = null
                showToast("未找到对应的重复系列信息")
            } else {
                // 用父事件的信息构造预览，但保留当前实例的时间
                eventToEdit = event.copy(
                    id = null,  // 清掉负数合成 id，确保不会被当成真实记录更新
                    importId = "",
                    rrule = "",
                    exdates = emptyList()
                )
                recurringEditSession = RecurringEditSession(
                    parentEventId = parentEvent.id,
                    occurrenceTs = event.startTS,
                    nextOccurrenceText = RecurringEventUtils.formatMillis(parentEvent.startMillis),
                    editHint = "本次修改将应用到当前实例，并脱离重复系列"
                )
            }
        } else {
            eventToEdit = event
            recurringEditSession = null
        }
    }

    LaunchedEffect(openEventRequestId, openEventId, uiState.rawEvents) {
        val eventId = openEventId ?: return@LaunchedEffect
        if (openEventRequestId == 0L || openEventRequestId == lastOpenEventRequestId) return@LaunchedEffect
        val event = uiState.rawEvents.firstOrNull { it.id == eventId } ?: return@LaunchedEffect
        lastOpenEventRequestId = openEventRequestId
        if (event.codeQrPayload.isNotBlank() && app.floatingCenter.startPickupQrCard(eventId)) {
            return@LaunchedEffect
        }
        onSelectedPageKeyChange(HomeEntryKey.ALL)
        beginEdit(event)
    }

    /**
     * 新版编辑入口：从 ScheduleDisplayItem 路由到正确的编辑流程。
     * Single → 加载真实 Event 走旧流程
     * RecurringOccurrence → 加载母事件，设置 recurringEditSession
     */
    fun beginEditItem(item: ScheduleDisplayItem) {
        if (detailDirty) { showToast("请先保存或返回，处理当前未保存的内容"); return }
        showAddEventDialog = false
        editDraft = null
        dialogAttachments = emptyList()
        currentDialogSessionId = System.nanoTime()
        if (demoModeEnabled) {
            editDraft = com.antgskds.calendarassistant.feature.schedule.application.model.EditDraft(
                title = item.title, startDate = item.startDate, startTime = item.startLocalTime,
                endDate = item.endDate, endTime = item.endLocalTime, location = item.location,
                description = item.description, tag = item.tag, color = item.color,
            )
            showAddEventDialog = true
            return
        }
        pendingAddDialog = false
        draftEventToAdd = null
        recurringEditCommitSession = null

        when (val target = item.action) {
            is ScheduleDisplayItem.ActionTarget.Single -> {
                val event = mainViewModel.getEventById(target.eventId) ?: return
                // 单次事件或已有子实例
                val draft = mainViewModel.prepareEditSingle(target.eventId)
                if (draft != null) {
                    editDraft = draft
                    dialogAttachments = emptyList()
                    val sessionId = System.nanoTime()
                    currentDialogSessionId = sessionId
                    scope.launch {
                        runCatching { mainViewModel.getEventAttachments(target.eventId) }
                            .onSuccess { if (currentDialogSessionId == sessionId) dialogAttachments = it }
                            .onFailure { showToast("附件加载失败: ${it.message}", ToastType.ERROR) }
                    }
                    editContext = if (event.parentId != 0L) {
                        // 已存在的异常子实例 → 按单次事件更新
                        EditContext.SingleEvent(target.eventId)
                    } else {
                        EditContext.SingleEvent(target.eventId)
                    }
                    showAddEventDialog = true
                }
            }
            is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                val draft = mainViewModel.prepareEditRecurringOccurrence(target.parentId, target.occurrenceTs)
                if (draft != null) {
                    editDraft = draft
                    dialogAttachments = emptyList()
                    val sessionId = System.nanoTime()
                    currentDialogSessionId = sessionId
                    scope.launch {
                        runCatching { mainViewModel.getEventAttachments(target.parentId) }
                            .onSuccess { if (currentDialogSessionId == sessionId) dialogAttachments = it }
                            .onFailure { showToast("附件加载失败: ${it.message}", ToastType.ERROR) }
                    }
                    editContext = EditContext.RecurringOccurrence(target.parentId, target.occurrenceTs)
                    showAddEventDialog = true
                }
            }
        }
    }

    fun requestDeleteItem(item: ScheduleDisplayItem) {
        mainViewModel.onRevealItem(null)
        scheduleItemToDelete = item
    }

    fun openAddEventDialog() {
        isActionExpanded = false
        addDialogRequestId += 1
        currentDialogSessionId = System.nanoTime()
        recurringEditSession = null
        recurringEditCommitSession = null
        draftEventToAdd = null
        eventToEdit = null
        editDraft = null
        dialogAttachments = emptyList()
        editContext = EditContext.NewEvent
        showAddEventDialog = false
        pendingAddDialog = true
    }

    fun openPrimaryCreateDialog() {
        if (demoModeEnabled) { showToast("演示模式仅供查看，请关闭演示模式后新建"); return }
        if (detailDirty) { showToast("请先保存或返回，处理当前未保存的内容"); return }
        isActionExpanded = false
        if (effectiveSelectedPageKey == HomeEntryKey.NOTE) {
            mainViewModel.createTextQuickMemo("") { id -> onOpenQuickMemoDetail(id) }
        } else {
            openAddEventDialog()
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 底栏常驻（一级导航栏已移除），悬浮卡片与 Toast 始终让开底栏高度。
    val cardFloatingBarOffset =
        IntegratedFloatingBarHeight +
            IntegratedFloatingBarBottomSpacing +
            bottomInset
    val toastFloatingBarOffset =
        IntegratedFloatingBarVisualHeight +
            IntegratedFloatingBarToastGap +
            IntegratedFloatingBarBottomSpacing +
            bottomInset
    val hasAppBackground = settings.appBackgroundImagePath.isNotBlank()

    val editorContent: @Composable () -> Unit = {
    // 1. 普通日程编辑/添加
    val isDialogVisible = showAddEventDialog || editDraft != null
    val dialogKey = editDraft?.hashCode() ?: "add_$addDialogRequestId"
    key(dialogKey) {
        AddEventDialog(
            visible = isDialogVisible,
            initialDate = null,
            editDraft = editDraft,
            currentEventsCount = uiState.rawEventCount,
            settings = settings,
            attachments = dialogAttachments,
            onAddAttachment = { uri ->
                if (demoModeEnabled) return@AddEventDialog
                val eventId = editDraft?.eventId ?: return@AddEventDialog
                scope.launch {
                    runCatching { mainViewModel.addAttachmentToEvent(eventId, uri) }
                        .onSuccess { attachment -> dialogAttachments = dialogAttachments + attachment }
                        .onFailure { showToast("附件添加失败: ${it.message}", ToastType.ERROR) }
                }
            },
            onAddPendingAttachment = { uri, eventKey ->
                if (demoModeEnabled) return@AddEventDialog
                scope.launch {
                    runCatching { mainViewModel.addPendingAttachment(eventKey, uri) }
                        .onSuccess { attachment -> dialogAttachments = dialogAttachments + attachment }
                        .onFailure { showToast("附件添加失败: ${it.message}", ToastType.ERROR) }
                }
            },
            onOpenAttachment = { attachment ->
                scope.launch {
                    val opened = mainViewModel.openAttachment(attachment)
                    if (!opened) showToast("无法打开附件", ToastType.ERROR)
                }
            },
            onDeleteAttachment = { attachment ->
                if (demoModeEnabled) return@AddEventDialog
                scope.launch {
                    runCatching { mainViewModel.deleteAttachment(attachment) }
                        .onSuccess { dialogAttachments = dialogAttachments.filterNot { it.id == attachment.id } }
                        .onFailure { showToast("附件删除失败: ${it.message}", ToastType.ERROR) }
                }
            },
            onShowMessage = { message -> showToast(message, ToastType.INFO) },
            onDismiss = {
                pendingAddDialog = false
                showAddEventDialog = false
                editDraft = null
                dialogAttachments.filter { it.eventId == null && it.eventKey.isNotBlank() }
                    .map { it.eventKey }.distinct().forEach { pendingKey ->
                        scope.launch { mainViewModel.deletePendingAttachments(pendingKey) }
                    }
                dialogAttachments = emptyList()
                editContext = null
                recurringEditCommitSession = null
                draftEventToAdd = null
            },
            onConfirm = { patch ->
                if (demoModeEnabled) { showToast("演示模式不会保存修改"); return@AddEventDialog }
                val ctx = editContext
                var nextRecurringCommit: RecurringEditCommitSession? = null
                when (ctx) {
                    is EditContext.SingleEvent -> {
                        mainViewModel.updateSingleFromPatch(ctx.eventId, patch)
                        scope.launch { mainViewModel.refreshAttachmentKey(ctx.eventId) }
                        patch.pendingAttachmentUris.forEach { uri ->
                            scope.launch {
                                runCatching { mainViewModel.addAttachmentToEvent(ctx.eventId, uri) }
                                    .onFailure { showToast("附件添加失败: ${it.message}", ToastType.ERROR) }
                            }
                        }
                    }
                    is EditContext.RecurringOccurrence -> {
                        nextRecurringCommit = RecurringEditCommitSession(
                            parentId = ctx.parentId,
                            occurrenceTs = ctx.occurrenceTs,
                            patch = patch,
                            attachments = dialogAttachments
                        )
                    }
                    is EditContext.NewEvent, null -> {
                        scope.launch {
                            val eventId = mainViewModel.addEventFromPatchWithResult(patch)
                            mainViewModel.bindPendingAttachmentsToEvent(eventId, patch.pendingAttachmentKey)
                        }
                    }
                }
                pendingAddDialog = false
                showAddEventDialog = false
                editDraft = null
                dialogAttachments = emptyList()
                editContext = null
                recurringEditCommitSession = nextRecurringCommit
                draftEventToAdd = null
            }
        )
    }
    }
    CompositionLocalProvider(
        LocalDetailReadOnly provides demoModeEnabled,
        LocalDetailDirtyChanged provides { detailDirty = it },
    ) {
    HomeScreenContent(
        state = HomeShellUiState(
            backgroundEnabled = hasAppBackground,
            backgroundMiuiBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled,
            backgroundCardAlphaPercent = settings.appBackgroundCardAlphaPercent,
        ),
        content = {
            // 设置页 = 原侧边栏内容，作为常驻底栏的固定入口原地切换
            BackHandler(enabled = effectiveSelectedPageKey == HomeEntryKey.SETTINGS) {
                onSelectedPageKeyChange(HomeEntryKey.TODAY)
            }
            if (effectiveSelectedPageKey == HomeEntryKey.SETTINGS) {
                SettingsTabPage(
                    glassMode = hasAppBackground,
                    onNavigate = { destination ->
                        onNavigateToSettings(destination)
                    },
                )
            } else {
            HomePageRoute(
                viewModel = mainViewModel,
                currentPageKey = effectiveSelectedPageKey,
                pageOrder = pagerPageOrder,
                uiSize = settings.uiSize,
                pickupTimestamp = pickupTimestamp,
                isActionExpanded = isActionExpanded,
                onActionExpandedChange = { isActionExpanded = it },
                searchRequestId = searchRequestId,
                imageRequestId = imageRequestId,
                onRequestsHandled = { searchRequestId = 0; imageRequestId = 0 },
                isSidebarOpen = false,
                onPageChange = { page ->
                    if (detailDirty) showToast("请先保存或返回，处理当前未保存的内容")
                    else {
                        isActionExpanded = false
                        searchRequestId = 0
                        imageRequestId = 0
                        editDraft = null
                        showAddEventDialog = false
                        onSelectedPageKeyChange(page)
                    }
                },
                onAddEventClick = { openPrimaryCreateDialog() },
                onEditItem = { item -> beginEditItem(item) },
                onRequestDeleteItem = { item -> requestDeleteItem(item) },
                onRequestDeleteQuickMemo = { memo -> selectedQuickMemoAction = memo },
                onRequestDeleteFlashNote = { key -> selectedFlashNoteKey = key },
                onRequestClearQuickMemos = { showClearQuickMemosConfirm = true },
                quickMemoCount = quickMemoCount,
                onOpenQuickMemoDetail = onOpenQuickMemoDetail,
                onOpenFlashNoteDetail = onOpenFlashNoteDetail,
                memoSegment = memoSegment,
                onMemoSegmentChange = { memoSegment = it },
                onAddQuickMemo = { openMemoInput(MemoStreamSegment.QUICK_MEMO) },
                onAddFlashNote = { openMemoInput(MemoStreamSegment.FLASH_NOTE) },
                onVoiceSchedule = {
                    when {
                        // 随口记那支录音器正在用：不抢麦，提示一下就走。
                        isMemoRecording || isScheduleVoiceSaving ->
                            showToast("请先结束当前录音", ToastType.INFO)

                        else -> scope.launch {
                            runCatching { withContext(Dispatchers.IO) { memoRecorder.start() } }
                                .onSuccess { isScheduleVoiceDialogOpen = true }
                                .onFailure { showToast(it.message ?: "录音失败", ToastType.ERROR) }
                        }
                    }
                },
                onScheduleExpandedChange = { isScheduleExpanded = it },
                onScheduleProgressChange = { scheduleProgress = it },
                onScheduleOffsetChange = { scheduleOffsetPx = it.coerceAtLeast(0f) },
                onOpenWeatherDetail = onOpenWeatherDetail,
                onNavigateToSettings = onNavigateToSettings,
            )
            }
        },
        chrome = {
        // 底栏此前读的是 LocalAppHapticsEnabled 的默认值 true，导致「触感反馈」关闭后
        // 底栏仍震动；此处按用户设置注入。
        androidx.compose.runtime.CompositionLocalProvider(
            LocalAppHapticsEnabled provides settings.hapticFeedbackEnabled
        ) {
        IntegratedFloatingBar(
            isExpanded = isActionExpanded,
            onExpandedChange = { isActionExpanded = it },
            navItems = homeBottomItems,
            selectedPageKey = effectiveSelectedPageKey,
            onPageClick = { pageKey ->
                isActionExpanded = false
                searchRequestId = 0
                imageRequestId = 0
                onSelectedPageKeyChange(pageKey)
            },
            onSearchClick = {
                isActionExpanded = false
                searchRequestId += 1
            },
            onImageClick = {
                isActionExpanded = false
                imageRequestId += 1
            },
            onEditClick = {
                isActionExpanded = false
                openPrimaryCreateDialog()
            },
            backgroundMode = hasAppBackground,
            miuiBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled,
            cardAlphaPercent = settings.appBackgroundCardAlphaPercent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = IntegratedFloatingBarBottomSpacing)
                .zIndex(3f)
        )
        }
        },
        overlay = {
        val deleteItem = scheduleItemToDelete
        val editCommitSession = recurringEditCommitSession
        val clipboardPromptItem = clipboardPrompt
        HomeActionDialog(
            visible = clipboardPromptItem != null,
            title = "识别到剪贴板中的${clipboardPromptItem?.candidate?.type?.displayLabel.orEmpty()}",
            content = "${clipboardPromptItem?.candidate?.type?.displayLabel.orEmpty()}：${clipboardPromptItem?.candidate?.code.orEmpty()}",
            confirmText = "入库",
            dismissText = "忽略",
            isDestructive = false,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = onConfirmClipboardPrompt,
            onDismiss = onDismissClipboardPrompt,
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        HomeActionDialog(
            visible = showClearQuickMemosConfirm && quickMemoCount > 0,
            title = "确认清空",
            content = "此操作将永久删除 $quickMemoCount 条随口记。\n删除后将无法恢复。",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = {
                selectedQuickMemoAction = null
                showClearQuickMemosConfirm = false
                mainViewModel.clearAllQuickMemos()
            },
            onDismiss = { showClearQuickMemosConfirm = false },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        HomeActionDialog(
            visible = selectedQuickMemoAction != null,
            title = "删除随口记",
            content = "删除后无法恢复，确认删除这条随口记吗？",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = {
                selectedQuickMemoAction?.id?.let { mainViewModel.deleteQuickMemo(it) }
                selectedQuickMemoAction = null
            },
            onDismiss = { selectedQuickMemoAction = null },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        // 键盘上方的输入栏：从顶栏 ⋮ 的「添加随口记 / 添加随手记」进来。
        // 挂在这一层而不是菜单里——菜单一点就关，挂里面会被一起卸载。
        val inputTarget = memoInputTarget
        if (inputTarget != null) {
            MemoInputBar(
                text = memoInputText,
                onTextChange = { memoInputText = it },
                onSend = { submitMemoInput() },
                actions = listOf(
                    MemoInputAction("image", Icons.Outlined.Image, "图片") {
                        handleMemoInputAction("image")
                    },
                    MemoInputAction("link", Icons.Outlined.Link, "链接") {
                        handleMemoInputAction("link")
                    },
                    MemoInputAction("capsule", Icons.Outlined.ArrowUpward, "上实况通知") {
                        handleMemoInputAction("capsule")
                    },
                    MemoInputAction(
                        id = "voice",
                        icon = Icons.Outlined.Mic,
                        label = if (isMemoRecording) "结束录音" else "语音转文字",
                        active = isMemoRecording
                    ) { handleMemoInputAction("voice") }
                ),
                hapticEnabled = settings.hapticFeedbackEnabled,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .zIndex(6f)
            )
        }

        // 输入栏开着时，返回键先收输入栏，不退页面。
        BackHandler(enabled = memoInputTarget != null) { closeMemoInput() }

        // 日程页「语音识别」的录音弹窗。同样挂在 HomeScreen 层：
        // 顶栏那个下拉菜单一点就关，弹窗要是挂在菜单里会跟着被卸载。
        if (isScheduleVoiceDialogOpen) {
            ScheduleVoiceDialog(
                isSaving = isScheduleVoiceSaving,
                onStop = { stopScheduleVoice() },
                onDismiss = {
                    scope.launch {
                        withContext(Dispatchers.IO) { memoRecorder.stopAndDiscard() }
                        isScheduleVoiceDialogOpen = false
                    }
                }
            )
        }

        HomeActionDialog(
            visible = selectedFlashNoteKey != null,
            title = "删除闪记",
            content = "删除后无法恢复，确认删除这条闪记吗？",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = {
                selectedFlashNoteKey?.let { mainViewModel.deleteFlashNote(it) }
                selectedFlashNoteKey = null
            },
            onDismiss = { selectedFlashNoteKey = null },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        val singleDeleteItem = deleteItem?.takeIf { it.action is ScheduleDisplayItem.ActionTarget.Single }
        HomeActionDialog(
            visible = singleDeleteItem != null,
            title = "删除日程",
            content = "删除后无法恢复，确认删除这条日程吗？",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = {
                val eventId = (singleDeleteItem?.action as? ScheduleDisplayItem.ActionTarget.Single)?.eventId
                if (eventId != null) mainViewModel.deleteEvent(eventId)
                scheduleItemToDelete = null
            },
            onDismiss = { scheduleItemToDelete = null },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        val recurringDeleteItem = deleteItem?.takeIf { it.action is ScheduleDisplayItem.ActionTarget.RecurringOccurrence }
        PredictiveRecurringDeleteActionCard(
            visible = recurringDeleteItem != null,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onDeleteThis = {
                recurringDeleteItem?.let { mainViewModel.deleteRecurringItem(it, RecurringMode.THIS) }
                scheduleItemToDelete = null
            },
            onDeleteAll = {
                recurringDeleteItem?.let { mainViewModel.deleteRecurringItem(it, RecurringMode.ALL) }
                scheduleItemToDelete = null
            },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        PredictiveRecurringEditActionCard(
            visible = editCommitSession != null,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onEditThis = {
                val session = editCommitSession ?: return@PredictiveRecurringEditActionCard
                recurringEditCommitSession = null
                scope.launch {
                    val editedId = mainViewModel.editRecurringFromPatchWithResult(
                        session.parentId,
                        session.occurrenceTs,
                        RecurringMode.THIS,
                        session.patch
                    )
                    editedId?.let { eventId ->
                        session.attachments.filter { it.eventId == null && it.eventKey.isNotBlank() }.forEach { attachment ->
                            mainViewModel.bindPendingAttachmentsToEvent(eventId, attachment.eventKey)
                        }
                    }
                }
            },
            onEditAll = {
                val session = editCommitSession ?: return@PredictiveRecurringEditActionCard
                recurringEditCommitSession = null
                scope.launch {
                    val editedId = mainViewModel.editRecurringFromPatchWithResult(
                        session.parentId,
                        session.occurrenceTs,
                        RecurringMode.ALL,
                        session.patch
                    )
                    editedId?.let { eventId ->
                        session.attachments.filter { it.eventId == null && it.eventKey.isNotBlank() }.forEach { attachment ->
                            mainViewModel.bindPendingAttachmentsToEvent(eventId, attachment.eventKey)
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        // SnackbarHost 放在屏幕底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = toastFloatingBarOffset),
            snackbar = { snackbarData ->
                UniversalToast(message = snackbarData.visuals.message, type = currentToastType)
            }
        )
        },
    )

    }

    // --- 全局弹窗处理 (仅保留日常操作) ---

    editorContent()

}

@Composable
private fun PredictiveRecurringDeleteActionCard(
    visible: Boolean,
    predictiveBackEnabled: Boolean,
    onDeleteThis: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeActionDialog(
        visible = visible,
        title = "删除重复日程",
        content = "删除本次还是全部？",
        confirmText = "全部",
        dismissText = "仅本次",
        dismissIsDestructive = true,
        isDestructive = true,
        predictiveBackEnabled = predictiveBackEnabled,
        onConfirm = onDeleteAll,
        onDismiss = onDeleteThis,
        modifier = modifier
    )
}

@Composable
private fun PredictiveRecurringEditActionCard(
    visible: Boolean,
    predictiveBackEnabled: Boolean,
    onEditThis: () -> Unit,
    onEditAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeActionDialog(
        visible = visible,
        title = "编辑重复日程",
        content = "保存到本次还是全部？",
        confirmText = "全部",
        dismissText = "仅本次",
        isDestructive = false,
        predictiveBackEnabled = predictiveBackEnabled,
        onConfirm = onEditAll,
        onDismiss = onEditThis,
        modifier = modifier
    )
}

/**
 * 从剪贴板取纯文本，供「闪记 · 链接」入口使用。
 *
 * 只取第一项；部分系统限制后台读剪贴板时会返回空，
 * 调用方需要走退化路径（转成手动输入），不要直接把用户卡住。
 */
private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}
