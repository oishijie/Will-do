package com.antgskds.calendarassistant.platform.floating

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import com.antgskds.calendarassistant.shared.query.ScheduleQueryApi
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.pm.ServiceInfo
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.antgskds.calendarassistant.platform.floating.capsule.HandleTheme
import com.antgskds.calendarassistant.platform.floating.capsule.OverlayConstants
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.schedule.domain.ScheduleDisplayHelper
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.RecognitionFailureMessageMapper
import com.antgskds.calendarassistant.feature.recognition.application.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.feature.recognition.application.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.feature.schedule.application.ScheduleFacade
import com.antgskds.calendarassistant.shared.event.DomainEventType
import com.antgskds.calendarassistant.shared.event.EventIdentity
import com.antgskds.calendarassistant.shared.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.shared.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.shared.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.feature.weather.api.WeatherQueryApi
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTranscriptionStatus
import com.antgskds.calendarassistant.feature.quickmemo.data.audio.QuickMemoAudioRecorder
import com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoAutoStopPolicy
import com.antgskds.calendarassistant.feature.quickmemo.domain.model.QuickMemoVoiceCaptureState
import com.antgskds.calendarassistant.feature.quickmemo.domain.model.QuickMemoVoiceCaptureStatus
import com.antgskds.calendarassistant.feature.weather.domain.hasWeatherConfig
import com.antgskds.calendarassistant.core.service.image.ImagePickHandleActivity
import com.antgskds.calendarassistant.shared.util.ImageImportUtils
import com.antgskds.calendarassistant.feature.schedule.domain.model.RecurringMode
import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.*
import com.antgskds.calendarassistant.feature.schedule.application.model.EventPatch
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.platform.floating.ui.connector.EventMediaFloatingCardRoute
import com.antgskds.calendarassistant.platform.floating.ui.connector.ImagePinMediaFloatingCardRoute
import com.antgskds.calendarassistant.platform.floating.ui.connector.QuickMemoMediaFloatingCardRoute
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.SystemNormalDisplay
import com.antgskds.calendarassistant.app.ui.theme.CalendarAssistantStyleTheme
import com.antgskds.calendarassistant.app.ui.theme.ThemeColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import java.io.File
import java.time.LocalDate

class FloatingScheduleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingScheduleService"
        private const val RECOGNITION_SOURCE_TYPE = "floating"
        private const val RECOGNITION_SOURCE_ID = "floating.manual_input"
        private const val RECENT_VOICE_MEMO_HOLD_MS = 5_000L
        private const val TEXT_DRAG_WINDOW_ANIMATION_MS = 180L
        /** 把手纵向偏移的持久化：存 dp，相对垂直居中的偏移（向下为正），左右停靠共用。 */
        private const val PREFS_NAME = "floating_overlay_prefs"
        private const val HANDLE_Y_OFFSET_KEY = "overlay_handle_y_offset_dp"

        const val ACTION_IMAGE_PICKED = "com.antgskds.calendarassistant.floating.action.IMAGE_PICKED"
        const val ACTION_IMAGE_PICK_CANCELLED = "com.antgskds.calendarassistant.floating.action.IMAGE_PICK_CANCELLED"
        const val ACTION_PREPARE_VOICE_CAPTURE = "com.antgskds.calendarassistant.floating.action.PREPARE_VOICE_CAPTURE"
        const val ACTION_START_VOICE_CAPTURE = "com.antgskds.calendarassistant.floating.action.START_VOICE_CAPTURE"
        const val ACTION_STOP_VOICE_CAPTURE = "com.antgskds.calendarassistant.floating.action.STOP_VOICE_CAPTURE"
        const val ACTION_SHOW_PICKUP_QR_CARD = "com.antgskds.calendarassistant.floating.action.SHOW_PICKUP_QR_CARD"
        const val ACTION_SHOW_IMAGE_PIN = "com.antgskds.calendarassistant.SHOW_IMAGE_PIN"

        /** 外部请求关闭悬浮窗（音量键 toggle 的“再按一次收起”）。 */
        const val ACTION_REQUEST_CLOSE = "com.antgskds.calendarassistant.floating.action.REQUEST_CLOSE"
        const val ACTION_SHOW_QUICK_MEMO_MEDIA_CARD = "com.antgskds.calendarassistant.floating.action.SHOW_QUICK_MEMO_MEDIA_CARD"
        const val ACTION_VOICE_CAPTURE_RECORDING = "com.antgskds.calendarassistant.floating.action.VOICE_CAPTURE_RECORDING"
        const val ACTION_VOICE_CAPTURE_COMPLETED = "com.antgskds.calendarassistant.floating.action.VOICE_CAPTURE_COMPLETED"
        const val ACTION_VOICE_CAPTURE_TOO_SHORT = "com.antgskds.calendarassistant.floating.action.VOICE_CAPTURE_TOO_SHORT"
        const val ACTION_VOICE_CAPTURE_ERROR = "com.antgskds.calendarassistant.floating.action.VOICE_CAPTURE_ERROR"
        const val ACTION_FLOATING_SHOWN = "com.antgskds.calendarassistant.floating.action.SHOWN"
        const val ACTION_FLOATING_HIDDEN = "com.antgskds.calendarassistant.floating.action.HIDDEN"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_VOICE_AUDIO_PATH = "extra_voice_audio_path"
        const val EXTRA_VOICE_DURATION_MS = "extra_voice_duration_ms"
        const val EXTRA_VOICE_ERROR_MESSAGE = "extra_voice_error_message"
        const val EXTRA_PICKUP_EVENT_ID = "extra_pickup_event_id"
        const val EXTRA_QUICK_MEMO_ID = "extra_quick_memo_id"
        const val EXTRA_MEDIA_IMAGE_PATH = "extra_media_image_path"
        const val EXTRA_MEDIA_IMAGE_PATHS = "extra_media_image_paths"
        const val EXTRA_MEDIA_TITLE = "extra_media_title"

        @Volatile var isShowing: Boolean = false
            private set
        @Volatile var instance: FloatingScheduleService? = null
            private set
    }

    fun isCapturingNow(): Boolean =
        voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.RECORDING

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var isViewAttached: Boolean = false
    private var baseWindowFlags: Int = 0

    private var pendingImagePickCompletion: (() -> Unit)? = null
    private var pendingImagePickAsQuickMemo: Boolean = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val voiceCaptureState = MutableStateFlow(QuickMemoVoiceCaptureState())
    private val recentVoiceMemoId = MutableStateFlow<Long?>(null)
    private val mediaRequest = MutableStateFlow<FloatingMediaRequest?>(null)
    private val capsuleExpandRequest = MutableStateFlow(0L)
    private val screenOffRequest = MutableStateFlow(0L)
    private val screenOnRequest = MutableStateFlow(0L)
    private var screenOn = true
    private var awaitingScreenRestore = false
    private var closingOnScreenOff = false
    // 本次启动是否为用户主动唤起（音量键/磁贴/快捷方式），决定首次渲染是否直接展开面板
    private var expandOnLaunch = false
    // 本次启动是否由媒体卡片或录音拉起（这两类有自己的视图，不应展开面板）
    private var initialHasMediaOrVoice = false
    private val audioRecorder by lazy { QuickMemoAudioRecorder(applicationContext) }
    private var voiceConfirmJob: Job? = null
    private var recentVoiceMemoJob: Job? = null
    private var voiceStartJob: Job? = null
    private var voiceAutoStopJob: Job? = null
    private var voiceStopRequested: Boolean = false
    /** 录音中收到关窗请求：停录并落库，保存完成后再关窗（不丢录音）。 */
    private var closeAfterVoiceSave: Boolean = false
    private var voiceForegroundActive: Boolean = false
    private var mediaFallbackInProgress: Boolean = false

    private data class FloatingMediaRequest(
        val eventId: Long? = null,
        val quickMemoId: Long? = null,
        val imagePinId: Long? = null,
        val imagePaths: List<String> = emptyList(),
        val title: String = ""
    )

    private val app by lazy { applicationContext as App }
    private val scheduleCenter: ScheduleFacade by lazy { app.scheduleCenter }
    private val quickMemoCenter by lazy { app.quickMemoCenter }
    private val audioPlaybackCenter by lazy { app.audioPlaybackCenter }
    private val scheduleQueryApi: ScheduleQueryApi by lazy { app.scheduleQueryApi }
    private val settingsQueryApi: SettingsQueryApi by lazy { app.settingsQueryApi }
    private val weatherQueryApi: WeatherQueryApi by lazy { app.weatherQueryApi }
    private val permissionCenter by lazy { app.permissionCenter }
    private val domainEventBus by lazy { app.domainEventBus }
    private var recognitionFailedSubscriptionJob: Job? = null
    private var ingestSucceededSubscriptionJob: Job? = null
    private var ingestFailedSubscriptionJob: Job? = null
    // 广播接收器
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    composeView?.visibility = View.GONE
                    if (voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.IDLE && mediaRequest.value == null) {
                        awaitingScreenRestore = true
                        val current = settingsQueryApi.settings.value
                        val closeOnScreenOff = current.floatingHandleAutoHideSeconds !=
                            OverlayConstants.HANDLE_AUTO_HIDE_NEVER_SECONDS && !current.floatingEdgeLineEnabled
                        if (closeOnScreenOff) {
                            closingOnScreenOff = true
                            requestClose()
                        } else {
                            screenOffRequest.value++
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    if (closingOnScreenOff) return
                    if (awaitingScreenRestore) {
                        screenOnRequest.value++
                    } else if (isViewAttached) {
                        composeView?.visibility = View.VISIBLE
                    }
                }
                TextAccessibilityService.ACTION_CLOSE_FLOATING -> {
                    requestClose()
                }
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    // 监听 Home 键或多任务键，自动关闭悬浮窗
                    val reason = intent.getStringExtra("reason")
                    if (reason == "homekey" || reason == "recentapps") {
                        requestClose()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isShowing = true
        sendBroadcast(Intent(ACTION_FLOATING_SHOWN))
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 注册广播 (兼容 Android 12+)
        val filter = IntentFilter().apply {
            addAction(TextAccessibilityService.ACTION_CLOSE_FLOATING)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                // Android 12+ 限制了系统广播的接收，通常不需要手动监听 Home 键，系统会处理层级
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }

        if (!permissionCenter.canDrawOverlays(this)) {
            requestClose()
            return
        }

        subscribeRecognitionFailedEvents()
        subscribeIngestEvents()
        initUI()
    }

    private fun subscribeRecognitionFailedEvents() {
        recognitionFailedSubscriptionJob?.cancel()
        recognitionFailedSubscriptionJob = serviceScope.launch {
            domainEventBus
                .eventsOfType<RecognitionFailedEvent>(DomainEventType.RECOGNITION_FAILED)
                .collect { event ->
                    val payload = event.payload
                    if (payload.sourceType != RECOGNITION_SOURCE_TYPE || payload.sourceId != RECOGNITION_SOURCE_ID) {
                        return@collect
                    }
                    Toast.makeText(
                        applicationContext,
                        RecognitionFailureMessageMapper.userMessage(payload),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun subscribeIngestEvents() {
        ingestSucceededSubscriptionJob?.cancel()
        ingestSucceededSubscriptionJob = serviceScope.launch {
            domainEventBus
                .eventsOfType<IngestSucceededEvent>(DomainEventType.INGEST_SUCCEEDED)
                .collect { event ->
                    val payload = event.payload
                    if (payload.sourceType != RECOGNITION_SOURCE_TYPE || payload.sourceId != RECOGNITION_SOURCE_ID) {
                        return@collect
                    }
                    val message = when {
                        payload.createdCount <= 0 -> "已处理，无新增"
                        payload.createdCount == 1 -> "已添加 1 个事件"
                        else -> "已添加 ${payload.createdCount} 个事件"
                    }
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                }
        }

        ingestFailedSubscriptionJob?.cancel()
        ingestFailedSubscriptionJob = serviceScope.launch {
            domainEventBus
                .eventsOfType<IngestFailedEvent>(DomainEventType.INGEST_FAILED)
                .collect { event ->
                    val payload = event.payload
                    if (payload.sourceType != RECOGNITION_SOURCE_TYPE || payload.sourceId != RECOGNITION_SOURCE_ID) {
                        return@collect
                    }
                    Toast.makeText(
                        applicationContext,
                        payload.message.ifBlank { "保存失败" },
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun initUI() {
        val params = WindowManager.LayoutParams(
            (OverlayConstants.HANDLE_WIDTH_DP * resources.displayMetrics.density).toInt(),
            (OverlayConstants.HANDLE_HEIGHT_DP * resources.displayMetrics.density).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // 小窗口与闪念胶囊一致：窗口边界即胶囊边界，透明区域不拦截触摸。
            // 不能用 FLAG_LAYOUT_NO_LIMITS：它允许窗口超出屏幕边界，配合 Gravity.END
            // 时右边缘会算到屏幕外，右侧展开的内容被推出可视区。
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            // 横向贴停靠侧靠 gravity；纵向位置由持久化偏移决定（垂直居中 + 偏移），
            // 拖动松手落盘，重启/切侧不丢位置。
            val dockLeft = settingsQueryApi.settings.value.floatingExpandSide == "LEFT"
            gravity = (if (dockLeft) android.view.Gravity.START else android.view.Gravity.END) or
                android.view.Gravity.CENTER_VERTICAL
            x = 0
            y = handleYOffsetPxClamped(
                (OverlayConstants.HANDLE_HEIGHT_DP * resources.displayMetrics.density).toInt()
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        windowLayoutParams = params
        baseWindowFlags = params.flags

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingScheduleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingScheduleService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            // 【修复关闭按钮重叠问题】
            // 在悬浮窗模式下，Compose 有时无法自动获取 statusBarsPadding。
            // 这里强制设置 systemUiVisibility 帮助 Compose 识别全屏状态
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )

            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    requestClose()
                    return@setOnKeyListener true
                }
                false
            }

            isFocusable = true
            isFocusableInTouchMode = true

            setContent {
                val events by scheduleQueryApi.events.collectAsState()
                val settings by settingsQueryApi.settings.collectAsState()
                val mediaRequestState by mediaRequest.collectAsState()
                // 闪念胶囊收起/展开态：收起时窗口 28×88 只显示把手，展开时 resize 到面板尺寸。
                // 展开态不能靠 rememberSaveable 持久：窗口重建时若残留 true，
                // 28×88 窗口里渲染整块面板会布局异常导致悬浮窗不可见。
                // 用户主动唤起（音量键/磁贴/快捷方式）直接展开面板，省一次点击；
                // 媒体卡片/录音拉起时不展开，它们有自己的视图。
                var capsuleExpanded by remember {
                    mutableStateOf(expandOnLaunch && !initialHasMediaOrVoice)
                }
                val expandRequest by capsuleExpandRequest.collectAsState()
                // 展开的卡片集合提到这一层：原生窗口宽度要跟着「有卡展开」变化
                // （无展开 0.72 屏宽 → 有展开 0.92 屏宽，对齐参考实现）
                var expandedIds by remember { mutableStateOf(emptySet<String>()) }
                var panelMaxCards by remember { mutableStateOf(settings.floatingPanelMaxCards) }
                var fontSizeStep by remember { mutableStateOf(settings.floatingFontSizeStep.coerceIn(-2, 2)) }
                LaunchedEffect(capsuleExpanded) {
                    if (capsuleExpanded) {
                        val current = settingsQueryApi.settings.value
                        panelMaxCards = current.floatingPanelMaxCards
                        fontSizeStep = current.floatingFontSizeStep.coerceIn(-2, 2)
                    }
                }
                // 自动隐藏后的竖线态（把手瘦身版）
                var edgeLineVisible by remember { mutableStateOf(false) }
                var edgeLineMarginDp by remember { mutableStateOf(settings.floatingEdgeLineMarginDp) }
                LaunchedEffect(edgeLineVisible) {
                    if (edgeLineVisible) edgeLineMarginDp = settingsQueryApi.settings.value.floatingEdgeLineMarginDp
                }
                LaunchedEffect(expandRequest) {
                    if (expandRequest > 0L) {
                        edgeLineVisible = false
                        capsuleExpanded = true
                    }
                }
                val screenOffGeneration by screenOffRequest.collectAsState()
                val screenOnGeneration by screenOnRequest.collectAsState()
                var handledScreenOffGeneration by remember { mutableStateOf(0L) }
                LaunchedEffect(screenOffGeneration) {
                    if (screenOffGeneration == 0L) return@LaunchedEffect
                    if (voiceCaptureState.value.status != QuickMemoVoiceCaptureStatus.IDLE) {
                        handledScreenOffGeneration = screenOffGeneration
                        return@LaunchedEffect
                    }
                    audioPlaybackCenter.stop()
                    capsuleExpanded = false
                    expandedIds = emptySet()
                    val current = settingsQueryApi.settings.value
                    edgeLineVisible = current.floatingHandleAutoHideSeconds !=
                        OverlayConstants.HANDLE_AUTO_HIDE_NEVER_SECONDS
                    handledScreenOffGeneration = screenOffGeneration
                }
                // 划走删除的撤销状态：待撤销的闪记 id 与气泡文案
                var pendingSwipeDeleteMemoId by remember { mutableStateOf<Long?>(null) }
                var swipeDeleteGeneration by remember { mutableStateOf(0L) }
                var undoMessage by remember { mutableStateOf<String?>(null) }
                val handleSizePercent = settings.floatingHandleSizePercent
                val handleTheme = remember(settings.floatingHandleTheme) {
                    runCatching { HandleTheme.valueOf(settings.floatingHandleTheme) }
                        .getOrDefault(HandleTheme.DUO)
                }
                // 录音态：浮窗改成录音胶囊（计时 + 实时电平波形 + 停止钮）
                val voiceState by voiceCaptureState.collectAsState()
                val isRecordingNow = voiceState.status == QuickMemoVoiceCaptureStatus.RECORDING ||
                    voiceState.status == QuickMemoVoiceCaptureStatus.SAVING ||
                    voiceState.status == QuickMemoVoiceCaptureStatus.TRANSCRIBING ||
                    voiceState.status == QuickMemoVoiceCaptureStatus.CONFIRMING
                // 录音计时：voiceState.durationMs 只在保存时赋值，录音过程中恒 0，
                // 所以界面上自己跑一个秒级 ticker，不动 service 状态机。
                var recordElapsedMs by remember { mutableStateOf(0L) }
                LaunchedEffect(voiceState.status) {
                    if (voiceState.status != QuickMemoVoiceCaptureStatus.RECORDING) {
                        if (!isRecordingNow) recordElapsedMs = 0L
                        return@LaunchedEffect
                    }
                    val startedAt = System.currentTimeMillis()
                    while (true) {
                        recordElapsedMs = System.currentTimeMillis() - startedAt
                        kotlinx.coroutines.delay(200)
                    }
                }
                LaunchedEffect(
                    capsuleExpanded, mediaRequestState, handleSizePercent, isRecordingNow,
                    edgeLineVisible, handledScreenOffGeneration, screenOffGeneration, screenOnGeneration,
                ) {
                    if (handledScreenOffGeneration != screenOffGeneration) return@LaunchedEffect
                    val density = resources.displayMetrics.density
                    val handleHeightPx = (OverlayConstants.HANDLE_HEIGHT_DP * density).toInt()
                    if (mediaRequestState != null) {
                        // 媒体卡片（图片挂起 / 取件码 / 闪记图片）是全屏居中大卡，
                        // 必须撑满窗口；沿用把手窗口尺寸会渲染不出卡片。
                        resizeWindow(
                            resources.displayMetrics.widthPixels,
                            resources.displayMetrics.heightPixels,
                            focusable = true
                        )
                    } else if (isRecordingNow) {
                        // 录音胶囊：宽度够放波形，高度只占一条，仍贴停靠侧垂直居中。
                        resizeWindow(
                            ((OverlayConstants.RECORD_BAR_MAX_WIDTH + OverlayConstants.RECORD_BAR_EDGE_MARGIN).value * density).toInt(),
                            (OverlayConstants.RECORD_BAR_HEIGHT.value * density).toInt() + (12 * density).toInt(),
                            focusable = false,
                            yOffsetPx = 0
                        )
                    } else if (capsuleExpanded) {
                        val screenWidthPx = resources.displayMetrics.widthPixels
                        // 窗口宽度恒定用最宽的展开比例（0.92），不跟随「是否有卡展开」跳变：
                        // 之前把 expandedIds.isNotEmpty() 放进 key，展开一张卡就瞬时重调
                        // 窗口尺寸，整块面板一下子变宽——这是「展开时界面跳一下」的一半原因。
                        // 面板自身的 0.72→0.92 宽度补间放在 CapsulePanel 内部用动画做
                        //（对齐参考实现 AnimatedContainer 补间面板宽度）。
                        val panelWidthPx = (screenWidthPx * OverlayConstants.EXPANDED_PANEL_WIDTH_RATIO).toInt()
                        val screenHeightPx = resources.displayMetrics.heightPixels
                        resizeWindow(panelWidthPx, screenHeightPx, focusable = true)
                    } else if (edgeLineVisible) {
                        // 竖线窗口：20×64 透明触摸缓冲，视觉线 4dp 贴缘
                        resizeWindow(
                            (OverlayConstants.EDGE_LINE_WINDOW_WIDTH_DP.value * density).toInt(),
                            (OverlayConstants.EDGE_LINE_HEIGHT_DP.value * density).toInt(),
                            focusable = false,
                            yOffsetPx = handleYOffsetPxClamped(
                                (OverlayConstants.EDGE_LINE_HEIGHT_DP.value * density).toInt()
                            )
                        )
                    } else {
                        resizeWindow(
                            (OverlayConstants.HANDLE_WIDTH_DP * density).toInt(),
                            handleHeightPx,
                            focusable = false,
                            yOffsetPx = handleYOffsetPxClamped(handleHeightPx)
                        )
                    }
                    if (screenOn && !closingOnScreenOff && handledScreenOffGeneration == screenOffRequest.value) {
                        awaitingScreenRestore = false
                        if (isViewAttached) composeView?.visibility = View.VISIBLE
                    }
                }
                // 只在媒体卡片真正退出时收起；首次主动唤起时初始 null 不应覆盖展开态。
                var hadMediaRequest by remember { mutableStateOf(mediaRequestState != null) }
                LaunchedEffect(mediaRequestState) {
                    if (hadMediaRequest && mediaRequestState == null) capsuleExpanded = false
                    hadMediaRequest = mediaRequestState != null
                }
                // 收起态自动隐藏（对齐声物记 _scheduleAutoHide）：
                // 只在收起态计时；录音/转写中直接跳过；「永久」档（-1）不起计时。
                // 到期后默认缩成贴边竖线（edgeLineEnabled 默认 true），
                // 只有关掉竖线开关时才彻底隐藏悬浮窗。
                val autoHideSeconds = settings.floatingHandleAutoHideSeconds
                val edgeLineEnabled = settings.floatingEdgeLineEnabled
                LaunchedEffect(
                    capsuleExpanded,
                    isRecordingNow,
                    mediaRequestState,
                    autoHideSeconds,
                    edgeLineEnabled,
                    edgeLineVisible,
                ) {
                    if (edgeLineVisible) return@LaunchedEffect
                    // 非收起态不计时（双保险：isCollapsed 判定）
                    if (capsuleExpanded) return@LaunchedEffect
                    // 录音/转写期间不自动隐藏，中途消失会丢 UI 反馈
                    if (voiceState.status != QuickMemoVoiceCaptureStatus.IDLE) return@LaunchedEffect
                    if (mediaRequestState != null) return@LaunchedEffect
                    // 「永久」档：把手常驻，不起计时
                    if (autoHideSeconds == OverlayConstants.HANDLE_AUTO_HIDE_NEVER_SECONDS) {
                        return@LaunchedEffect
                    }
                    kotlinx.coroutines.delay(autoHideSeconds * 1000L)
                    if (edgeLineEnabled) {
                        edgeLineVisible = true
                    } else {
                        requestClose()
                    }
                }
                // 悬浮窗是独立 Window，不在 MainActivity 的 Compose 树内，需单独注入触感开关。
                // 否则 LocalAppHapticsEnabled 取默认值 true，用户关闭也无法生效。
                androidx.compose.runtime.CompositionLocalProvider(
                    com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
                        provides settings.hapticFeedbackEnabled
                ) {
                val context = LocalContext.current
                val quickMemos by quickMemoCenter.quickMemos.collectAsState()
                // 转写反馈：等本次语音闪记的真实转写终态（SUCCESS/FAILED）才结束，
                // 不以“已入库”代替“已转写”（对齐声物记 recording→transcribing→idle）。
                LaunchedEffect(voiceState.status, voiceState.memoId, quickMemos) {
                    if (voiceState.status != QuickMemoVoiceCaptureStatus.TRANSCRIBING) return@LaunchedEffect
                    val trackedId = voiceState.memoId ?: return@LaunchedEffect
                    val memo = quickMemos.firstOrNull { it.id == trackedId } ?: return@LaunchedEffect
                    if (memo.transcriptionStatus == QuickMemoTranscriptionStatus.SUCCESS ||
                        memo.transcriptionStatus == QuickMemoTranscriptionStatus.FAILED
                    ) {
                        voiceCaptureState.value = QuickMemoVoiceCaptureState()
                    }
                }
                val currentMediaRequest by mediaRequest.collectAsState()
                val mediaEvent = currentMediaRequest?.eventId?.let { eventId ->
                    events.firstOrNull { it.id == eventId }
                }

                // 根据悬浮窗日程范围设置过滤事件
                val today = LocalDate.now()
                val scheduleEvents = events.filter { it.archivedAt == null }
                val (displayFrom, displayTo) = when (settings.floatingEventRange) {
                    1 -> today to today
                    2 -> today to today.plusDays(1)
                    else -> (scheduleEvents.minOfOrNull { it.startDate } ?: today) to today.plusDays(7) // 0 = 过去全部到未来7天
                }
                val scheduleItems = scheduleCenter.applyPendingStateOverrides(
                    ScheduleDisplayHelper.buildDisplayItems(scheduleEvents, displayFrom, displayTo)
                )

                val isDarkTheme = when (settings.themeMode) {
                    1 -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    2 -> false
                    3 -> true
                    else -> false
                }
                val themeColorSchemeEnum = ThemeColorScheme.fromName(settings.themeColorScheme)

                CalendarAssistantStyleTheme(
                    darkTheme = isDarkTheme,
                    dynamicColor = themeColorSchemeEnum == ThemeColorScheme.DEFAULT,
                    themeColorScheme = themeColorSchemeEnum,
                    customThemeColorHex = settings.customThemeColorHex
                ) {
                    // 委托属性的 smart cast 受限，取一份局部值供渲染分支与回调共用
                    val activeMediaRequest = currentMediaRequest
                    if (isRecordingNow) {
                        // 录音态优先：此时窗口已 resize 成录音胶囊尺寸，
                        // 面板/把手都不该渲染。
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .padding(
                                    start = if (settings.floatingExpandSide == "LEFT") OverlayConstants.RECORD_BAR_EDGE_MARGIN else 0.dp,
                                    end = if (settings.floatingExpandSide == "LEFT") 0.dp else OverlayConstants.RECORD_BAR_EDGE_MARGIN,
                                ),
                            contentAlignment = if (settings.floatingExpandSide == "LEFT")
                                androidx.compose.ui.Alignment.CenterStart else androidx.compose.ui.Alignment.CenterEnd,
                        ) {
                            com.antgskds.calendarassistant.platform.floating.capsule.CapsuleRecordingBar(
                                elapsedMs = recordElapsedMs,
                                onStop = { stopVoiceCapture() },
                                dockLeft = settings.floatingExpandSide == "LEFT",
                                saving = voiceState.status != QuickMemoVoiceCaptureStatus.RECORDING,
                                transcribing = voiceState.status == QuickMemoVoiceCaptureStatus.TRANSCRIBING,
                            )
                        }
                    } else if (activeMediaRequest != null) {
                        if (activeMediaRequest.imagePinId != null) {
                            androidx.compose.runtime.LaunchedEffect(settings.imagePinEnabled) {
                                if (!settings.imagePinEnabled) requestClose()
                            }
                            ImagePinMediaFloatingCardRoute(
                                imagePaths = activeMediaRequest.imagePaths,
                                onClose = { dismissMediaCard() }
                            )
                        } else if (mediaEvent != null) {
                            EventMediaFloatingCardRoute(
                                event = mediaEvent,
                                imagePaths = activeMediaRequest.imagePaths,
                                onClose = { dismissMediaCard() },
                                onComplete = {
                                    val eventId = mediaEvent.id
                                    if (eventId != null) {
                                        scheduleCenter.completeItem(ScheduleDisplayItem.ActionTarget.Single(eventId))
                                    }
                                    dismissMediaCard()
                                },
                                onMediaUnavailable = { openMediaSourceInApp(activeMediaRequest) }
                            )
                        } else if (activeMediaRequest.quickMemoId != null) {
                            QuickMemoMediaFloatingCardRoute(
                                memoId = activeMediaRequest.quickMemoId,
                                bodyText = activeMediaRequest.title,
                                imagePath = activeMediaRequest.imagePaths.firstOrNull().orEmpty(),
                                onClose = { dismissMediaCard() },
                                onMediaUnavailable = { openMediaSourceInApp(activeMediaRequest) }
                            )
                        }
                    } else if (capsuleExpanded) {
                        val capsuleItems = remember(scheduleItems, quickMemos, today) {
                            buildCapsuleItems(scheduleItems, quickMemos)
                        }
                        com.antgskds.calendarassistant.platform.floating.capsule.CapsulePanel(
                            items = capsuleItems,
                            expandedIds = expandedIds,
                            panelMaxCards = panelMaxCards,
                            fontSizeStep = fontSizeStep,
                            onToggleExpanded = { key ->
                                expandedIds = if (key in expandedIds) expandedIds - key else expandedIds + key
                            },
                            onToggleExpandAll = { expand ->
                                expandedIds = if (expand) capsuleItems.map { it.key }.toSet() else emptySet()
                            },
                            onOpenQuickMemo = {
                                // 照搬 TextAccessibilityService.openInAppQuickMemo 的写法。
                                // 不在这里 requestClose()：startActivity 后立刻 stopSelf 会
                                // 把刚建起的窗口事务打断，Activity 收不到 intent。
                                runCatching {
                                    startActivity(Intent(this@FloatingScheduleService, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        putExtra(MainActivity.EXTRA_OPEN_QUICK_MEMO, true)
                                    })
                                }.onFailure { Log.e(TAG, "打开闪记页失败", it) }
                            },
                            onStartVoiceCapture = {
                                capsuleExpanded = false
                                startVoiceCapture()
                            },
                            onItemAction = { item, action ->
                                handleCapsuleAction(item, action.id)
                            },
                            visible = true,
                            dockLeft = settings.floatingExpandSide == "LEFT",
                            onDismiss = { capsuleExpanded = false },
                            onCheckChange = { item, checked ->
                                handleCapsuleCheck(item, checked)
                            },
                            onPaletteSelect = { item, index ->
                                handleCapsulePaletteSelect(item, index)
                            },
                            // 划走：日程归档，闪记也归档（归档后可从归档区恢复，撤销胶囊提供 3 秒快捷撤销）。
                            onSwipeDismiss = { item ->
                                if (item.key.startsWith("schedule:")) {
                                    handleCapsuleCheck(item, true)
                                } else if (item.key.startsWith("memo:")) {
                                    val memoId = item.key.removePrefix("memo:").toLongOrNull()
                                    if (memoId != null) {
                                        val generation = ++swipeDeleteGeneration
                                        pendingSwipeDeleteMemoId = null
                                        undoMessage = null
                                        serviceScope.launch {
                                            runCatching { quickMemoCenter.archiveQuickMemo(memoId) }
                                                .onSuccess {
                                                    if (generation == swipeDeleteGeneration) {
                                                        pendingSwipeDeleteMemoId = memoId
                                                        undoMessage = "已归档"
                                                    }
                                                }
                                                .onFailure {
                                                    Log.e(TAG, "划走归档闪记失败 id=$memoId", it)
                                                }
                                        }
                                    }
                                }
                            },
                            undoMessage = undoMessage,
                            undoKey = swipeDeleteGeneration,
                            onToggleTodo = { item ->
                                val memoId = item.key.removePrefix("memo:").toLongOrNull()
                                if (memoId != null) {
                                    serviceScope.launch {
                                        runCatching { quickMemoCenter.toggleTodoCompletion(memoId) }
                                            .onFailure { Log.e(TAG, "切换待办完成态失败 id=$memoId", it) }
                                    }
                                }
                            },
                            onUndoSwipe = { generation ->
                                val memoId = pendingSwipeDeleteMemoId
                                if (generation == swipeDeleteGeneration && memoId != null) {
                                    pendingSwipeDeleteMemoId = null
                                    undoMessage = null
                                    serviceScope.launch {
                                        runCatching { quickMemoCenter.restoreQuickMemoArchived(memoId) }
                                            .onFailure {
                                                Log.e(TAG, "撤销归档闪记失败 id=$memoId", it)
                                                Toast.makeText(applicationContext, "恢复失败", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            },
                            onSwipeDeleteCommitted = { generation ->
                                if (generation == swipeDeleteGeneration) {
                                    pendingSwipeDeleteMemoId = null
                                    undoMessage = null
                                }
                            },
                            // 闹钟预填：先按文字识别，识别不到用下个整点。
                            resolveInitialTime = { item ->
                                com.antgskds.calendarassistant.platform.floating.capsule.CapsuleTimeParser
                                    .parse(item.text)
                                    ?: com.antgskds.calendarassistant.platform.floating.capsule
                                        .defaultCapsulePickerTime()
                            },
                            onTimeConfirmed = { item, picked, enableAlarm ->
                                createCapsuleAlarmAt(item.text, picked, enableAlarm)
                            },
                            onInputSubmit = { text, mode ->
                                // 闪记存 quick_memos；日程走识别链路建日程。
                                // 分发沿用已有的 handleManualInput，不再另写一套。
                                handleManualInput(
                                    text = text,
                                    isQuickMemo = mode == com.antgskds.calendarassistant.platform.floating.capsule.FloatingInputMode.NOTE,
                                )
                            },
                        )
                    } else if (edgeLineVisible) {
                        // 自动隐藏后的贴边竖线：点按即退回把手态
                        com.antgskds.calendarassistant.platform.floating.capsule.OverlayEdgeLine(
                            dockLeft = settings.floatingExpandSide == "LEFT",
                            tapEnabled = settings.floatingEdgeLineTapEnabled,
                            marginDp = edgeLineMarginDp,
                            onTap = { edgeLineVisible = false },
                            onSwipeInward = {
                                edgeLineVisible = false
                                capsuleExpanded = true
                            },
                        )
                    } else {
                        com.antgskds.calendarassistant.platform.floating.capsule.OverlayHandle(
                            onTap = { capsuleExpanded = true },
                            onSwipeInward = { capsuleExpanded = true },
                            onDrag = { deltaY -> moveHandleVertically(deltaY) },
                            onDragEnd = { persistHandleYOffset() },
                            dockLeft = settings.floatingExpandSide == "LEFT",
                            sizePercent = handleSizePercent,
                            theme = handleTheme,
                        )
                    }
                }
                }
                }
                }

        try {
            windowManager.addView(composeView, params)
            composeView?.requestFocus()
            isViewAttached = true
        } catch (e: Exception) {
            Log.e(TAG, "UI Init Failed", e)
            isViewAttached = false
            requestClose()
        }
    }

    /** 把窗口 resize 到指定尺寸（px）。闪念胶囊靠它切换收起把手 / 展开面板。 */
    private fun resizeWindow(widthPx: Int, heightPx: Int, focusable: Boolean, yOffsetPx: Int = 0) {
        val view = composeView ?: return
        val params = windowLayoutParams ?: return
        if (!isViewAttached || !view.isAttachedToWindow) return
        try {
            val dockLeft = settingsQueryApi.settings.value.floatingExpandSide == "LEFT"
            params.width = widthPx
            params.height = heightPx
            // 纵向固定 CENTER_VERTICAL：y 是相对垂直中心的偏移，与 initUI、moveHandleVertically、
            // handleYOffsetPxClamped 同一套语义。改用 TOP 会让同一个 y 变成距屏幕顶部的距离，把手跳位。
            // 展开态与媒体态必须传 y=0，否则收起态遗留的偏移会把全高窗口推出屏幕。
            params.gravity = if (mediaRequest.value != null) {
                android.view.Gravity.CENTER
            } else {
                (if (dockLeft) android.view.Gravity.START else android.view.Gravity.END) or
                    android.view.Gravity.CENTER_VERTICAL
            }
            params.x = 0
            params.y = yOffsetPx
            params.flags = if (focusable) {
                baseWindowFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                baseWindowFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "resizeWindow failed", e)
        }
    }

    /** 拖动把手调整其在屏幕停靠缘的纵向位置（居中锚点 + 偏移）。 */
    private fun moveHandleVertically(deltaY: Float) {
        val view = composeView ?: return
        val params = windowLayoutParams ?: return
        if (!isViewAttached) return
        try {
            params.y = (params.y + deltaY.toInt()).coerceIn(-maxAbsHandleY(), maxAbsHandleY())
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "moveHandleVertically failed", e)
        }
    }

    /** 拖动结束：把当前纵向偏移落盘（dp），下次启动恢复。 */
    private fun persistHandleYOffset() {
        val params = windowLayoutParams ?: return
        val density = resources.displayMetrics.density
        if (density <= 0f) return
        val offsetDp = (params.y / density).toInt()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(HANDLE_Y_OFFSET_KEY, offsetDp)
            .apply()
    }

    /** 读拖存偏移（dp → px）并 clamp 到窗口完整留在屏内。 */
    private fun handleYOffsetPxClamped(winHeightPx: Int): Int {
        val savedDp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(HANDLE_Y_OFFSET_KEY, 0)
        if (savedDp == 0) return 0
        val maxAbsY = maxAbsHandleY(winHeightPx)
        return (savedDp * resources.displayMetrics.density).toInt().coerceIn(-maxAbsY, maxAbsY)
    }

    /** 偏移上限：保证窗口完整留在屏内。 */
    private fun maxAbsHandleY(winHeightPx: Int = (OverlayConstants.HANDLE_HEIGHT_DP * resources.displayMetrics.density).toInt()): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        return ((screenHeight - winHeightPx) / 2).coerceAtLeast(0)
    }

    private fun hideFloatingWindow() {
        val view = composeView ?: return
        val params = windowLayoutParams ?: return
        if (!isViewAttached || !view.isAttachedToWindow) {
            isViewAttached = false
            return
        }

        try {
            params.flags = baseWindowFlags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(view, params)
            view.visibility = View.GONE
        } catch (e: Exception) {
            Log.w(TAG, "hideFloatingWindow failed", e)
        }
    }

    private fun detachFloatingWindowForVoice() {
        val view = composeView ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return
        try {
            windowManager.removeView(view)
            isViewAttached = false
        } catch (e: IllegalArgumentException) {
            isViewAttached = false
        } catch (e: Exception) {
            Log.w(TAG, "detachFloatingWindowForVoice failed", e)
        }
    }

    private fun showFloatingWindow() {
        val view = composeView ?: return
        val params = windowLayoutParams ?: return
        if (!permissionCenter.canDrawOverlays(this)) return

        if (!isViewAttached && !view.isAttachedToWindow) {
            try {
                windowManager.addView(view, params)
                isViewAttached = true
            } catch (e: IllegalStateException) {
                Log.w(TAG, "showFloatingWindow addView already attached", e)
                isViewAttached = true
            } catch (e: Exception) {
                Log.e(TAG, "showFloatingWindow addView failed", e)
                isViewAttached = false
                return
            }
        } else {
            isViewAttached = true
        }

        try {
            view.visibility = if (screenOn && !awaitingScreenRestore && !closingOnScreenOff) View.VISIBLE else View.GONE
            view.requestLayout()
            params.flags = baseWindowFlags
            windowManager.updateViewLayout(view, params)
            view.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "showFloatingWindow update failed", e)
        }
    }

    private fun finishPendingImagePick() {
        val callback = pendingImagePickCompletion
        pendingImagePickCompletion = null
        pendingImagePickAsQuickMemo = false
        callback?.invoke()
    }

    private fun startVoiceCapture() {
        // 上一条还在保存/转写时不重叠新录音，避免把转写反馈与状态冲掉。
        if (voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.SAVING ||
            voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.TRANSCRIBING
        ) {
            Toast.makeText(applicationContext, "语音正在处理", Toast.LENGTH_SHORT).show()
            return
        }
        voiceConfirmJob?.cancel()
        voiceStartJob?.cancel()
        voiceAutoStopJob?.cancel()
        voiceAutoStopJob = null
        voiceStopRequested = false
        if (!settingsQueryApi.settings.value.voiceInputEnabled) {
            voiceCaptureState.value = QuickMemoVoiceCaptureState(
                status = QuickMemoVoiceCaptureStatus.ERROR,
                message = "请先开启闪记"
            )
            Toast.makeText(applicationContext, "请先开启闪记", Toast.LENGTH_SHORT).show()
            serviceScope.launch {
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
            }
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceCaptureState.value = QuickMemoVoiceCaptureState(
                status = QuickMemoVoiceCaptureStatus.ERROR,
                message = "需要麦克风权限"
            )
            Toast.makeText(applicationContext, "需要麦克风权限", Toast.LENGTH_SHORT).show()
            requestRecordAudioPermission()
            serviceScope.launch {
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
            }
            return
        }
        try {
            startVoiceForeground()
        } catch (e: Exception) {
            Log.e(TAG, "启动闪记前台录音失败", e)
            val message = if (e is SecurityException) "系统限制后台录音，请重试" else "录音失败"
            voiceCaptureState.value = QuickMemoVoiceCaptureState(
                status = QuickMemoVoiceCaptureStatus.ERROR,
                message = message
            )
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            showFloatingWindow()
            serviceScope.launch {
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
            }
            return
        }
        voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.RECORDING)
        showFloatingWindow()
        voiceStartJob = serviceScope.launch {
            try {
                withContext(Dispatchers.IO) { audioRecorder.start() }
                if (voiceStopRequested) {
                    voiceStopRequested = false
                    stopVoiceCapture()
                    return@launch
                }
                performServiceHaptic()
                voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.RECORDING)
                scheduleVoiceAutoStop()
            } catch (e: Exception) {
                Log.e(TAG, "启动闪记录音失败", e)
                stopVoiceForeground()
                withContext(Dispatchers.IO) { audioRecorder.stopAndDiscard() }
                voiceCaptureState.value = QuickMemoVoiceCaptureState(
                    status = QuickMemoVoiceCaptureStatus.ERROR,
                    message = if (e is SecurityException) "系统限制后台录音，请重试" else "录音失败"
                )
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
            }
        }
    }

    private fun stopVoiceCapture() {
        voiceAutoStopJob?.cancel()
        voiceAutoStopJob = null
        if (voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.RECORDING && !audioRecorder.isRecording) {
            voiceStopRequested = true
            return
        }
        if (voiceCaptureState.value.status != QuickMemoVoiceCaptureStatus.RECORDING) return
        voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.SAVING)
        serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { audioRecorder.stop() }
                stopVoiceForeground()
                performServiceHaptic()
                if (result == null || result.durationMs < QuickMemoAudioRecorder.MIN_RECORDING_MS) {
                    result?.path?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
                    voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.TOO_SHORT)
                    if (closeAfterVoiceSave) {
                        closeAfterVoiceSave = false
                        delay(400)
                        finishClose()
                        return@launch
                    }
                    delay(1100)
                    voiceCaptureState.value = QuickMemoVoiceCaptureState()
                    return@launch
                }
                saveVoiceCapture(
                    audioPath = result.path,
                    durationMs = result.durationMs,
                    asTodo = false,
                    closeAfterSave = closeAfterVoiceSave
                )
                closeAfterVoiceSave = false
            } catch (e: Exception) {
                Log.e(TAG, "停止闪记录音失败", e)
                stopVoiceForeground()
                withContext(Dispatchers.IO) { audioRecorder.stopAndDiscard() }
                voiceCaptureState.value = QuickMemoVoiceCaptureState(
                    status = QuickMemoVoiceCaptureStatus.ERROR,
                    message = "录音失败"
                )
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
            }
        }
    }

    private fun scheduleVoiceAutoStop() {
        voiceAutoStopJob?.cancel()
        val durationMs = QuickMemoAutoStopPolicy.durationMillis(settingsQueryApi.settings.value) ?: return
        voiceAutoStopJob = serviceScope.launch {
            delay(durationMs)
            voiceAutoStopJob = null
            stopVoiceCapture()
        }
    }

    private fun handleExternalVoiceRecording() {
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        voiceStartJob?.cancel()
        voiceStopRequested = false
        voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.RECORDING)
    }

    private fun handleExternalVoiceCompleted(path: String?, durationMs: Long) {
        if (path.isNullOrBlank()) {
            handleExternalVoiceError("录音失败")
            return
        }
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        voiceStartJob?.cancel()
        voiceStopRequested = false
        showFloatingWindow()
        saveVoiceCapture(
            audioPath = path,
            durationMs = durationMs,
            asTodo = false
        )
    }

    private fun handleExternalVoiceTooShort() {
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        voiceStartJob?.cancel()
        voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.TOO_SHORT)
        showFloatingWindow()
        serviceScope.launch {
            delay(1100)
            voiceCaptureState.value = QuickMemoVoiceCaptureState()
        }
    }

    private fun handleExternalVoiceError(message: String) {
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        voiceStartJob?.cancel()
        voiceCaptureState.value = QuickMemoVoiceCaptureState(
            status = QuickMemoVoiceCaptureStatus.ERROR,
            message = message.ifBlank { "录音失败" }
        )
        showFloatingWindow()
        serviceScope.launch {
            delay(1400)
            voiceCaptureState.value = QuickMemoVoiceCaptureState()
        }
    }

    private fun startVoiceForeground() {
        if (voiceForegroundActive) return
        val notification = buildVoiceCaptureNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationIds.QUICK_MEMO_VOICE_CAPTURE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NotificationIds.QUICK_MEMO_VOICE_CAPTURE, notification)
        }
        voiceForegroundActive = true
    }

    private fun stopVoiceForeground() {
        if (!voiceForegroundActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        voiceForegroundActive = false
    }

    private fun buildVoiceCaptureNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            NotificationIds.QUICK_MEMO_VOICE_CAPTURE,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val content = SystemNormalDisplay.voiceCaptureRunning()
        return NotificationCompat.Builder(this, App.CHANNEL_ID_VOICE_CAPTURE)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun requestRecordAudioPermission() {
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_REQUEST_RECORD_AUDIO_PERMISSION, true)
            })
        } catch (e: Exception) {
            Log.w(TAG, "无法打开麦克风权限引导", e)
        }
    }

    private fun confirmVoiceCapture(asTodo: Boolean, closeAfterSave: Boolean = false) {
        val current = voiceCaptureState.value
        if (current.status != QuickMemoVoiceCaptureStatus.CONFIRMING) return
        val path = current.tempAudioPath?.takeIf { it.isNotBlank() } ?: return
        saveVoiceCapture(
            audioPath = path,
            durationMs = current.durationMs,
            asTodo = asTodo,
            closeAfterSave = closeAfterSave
        )
    }

    private fun saveVoiceCapture(
        audioPath: String,
        durationMs: Long,
        asTodo: Boolean = false,
        closeAfterSave: Boolean = false
    ) {
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        serviceScope.launch {
            try {
                voiceCaptureState.value = QuickMemoVoiceCaptureState(
                    status = QuickMemoVoiceCaptureStatus.SAVING,
                    tempAudioPath = audioPath,
                    durationMs = durationMs
                )
                val settings = settingsQueryApi.settings.value
                val memoId = withContext(Dispatchers.IO) {
                    quickMemoCenter.createVoiceMemo(
                        audioPath = audioPath,
                        durationMs = durationMs,
                        asTodo = asTodo,
                        autoPinOnTranscriptionSuccess = settings.voiceQuickMemoAutoPinEnabled
                    )
                }
                if (!closeAfterSave) showRecentVoiceMemo(memoId)
                if (closeAfterSave) {
                    // 关窗路径无需再留在转写反馈态：转写在独立前台服务继续。
                    voiceCaptureState.value = QuickMemoVoiceCaptureState()
                    finishClose()
                } else {
                    voiceCaptureState.value = QuickMemoVoiceCaptureState(
                        status = QuickMemoVoiceCaptureStatus.TRANSCRIBING,
                        durationMs = durationMs,
                        memoId = memoId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存语音闪记失败", e)
                withContext(Dispatchers.IO) { File(audioPath).delete() }
                voiceCaptureState.value = QuickMemoVoiceCaptureState(
                    status = QuickMemoVoiceCaptureStatus.ERROR,
                    message = "保存失败"
                )
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
                if (closeAfterSave) finishClose()
            }
        }
    }

    private fun postVoiceTranscriptionCapsule(memo: QuickMemoEntity) {
        val memoId = memo.id
        val title = memo.bodyText.trim()
        when {
            memoId == null -> {
                Toast.makeText(applicationContext, "语音条还未保存完成", Toast.LENGTH_SHORT).show()
            }
            memo.transcriptionStatus != QuickMemoTranscriptionStatus.SUCCESS || title.isBlank() -> {
                Toast.makeText(applicationContext, "语音转写完成后才能挂到实况通知", Toast.LENGTH_SHORT).show()
            }
            !settingsQueryApi.settings.value.isLiveCapsuleEnabled -> {
                Toast.makeText(applicationContext, "请先开启实况通知", Toast.LENGTH_SHORT).show()
            }
            else -> {
                serviceScope.launch {
                    val pinned = runCatching { quickMemoCenter.pinQuickMemo(memoId) }
                    if (pinned.getOrDefault(false)) {
                        clearRecentVoiceMemo(memoId)
                    } else {
                        Toast.makeText(applicationContext, pinned.exceptionOrNull()?.message ?: "挂起失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showRecentVoiceMemo(memoId: Long) {
        recentVoiceMemoJob?.cancel()
        recentVoiceMemoId.value = memoId
        recentVoiceMemoJob = serviceScope.launch {
            delay(RECENT_VOICE_MEMO_HOLD_MS)
            clearRecentVoiceMemo(memoId)
        }
    }

    private fun clearRecentVoiceMemo(memoId: Long? = null) {
        if (memoId != null && recentVoiceMemoId.value != memoId) return
        recentVoiceMemoJob?.cancel()
        recentVoiceMemoJob = null
        recentVoiceMemoId.value = null
    }

    private fun performServiceHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "闪记录音震动反馈失败", e)
        }
    }

    private fun startScreenshotAnalysisFlow(asQuickMemo: Boolean, onComplete: () -> Unit) {
        if (pendingImagePickCompletion != null) return
        pendingImagePickCompletion = onComplete
        pendingImagePickAsQuickMemo = asQuickMemo
        hideFloatingWindow()

        serviceScope.launch {
            if (asQuickMemo) {
                launchImagePickerFallback()
                return@launch
            }
            var accessibilityService = TextAccessibilityService.instance
            if (accessibilityService == null) {
                com.antgskds.calendarassistant.shared.util.AccessibilityGuardian.restoreIfNeeded(this@FloatingScheduleService)
                accessibilityService = TextAccessibilityService.instance
            }

            if (accessibilityService != null) {
                Log.d(TAG, "无障碍服务可用，开始截屏识屏")
                val delayMs = MySettings.normalizeScreenshotDelayMs(settingsQueryApi.settings.value.screenshotDelayMs)
                accessibilityService.startAnalysis(delayMs.milliseconds)
                finishPendingImagePick()
                requestClose()
                return@launch
            } else {
                Log.w(TAG, "无障碍服务不可用，回退到图片选择")
                Toast.makeText(applicationContext, "未开启无障碍服务，使用图片选择", Toast.LENGTH_SHORT).show()
                launchImagePickerFallback()
            }
        }
    }

    private fun launchImagePickerFallback() {
        try {
            val intent = Intent(this@FloatingScheduleService, ImagePickHandleActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start image picker", e)
            finishPendingImagePick()
        }
    }

    private fun handlePickedImage(uri: Uri) {
        serviceScope.launch {
            if (pendingImagePickAsQuickMemo) {
                handlePickedQuickMemoImage(uri)
                return@launch
            }
            val settings = settingsQueryApi.settings.value
            if (!settings.isRecognitionConfigReady()) {
                Toast.makeText(applicationContext, settings.recognitionConfigMissingMessage(), Toast.LENGTH_SHORT).show()
                finishPendingImagePick()
                return@launch
            }

            val imageFile = ImageImportUtils.createImportedImageFile(this@FloatingScheduleService)
            val copied = withContext(Dispatchers.IO) {
                ImageImportUtils.copyUriToFile(this@FloatingScheduleService, uri, imageFile)
            }
            if (!copied) {
                Toast.makeText(applicationContext, "图片读取失败", Toast.LENGTH_SHORT).show()
                finishPendingImagePick()
                return@launch
            }

            val bitmap = withContext(Dispatchers.IO) {
                ImageImportUtils.decodeSampledBitmapFromFile(imageFile)
            }
            if (bitmap == null) {
                Toast.makeText(applicationContext, "图片解码失败", Toast.LENGTH_SHORT).show()
                finishPendingImagePick()
                return@launch
            }

            val traceId = EventIdentity.newTraceId("floating_image")
            val result = withContext(Dispatchers.IO) {
                app.recognitionCenter.analyzeImage(
                    bitmap = bitmap,
                    settings = settings,
                    context = applicationContext,
                    sourceType = RECOGNITION_SOURCE_TYPE,
                    sourceId = RECOGNITION_SOURCE_ID,
                    sourceImagePath = imageFile.absolutePath,
                    ingestRequested = true,
                    traceId = traceId
                )
            }
            bitmap.recycle()
            when (result) {
                is AnalysisResult.Success -> Toast.makeText(applicationContext, result.feedback(), Toast.LENGTH_LONG).show()
                is AnalysisResult.Empty -> Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
                is AnalysisResult.Failure -> Unit
            }
            finishPendingImagePick()
        }
    }

    private suspend fun handlePickedQuickMemoImage(uri: Uri) {
        val imageFile = ImageImportUtils.createQuickMemoImageFile(this@FloatingScheduleService)
        val copied = withContext(Dispatchers.IO) {
            ImageImportUtils.copyUriToFile(this@FloatingScheduleService, uri, imageFile)
        }
        if (!copied) {
            Toast.makeText(applicationContext, "图片读取失败", Toast.LENGTH_SHORT).show()
            finishPendingImagePick()
            return
        }
        withContext(Dispatchers.IO) {
            quickMemoCenter.createImageMemo(imageFile.absolutePath)
        }
        Toast.makeText(applicationContext, "已保存到闪记", Toast.LENGTH_SHORT).show()
        finishPendingImagePick()
    }

    // ... 下面的业务逻辑部分保持不变 ...

    private fun handleManualInput(
        text: String,
        isQuickMemo: Boolean = false,
        sourceImagePath: String? = null,
        onComplete: () -> Unit = {}
    ) {
        if (text.isBlank()) {
            onComplete()
            return
        }
        serviceScope.launch {
            try {
                val settings = settingsQueryApi.settings.value
                if (isQuickMemo) {
                    val clean = text.trim()
                    val memoId = withContext(Dispatchers.IO) {
                        quickMemoCenter.createTextMemo(clean)
                    }
                    maybePinFloatingTextQuickMemo(memoId, settings.isLiveCapsuleEnabled, settings.floatingTextQuickMemoAutoPinEnabled)
                } else {
                    val traceId = EventIdentity.newTraceId("floating")
                    val result = withContext(Dispatchers.IO) {
                        app.recognitionCenter.analyzeTextEvents(
                            text = text,
                            settings = settings,
                            context = applicationContext,
                            sourceType = RECOGNITION_SOURCE_TYPE,
                            sourceId = RECOGNITION_SOURCE_ID,
                            ingestRequested = true,
                            traceId = traceId
                        )
                    }
                    when (result) {
                        is AnalysisResult.Success -> Toast.makeText(applicationContext, result.feedback(), Toast.LENGTH_LONG).show()
                        is AnalysisResult.Empty -> Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
                        is AnalysisResult.Failure -> Unit
                    }
                }
                // 收起输入法
                hideInputMethod()
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse manual input", e)
                hideInputMethod()
                Toast.makeText(applicationContext, "分析失败：${e.message ?: "unknown"}", Toast.LENGTH_SHORT).show()
                onComplete()
            }
        }
    }

    private suspend fun maybePinFloatingTextQuickMemo(
        memoId: Long,
        liveCapsuleEnabled: Boolean,
        autoPinEnabled: Boolean
    ) {
        if (!autoPinEnabled) return
        if (!liveCapsuleEnabled) {
            Toast.makeText(applicationContext, "已保存，实况通知未开启，未挂起", Toast.LENGTH_SHORT).show()
            return
        }
        val pinned = runCatching { quickMemoCenter.pinQuickMemo(memoId) }
        if (pinned.getOrDefault(false)) {
            Toast.makeText(applicationContext, "已保存并挂到实况通知", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(applicationContext, pinned.exceptionOrNull()?.message ?: "已保存，挂起失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideInputMethod() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            composeView?.let { view ->
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide input method", e)
        }
    }

    private fun openMediaSourceInApp(request: FloatingMediaRequest) {
        if (mediaFallbackInProgress) return
        mediaFallbackInProgress = true
        Toast.makeText(applicationContext, "图片无法打开", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            request.eventId?.let { putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, it) }
            request.quickMemoId?.let { putExtra(MainActivity.EXTRA_OPEN_QUICK_MEMO_ID, it) }
        })
        // 已经跳进 App 查看源内容，悬浮窗回到把手即可，不必连服务一起杀。
        dismissMediaCard()
    }

    /**
     * 关闭当前媒体卡片并回到把手。媒体卡片是 intent 拉起的独立展示，不是悬浮窗主体：
     * 用户看完图片关掉它，应当回到收起态把手，而不是把整个悬浮窗服务杀掉。
     */
    private fun dismissMediaCard() {
        mediaRequest.value = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendBroadcast(Intent(ACTION_FLOATING_SHOWN))
        val settings = settingsQueryApi.settings.value
        val action = intent?.action
        // 主动唤起（action 为空 = 音量键/磁贴/快捷方式）直接展开面板；
        // 媒体卡片、录音、图片选择等带 action 的入口保持各自视图。
        expandOnLaunch = action == null
        initialHasMediaOrVoice = action != null
        val requiresEnabledFloatingFeature = action == null || action == ACTION_START_VOICE_CAPTURE
        if (!settings.isFloatingWindowEnabled && !settings.voiceInputEnabled && requiresEnabledFloatingFeature) {
            requestClose()
            return START_NOT_STICKY
        }
        when (action) {
            ACTION_REQUEST_CLOSE -> {
                requestClose()
                return START_NOT_STICKY
            }
            ACTION_SHOW_PICKUP_QR_CARD -> {
                val eventId = intent.getLongExtra(EXTRA_PICKUP_EVENT_ID, -1L).takeIf { it > 0L }
                if (eventId == null) return START_NOT_STICKY
                mediaFallbackInProgress = false
                mediaRequest.value = FloatingMediaRequest(
                    eventId = eventId,
                    imagePaths = intent.getStringArrayListExtra(EXTRA_MEDIA_IMAGE_PATHS).orEmpty()
                )
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                showFloatingWindow()
                return START_NOT_STICKY
            }
            ACTION_SHOW_IMAGE_PIN -> {
                if (!com.antgskds.calendarassistant.feature.imagepin.ImagePinPolicy.enabled(settings)) return START_NOT_STICKY
                val id = intent.getLongExtra("image_pin_id", 0).takeIf { it > 0 } ?: return START_NOT_STICKY
                val paths = intent.getStringArrayListExtra(EXTRA_MEDIA_IMAGE_PATHS).orEmpty().filter { it.isNotBlank() }
                if (paths.isEmpty()) return START_NOT_STICKY
                mediaFallbackInProgress = false
                mediaRequest.value = FloatingMediaRequest(imagePinId = id, imagePaths = paths, title = "图片挂起")
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                showFloatingWindow()
                return START_NOT_STICKY
            }
            ACTION_SHOW_QUICK_MEMO_MEDIA_CARD -> {
                val memoId = intent.getLongExtra(EXTRA_QUICK_MEMO_ID, -1L).takeIf { it > 0L }
                val imagePath = intent.getStringExtra(EXTRA_MEDIA_IMAGE_PATH)?.takeIf { it.isNotBlank() }
                if (memoId == null || imagePath == null) return START_NOT_STICKY
                mediaFallbackInProgress = false
                mediaRequest.value = FloatingMediaRequest(
                    quickMemoId = memoId,
                    imagePaths = listOf(imagePath),
                    title = intent.getStringExtra(EXTRA_MEDIA_TITLE).orEmpty()
                )
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                showFloatingWindow()
                return START_NOT_STICKY
            }
            ACTION_IMAGE_PICKED -> {
                mediaRequest.value = null
                showFloatingWindow()
                val uriStr = intent.getStringExtra(EXTRA_IMAGE_URI)
                if (uriStr.isNullOrBlank()) {
                    finishPendingImagePick()
                    return START_NOT_STICKY
                }
                handlePickedImage(Uri.parse(uriStr))
                return START_NOT_STICKY
            }
            ACTION_IMAGE_PICK_CANCELLED -> {
                mediaRequest.value = null
                showFloatingWindow()
                finishPendingImagePick()
                return START_NOT_STICKY
            }
            ACTION_PREPARE_VOICE_CAPTURE -> {
                mediaRequest.value = null
                detachFloatingWindowForVoice()
                return START_NOT_STICKY
            }
            ACTION_VOICE_CAPTURE_RECORDING -> {
                mediaRequest.value = null
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleExternalVoiceRecording()
                return START_NOT_STICKY
            }
            ACTION_VOICE_CAPTURE_COMPLETED -> {
                mediaRequest.value = null
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleExternalVoiceCompleted(
                    path = intent.getStringExtra(EXTRA_VOICE_AUDIO_PATH),
                    durationMs = intent.getLongExtra(EXTRA_VOICE_DURATION_MS, 0L)
                )
                return START_NOT_STICKY
            }
            ACTION_VOICE_CAPTURE_TOO_SHORT -> {
                mediaRequest.value = null
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleExternalVoiceTooShort()
                return START_NOT_STICKY
            }
            ACTION_VOICE_CAPTURE_ERROR -> {
                mediaRequest.value = null
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleExternalVoiceError(intent.getStringExtra(EXTRA_VOICE_ERROR_MESSAGE).orEmpty())
                return START_NOT_STICKY
            }
            ACTION_START_VOICE_CAPTURE -> {
                mediaRequest.value = null
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                startVoiceCapture()
                return START_NOT_STICKY
            }
            ACTION_STOP_VOICE_CAPTURE -> {
                mediaRequest.value = null
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                stopVoiceCapture()
                return START_NOT_STICKY
            }
        }

        mediaRequest.value = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        showFloatingWindow()
        capsuleExpandRequest.value++
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        isShowing = false
        sendBroadcast(Intent(ACTION_FLOATING_HIDDEN))
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        audioPlaybackCenter.stop()
        audioRecorder.stopAndDiscard()
        voiceConfirmJob?.cancel()
        recentVoiceMemoJob?.cancel()
        voiceStartJob?.cancel()
        voiceAutoStopJob?.cancel()
        recognitionFailedSubscriptionJob?.cancel()
        recognitionFailedSubscriptionJob = null
        ingestSucceededSubscriptionJob?.cancel()
        ingestSucceededSubscriptionJob = null
        ingestFailedSubscriptionJob?.cancel()
        ingestFailedSubscriptionJob = null
        composeView?.let { view ->
            if (view.isAttachedToWindow) {
                try { windowManager.removeView(view) } catch (e: Exception) {}
            }
        }
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun requestClose() {
        if (voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.CONFIRMING) {
            confirmVoiceCapture(asTodo = false, closeAfterSave = true)
            return
        }
        if (voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.SAVING ||
            voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.TRANSCRIBING
        ) {
            Toast.makeText(applicationContext, "语音正在处理", Toast.LENGTH_SHORT).show()
            return
        }
        if (voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.RECORDING) {
            // 关闭浮窗不能丢掉正在录制的内容：先停录并落库（转写在独立前台服务里继续），
            // 保存完成后再真正关窗。与声物记“停录即保存”一致。
            closeAfterVoiceSave = true
            stopVoiceCapture()
            return
        }
        finishClose()
    }

    private fun finishClose() {
        sendBroadcast(Intent(ACTION_FLOATING_HIDDEN))
        stopSelf()
    }

    /**
     * 卡片左侧归档勾选框：勾上 = 归档，取消 = 恢复。
     * 只有日程条目带勾选框（key 前缀 schedule:）。
     */
    private fun handleCapsuleCheck(
        item: com.antgskds.calendarassistant.platform.floating.capsule.CapsuleItem,
        checked: Boolean
    ) {
        if (!item.key.startsWith("schedule:")) return
        val stableKey = item.key.removePrefix("schedule:")
        serviceScope.launch {
            runCatching {
                val target = scheduleCenter.applyPendingStateOverrides(
                    com.antgskds.calendarassistant.feature.schedule.domain.ScheduleDisplayHelper.buildDisplayItems(
                        scheduleCenter.events.value.filter { it.archivedAt == null },
                        java.time.LocalDate.now().minusDays(7),
                        java.time.LocalDate.now().plusDays(7),
                    )
                ).firstOrNull { it.stableKey == stableKey }

                // 取消归档时事件已不在活跃列表里，退回全量按 id 查。
                // stableKey 形如 single:<id> / recurring:<parentId>:<ts>
                val resolved: com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem.ActionTarget? =
                    target?.action ?: run {
                        val parts = stableKey.split(":")
                        when {
                            parts.size == 2 && parts[0] == "single" ->
                                parts[1].toLongOrNull()?.let {
                                    com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem.ActionTarget.Single(it)
                                }
                            parts.size == 3 && parts[0] == "recurring" -> {
                                val parentId = parts[1].toLongOrNull()
                                val ts = parts[2].toLongOrNull()
                                if (parentId != null && ts != null) {
                                    com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem.ActionTarget.RecurringOccurrence(parentId, ts)
                                } else null
                            }
                            else -> null
                        }
                    }

                when (val action = resolved) {
                    is com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem.ActionTarget.Single -> {
                        if (checked) {
                            scheduleCenter.archiveItem(action)
                        } else {
                            scheduleCenter.restoreEvent(action.eventId)
                        }
                    }
                    is com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                        if (checked) {
                            scheduleCenter.archiveItem(action)
                        } else {
                            // 取消归档：归档时已物化成子事件，按 parentId + startTS 反查回来
                            val childId = scheduleCenter.archivedEvents.value
                                .firstOrNull { it.parentId == action.parentId && it.startTS == action.occurrenceTs }
                                ?.id
                            if (childId != null) {
                                scheduleCenter.restoreEvent(childId)
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(applicationContext, "找不到该日程实例", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    null -> withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "找不到该日程", Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                Log.e(TAG, "切换日程归档失败 key=$stableKey", it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "操作失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 当前事件色板解析成 Compose Color 列表。 */
    private fun capsulePalette(): List<androidx.compose.ui.graphics.Color> {
        val hexes = com.antgskds.calendarassistant.feature.settings.data.model
            .sanitizeEventColorPaletteHex(settingsQueryApi.settings.value.eventColorPaletteHex)
        return hexes.map { hex ->
            androidx.compose.ui.graphics.Color(
                com.antgskds.calendarassistant.feature.settings.data.model.eventColorHexToArgb(hex)
            )
        }
    }

    /** 事件颜色在色板里的下标；未命中返回 -1（卡片用默认蓝）。 */
    private fun paletteIndexOf(eventColor: Int): Int {
        if (eventColor == 0) return -1
        val hexes = com.antgskds.calendarassistant.feature.settings.data.model
            .sanitizeEventColorPaletteHex(settingsQueryApi.settings.value.eventColorPaletteHex)
        return hexes.indexOfFirst { hex ->
            com.antgskds.calendarassistant.feature.settings.data.model.eventColorHexToArgb(hex) == eventColor
        }
    }

    /** 展开态调色板换色：写回日程的 color 字段。 */
    private fun handleCapsulePaletteSelect(
        item: com.antgskds.calendarassistant.platform.floating.capsule.CapsuleItem,
        index: Int
    ) {
        if (!item.key.startsWith("schedule:")) return
        val stableKey = item.key.removePrefix("schedule:")
        val hexes = com.antgskds.calendarassistant.feature.settings.data.model
            .sanitizeEventColorPaletteHex(settingsQueryApi.settings.value.eventColorPaletteHex)
        val hex = hexes.getOrNull(index) ?: return
        val colorInt = com.antgskds.calendarassistant.feature.settings.data.model
            .eventColorHexToArgb(hex)
        val eventId = stableKey.removePrefix("single:").toLongOrNull() ?: return

        serviceScope.launch {
            runCatching {
                val event = scheduleCenter.events.value.firstOrNull { it.id == eventId } ?: return@runCatching
                scheduleCenter.updateEvent(event.copy(color = colorInt))
            }.onFailure {
                Log.e(TAG, "胶囊换色失败 key=$stableKey", it)
            }
        }
    }

    /** 卡片操作分发：日程与闪记按 key 前缀区分。 */
    private fun handleCapsuleAction(
        item: com.antgskds.calendarassistant.platform.floating.capsule.CapsuleItem,
        actionId: String
    ) {
        when (actionId) {
            "close" -> requestClose()
            "delete" -> {
                if (item.key.startsWith("memo:")) {
                    item.key.removePrefix("memo:").toLongOrNull()?.let { memoId ->
                        serviceScope.launch {
                            runCatching { quickMemoCenter.archiveQuickMemo(memoId) }
                                .onFailure { Log.e(TAG, "归档闪记失败 memoId=$memoId", it) }
                        }
                    }
                }
            }
            "copy" -> copyCapsuleItemText(item.text)
            "alarm" -> createCapsuleAlarm(item.text)
            "done" -> {
                if (item.key.startsWith("schedule:")) {
                    val stableKey = item.key.removePrefix("schedule:")
                    // stableKey 形如 single:<id> / recurring:<parentId>:<ts>
                    val target = scheduleCenter.applyPendingStateOverrides(
                        com.antgskds.calendarassistant.feature.schedule.domain.ScheduleDisplayHelper.buildDisplayItems(
                            scheduleCenter.events.value.filter { it.archivedAt == null },
                            java.time.LocalDate.now().minusDays(7),
                            java.time.LocalDate.now().plusDays(7),
                        )
                    ).firstOrNull { it.stableKey == stableKey }
                    if (target != null) {
                        // completeItem 自身就是非 suspend 且内部起协程，不必再包一层
                        runCatching { scheduleCenter.completeItem(target.action) }
                            .onFailure { Log.e(TAG, "完成日程失败 key=$stableKey", it) }
                    }
                }
            }
        }
    }

    private fun copyCapsuleItemText(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("闪记", text))
            Toast.makeText(applicationContext, "已复制", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "复制闪记失败", e)
        }
    }

    /** 从卡片文字识别时间并直接建一条日程；识别不到就降级为复制。 */
    private fun createCapsuleAlarm(text: String) {
        val target = com.antgskds.calendarassistant.platform.floating.capsule.CapsuleTimeParser.parse(text)
        if (target == null) {
            copyCapsuleItemText(text)
            Toast.makeText(applicationContext, "没识别到时间，内容已复制", Toast.LENGTH_SHORT).show()
            return
        }
        createCapsuleAlarmAt(text, target)
    }

    /** 按用户选定（或识别出）的时间建日程。enableAlarm=false 则不设提醒。 */
    private fun createCapsuleAlarmAt(
        text: String,
        target: java.time.LocalDateTime,
        enableAlarm: Boolean = true,
    ) {
        val startTs = target.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        serviceScope.launch {
            runCatching {
                scheduleCenter.addEventFromPatch(
                    com.antgskds.calendarassistant.feature.schedule.application.model.EventPatch(
                        title = text.take(60),
                        startTS = startTs,
                        endTS = startTs + 30 * 60,
                        description = text,
                        // 0 = 开始时提醒；-1 = 不提醒
                        reminder1Minutes = if (enableAlarm) 0 else -1,
                    )
                )
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    val shown = "%02d:%02d".format(target.hour, target.minute)
                    Toast.makeText(applicationContext, "已添加到日历 $shown", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Log.e(TAG, "卡片闹钟建日程失败", it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "建日程失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 把日程与闪记统一成胶囊卡片条目：日程在上，闪记在下，各自保持原有顺序。 */
    private fun buildCapsuleItems(
        scheduleItems: List<ScheduleDisplayItem>,
        quickMemos: List<QuickMemoEntity>
    ): List<com.antgskds.calendarassistant.platform.floating.capsule.CapsuleItem> {
        val scheduleCards = scheduleItems.map { item ->
            com.antgskds.calendarassistant.platform.floating.capsule.CapsuleItem(
                key = "schedule:${item.stableKey}",
                text = item.title.ifBlank { "未命名日程" },
                timeText = formatCapsuleTime(item.startTS, item.endTS),
                // 事件未设颜色时 color 为 0（透明），回退到卡片默认蓝，避免卡片发灰。
                color = if (item.color == 0) OverlayConstants.DEFAULT_CARD_COLOR
                else androidx.compose.ui.graphics.Color(item.color),
                // 日程条目带归档勾选框（勾选即归档）；闪记条目不带。
                checkable = true,
                // 展开态调色板：取自设置的事件色板
                palette = capsulePalette(),
                paletteSelectedIndex = paletteIndexOf(item.color),
                footer = listOf(
                    com.antgskds.calendarassistant.platform.floating.capsule.CapsuleAction(
                        "done", "完成", Icons.Rounded.CheckCircle
                    ),
                    com.antgskds.calendarassistant.platform.floating.capsule.CapsuleAction(
                        "copy", "复制", Icons.Rounded.ContentCopy
                    ),
                    com.antgskds.calendarassistant.platform.floating.capsule.CapsuleAction(
                        "alarm", "闹钟", Icons.Rounded.Alarm
                    ),
                ),
            )
        }
        val memoCards = quickMemos.map { memo ->
            com.antgskds.calendarassistant.platform.floating.capsule.CapsuleItem(
                key = "memo:${memo.id}",
                text = memo.bodyText.ifBlank { memo.title.ifBlank { "空白闪记" } },
                timeText = null,
                archived = false,
                // 待办闪记：点卡片划线完成/取消完成
                todo = memo.isTodo,
                todoDone = memo.isTodoCompleted,
                footer = listOf(
                    com.antgskds.calendarassistant.platform.floating.capsule.CapsuleAction(
                        "copy", "复制", Icons.Rounded.ContentCopy
                    ),
                    com.antgskds.calendarassistant.platform.floating.capsule.CapsuleAction(
                        "alarm", "闹钟", Icons.Rounded.Alarm
                    ),
                    com.antgskds.calendarassistant.platform.floating.capsule.CapsuleAction(
                        "delete", "删除", Icons.Rounded.Delete,
                        destructive = true
                    ),
                ),
            )
        }
        return scheduleCards + memoCards
    }

    /** 卡片时间行：今天只显示时刻，其余带上日期。 */
    private fun formatCapsuleTime(startTs: Long, endTs: Long): String {
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.Instant.ofEpochSecond(startTs).atZone(zone)
        val end = java.time.Instant.ofEpochSecond(endTs).atZone(zone)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val datePrefix = if (start.toLocalDate() == java.time.LocalDate.now()) "" else
            start.format(java.time.format.DateTimeFormatter.ofPattern("M月d日 "))
        return "$datePrefix${start.format(formatter)} - ${end.format(formatter)}"
    }
}
