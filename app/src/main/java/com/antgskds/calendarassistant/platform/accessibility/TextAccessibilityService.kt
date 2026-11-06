package com.antgskds.calendarassistant.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.imagepin.ImagePinPolicy
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.RecognitionFailureMessageMapper
import com.antgskds.calendarassistant.feature.recognition.application.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.feature.recognition.application.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.shared.event.DomainEventType
import com.antgskds.calendarassistant.shared.event.EventIdentity
import com.antgskds.calendarassistant.shared.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.shared.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.shared.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.core.service.voice.VoiceCaptureHandleActivity
import com.antgskds.calendarassistant.platform.floating.QuickMemoVoiceCaptureService
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleActionSpec
import com.antgskds.calendarassistant.platform.floating.FloatingScheduleService
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.NormalNotificationContent
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.RecognitionNormalDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.SystemNormalDisplay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TextAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob() +
        CoroutineExceptionHandler { context, error ->
            Log.w(AccessibilityDiagnosticSnapshot.HEALTH_LOG_TAG,
                "task_failed task=${context[CoroutineName]?.name ?: "service"} error=${error.javaClass.simpleName}")
        })
    private var analysisJob: Job? = null
    private var imagePinJob: Job? = null
    private var lastEventAt: Long? = null
    private var lastEventType: Int? = null
    private var eventCount = 0L
    private var connectionCount = 0

    /** 只读取运行状态和监听配置；不读取 UI 树、事件正文或截图。 */
    internal fun diagnosticServiceState(): org.json.JSONObject {
        val now = android.os.SystemClock.elapsedRealtime()
        val data = org.json.JSONObject()
            .put("instanceId", System.identityHashCode(this))
            .put("instanceRegistered", instance === this)
            .put("connectionCount", connectionCount)
            .put("lastConnectedAt", lastConnectedAt).put("lastDisconnectedAt", lastDisconnectedAt)
            .put("scopeActive", serviceScope.isActive)
            .put("settingsCollectorActive", keyFilterSettingsJob?.isActive == true)
            .put("lastEventAgeMs", lastEventAt?.let { now - it } ?: org.json.JSONObject.NULL)
            .put("lastEventType", lastEventType ?: org.json.JSONObject.NULL)
            .put("eventCount", eventCount)
            .put("busy", isAnalyzing.get()).put("analysisActive", analysisJob?.isActive == true)
        runCatching {
            data.put("recognitionConfigReady", settingsQueryApi.settings.value.isRecognitionConfigReady())
                .put("configuration", AccessibilityDiagnosticSnapshot.serviceConfiguration(serviceInfo))
        }.onFailure { data.put("stateReadError", it.javaClass.simpleName) }
        return data
    }

    private fun logServiceHealth(reason: String) {
        Log.i(AccessibilityDiagnosticSnapshot.HEALTH_LOG_TAG,
            diagnosticServiceState().put("reason", reason).toString())
    }

    private fun observeEventHealth(event: AccessibilityEvent) {
        lastEventAt = android.os.SystemClock.elapsedRealtime()
        lastEventType = event.eventType
        eventCount++
    }

    // 用于处理音量键长按的 Job
    private var volumeLongPressJob: Job? = null
    // 标记是否已经触发了长按事件
    private var isLongPressTriggered = false
    private var isVoiceCaptureTriggered = false
    private var isInAppVoiceTriggered = false
    private var isVolumeUpPressed = false
    private var isVolumeDownPressed = false

    // 双击判定：第二次 DOWN 距首次 UP 小于该窗口则认作双击
    private var lastUpAt = 0L
    private var lastUpKeyCode = 0
    private var pendingSingleTapJob: Job? = null
    // 双击已触发动作，随后的抬起不再补调音量
    private var doubleTapJustFired = false
    // 双击占用整键时，按住连调音量改由应用代调（系统收不到 DOWN）
    private var repeatVolumeJob: Job? = null

    private val NOTIFICATION_ID_PROGRESS = 1001
    private val NOTIFICATION_ID_RESULT = 2002

    private val app by lazy { applicationContext as App }
    private val capsuleCenter by lazy { app.capsuleCenter }
    private val floatingCenter by lazy { app.floatingCenter }
    private val domainEventBus by lazy { app.domainEventBus }
    private val settingsQueryApi by lazy { app.settingsQueryApi }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var recognitionFailedSubscriptionJob: Job? = null
    private var ingestSucceededSubscriptionJob: Job? = null
    private var ingestFailedSubscriptionJob: Job? = null
    private var keyFilterSettingsJob: Job? = null
    private var baseAccessibilityFlags: Int? = null

    companion object {
        private const val TAG = "TextAccessibilityService"
        private const val RECOGNITION_LOG_TAG = "WillDoRecognition"
        private const val RECOGNITION_SOURCE_TYPE = "accessibility"
        private const val RECOGNITION_SOURCE_ID = "accessibility.screenshot"
        const val ACTION_CANCEL_ANALYSIS = "ACTION_CANCEL_ANALYSIS"
        const val ACTION_CLOSE_FLOATING = "com.antgskds.calendarassistant.ACTION_CLOSE_FLOATING"
        @Volatile var instance: TextAccessibilityService? = null
            private set
        @Volatile private var lastConnectedAt: Long = 0L
        @Volatile private var lastDisconnectedAt: Long = 0L
        private val isAnalyzing = AtomicBoolean(false)
        private const val LONG_PRESS_THRESHOLD = 400L
        private const val DOUBLE_TAP_WINDOW_MS = 300L
        private const val VOLUME_REPEAT_INITIAL_MS = 500L
        private const val VOLUME_REPEAT_INTERVAL_MS = 100L
        private const val ACTION_VOLUME_LONG_PRESS_NONE = 0
        private const val ACTION_VOLUME_LONG_PRESS_SCREENSHOT = 1
        private const val ACTION_VOLUME_LONG_PRESS_FLOATING = 2
        private const val ACTION_VOLUME_LONG_PRESS_VOICE = 3
        private const val ACTION_VOLUME_LONG_PRESS_QUICK_RECORD = 4
        private const val ACTION_VOLUME_LONG_PRESS_IN_APP_NOTE = 5
        private const val ACTION_VOLUME_LONG_PRESS_FLOATING_NOTE = 6
        private const val ACTION_VOLUME_LONG_PRESS_PTT_RECORD = 7

        fun isConnected(): Boolean = instance != null

        fun lastConnectedAt(): Long = lastConnectedAt

        fun lastDisconnectedAt(): Long = lastDisconnectedAt
    }

    private var launcherPackageName: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        observeEventHealth(event)
    }

    override fun onInterrupt() {
        logServiceHealth("interrupted")
        cancelCurrentAnalysis()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connectionCount++
        lastConnectedAt = System.currentTimeMillis()
        launcherPackageName = getLauncherPackageName()
        logServiceHealth("connected_before_configuration")
        refreshKeyEventFiltering()
        subscribeKeyFilterSettings()
        subscribeRecognitionFailedEvents()
        subscribeIngestEvents()
        logServiceHealth("connected_ready")
        Log.d(TAG, "无障碍服务已连接")
    }

    private fun subscribeKeyFilterSettings() {
        keyFilterSettingsJob?.cancel()
        keyFilterSettingsJob = serviceScope.launch(CoroutineName("settings_listener")) {
            settingsQueryApi.settings.collect {
                refreshKeyEventFiltering()
            }
        }
    }

    private fun refreshKeyEventFiltering() {
        // 系统连接抖动不能让设置 collect 永久退出，也不能阻断 unbind/destroy 的清理。
        runCatching {
            val info = serviceInfo
            if (info == null) {
                Log.w(AccessibilityDiagnosticSnapshot.HEALTH_LOG_TAG, "configuration_unavailable")
                return
            }
            val baseFlags = AccessibilityDiagnosticSnapshot.connectionBaseFlags(baseAccessibilityFlags, info.flags)
            baseAccessibilityFlags = baseFlags
            val shouldFilterKeys = shouldFilterVolumeUpKeys() || shouldFilterVolumeDownKeys() ||
                shouldFilterVolumeUpDoubleTap() || shouldFilterVolumeDownDoubleTap()
            val keyFlags = if (shouldFilterKeys) {
                baseFlags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            } else {
                cancelVolumeLongPressState()
                baseFlags
            }
            // 窗口能力固定启用；诊断开始和结束不再重新配置系统连接。
            val nextFlags = keyFlags

            if (info.flags != nextFlags) {
                logServiceHealth("configuration_before_update")
                info.flags = nextFlags
                setServiceInfo(info)
                logServiceHealth("configuration_after_update")
            }
        }.onFailure {
            Log.w(AccessibilityDiagnosticSnapshot.HEALTH_LOG_TAG,
                "configuration_update_failed error=${it.javaClass.simpleName}")
        }
    }

    private fun shouldFilterVolumeUpKeys(): Boolean {
        val settings = settingsQueryApi.settings.value
        return isLongPressActionEnabled(settings.volumeUpLongPressEnabled, settings.volumeUpLongPressAction, settings)
    }

    private fun shouldFilterVolumeDownKeys(): Boolean {
        val settings = settingsQueryApi.settings.value
        return isLongPressActionEnabled(settings.volumeDownLongPressEnabled, settings.volumeDownLongPressAction, settings)
    }

    private fun shouldFilterVolumeUpDoubleTap(): Boolean {
        val settings = settingsQueryApi.settings.value
        return isLongPressActionEnabled(settings.volumeUpDoubleTapEnabled, settings.volumeUpDoubleTapAction, settings)
    }

    private fun shouldFilterVolumeDownDoubleTap(): Boolean {
        val settings = settingsQueryApi.settings.value
        return isLongPressActionEnabled(settings.volumeDownDoubleTapEnabled, settings.volumeDownDoubleTapAction, settings)
    }

    /** 双击占用整键后，按住连调改由应用代调（系统收不到 DOWN 事件）。 */
    private fun startVolumeRepeat(isVolumeUp: Boolean) {
        repeatVolumeJob?.cancel()
        repeatVolumeJob = serviceScope.launch {
            delay(VOLUME_REPEAT_INITIAL_MS)
            while (true) {
                try {
                    audioManager.adjustSuggestedStreamVolume(
                        if (isVolumeUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                        AudioManager.FLAG_SHOW_UI
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "代调音量失败", e)
                    break
                }
                delay(VOLUME_REPEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopVolumeRepeat() {
        repeatVolumeJob?.cancel()
        repeatVolumeJob = null
    }

    private fun isLongPressActionEnabled(
        enabled: Boolean,
        action: Int,
        settings: MySettings
    ): Boolean {
        if (!enabled) return false
        return when (action.coerceIn(0, 7)) {
            ACTION_VOLUME_LONG_PRESS_NONE -> false
            ACTION_VOLUME_LONG_PRESS_SCREENSHOT -> true
            ACTION_VOLUME_LONG_PRESS_FLOATING -> settings.isFloatingWindowEnabled || settings.voiceInputEnabled
            ACTION_VOLUME_LONG_PRESS_VOICE -> settings.voiceInputEnabled
            ACTION_VOLUME_LONG_PRESS_QUICK_RECORD -> settings.voiceInputEnabled
            ACTION_VOLUME_LONG_PRESS_IN_APP_NOTE -> true  // App 内笔记功能总是可用
            ACTION_VOLUME_LONG_PRESS_FLOATING_NOTE -> settings.isFloatingWindowEnabled
            ACTION_VOLUME_LONG_PRESS_PTT_RECORD -> settings.voiceInputEnabled && settings.isFloatingWindowEnabled
            else -> false
        }
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
                    cancelProgressNotification()
                    app.notificationCenter.showRecognitionFailureResultNotification(
                        RecognitionFailureMessageMapper.display(payload)
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        cancelResultNotification()
                    }, 8000)
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
                    cancelProgressNotification()
                    if (payload.createdCount <= 0) {
                        showResultNotification(RecognitionNormalDisplay.completedNoNewEvents(), useOcrCapsule = true, durationMs = 8000L)
                        Handler(Looper.getMainLooper()).postDelayed({
                            cancelResultNotification()
                        }, 8000)
                    }
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
                    cancelProgressNotification()
                    showResultNotification(RecognitionNormalDisplay.saveFailed(payload.message), useOcrCapsule = true, durationMs = 8000L)
                    Handler(Looper.getMainLooper()).postDelayed({
                        cancelResultNotification()
                    }, 8000)
                }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val currentSettings = try {
            settingsQueryApi.settings.value
        } catch (e: Exception) {
            return super.onKeyEvent(event)
        }

        val isVolumeUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isVolumeUp && !isVolumeDown) return super.onKeyEvent(event)

        val enabled = if (isVolumeUp) shouldFilterVolumeUpKeys() else shouldFilterVolumeDownKeys()
        val doubleTapEnabled = if (isVolumeUp) shouldFilterVolumeUpDoubleTap() else shouldFilterVolumeDownDoubleTap()
        if (!enabled && !doubleTapEnabled) {
            cancelVolumeLongPressState()
            return false
        }

        val longPressAction = (if (isVolumeUp) currentSettings.volumeUpLongPressAction
            else currentSettings.volumeDownLongPressAction).coerceIn(0, 7)
        val doubleTapAction = (if (isVolumeUp) currentSettings.volumeUpDoubleTapAction
            else currentSettings.volumeDownDoubleTapAction).coerceIn(0, 7)
        val keyLabel = if (isVolumeUp) "音量+" else "音量−"

        if (FloatingScheduleService.isShowing && longPressAction == ACTION_VOLUME_LONG_PRESS_SCREENSHOT) {
            Log.d(TAG, "悬浮窗已显示，$keyLabel 识屏长按放行")
            return false
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) {
                    // 双击占用整键时，系统收不到 DOWN，按住连调由应用代调
                    if (doubleTapEnabled && repeatVolumeJob == null) startVolumeRepeat(isVolumeUp)
                    return true
                }

                if (isVolumeUp) isVolumeUpPressed = true else isVolumeDownPressed = true
                isLongPressTriggered = false
                isVoiceCaptureTriggered = false
                volumeLongPressJob?.cancel()

                // 双击判定：与上一次 UP 间隔在窗口内、且同键 → 消费掉待执行的单击
                // lastUpAt 为 0（从未按过）时直接视为非双击，避免 Long 减法溢出
                val now = SystemClock.uptimeMillis()
                val isDoubleTap = doubleTapEnabled &&
                    lastUpAt != 0L &&
                    lastUpKeyCode == event.keyCode &&
                    now - lastUpAt <= DOUBLE_TAP_WINDOW_MS &&
                    pendingSingleTapJob?.isActive == true

                if (isDoubleTap) {
                    pendingSingleTapJob?.cancel()
                    pendingSingleTapJob = null
                    lastUpAt = 0L
                    lastUpKeyCode = 0
                    doubleTapJustFired = true
                    Log.d(TAG, "$keyLabel 双击触发（动作$doubleTapAction）")
                    performHapticFeedback()
                    serviceScope.launch { executeLongPressAction(doubleTapAction, currentSettings, keyLabel) }
                    return true
                }

                volumeLongPressJob = serviceScope.launch {
                    delay(LONG_PRESS_THRESHOLD)
                    isLongPressTriggered = true
                    val actionLabel = when (longPressAction) {
                        ACTION_VOLUME_LONG_PRESS_SCREENSHOT -> "识屏"
                        ACTION_VOLUME_LONG_PRESS_FLOATING -> "悬浮窗"
                        ACTION_VOLUME_LONG_PRESS_VOICE -> "语音"
                        ACTION_VOLUME_LONG_PRESS_QUICK_RECORD -> "快速录音"
                        ACTION_VOLUME_LONG_PRESS_IN_APP_NOTE -> "App内笔记"
                        ACTION_VOLUME_LONG_PRESS_FLOATING_NOTE -> "悬浮窗笔记"
                        ACTION_VOLUME_LONG_PRESS_PTT_RECORD -> "按住说话"
                        else -> "无操作"
                    }
                    Log.d(TAG, "长按$keyLabel 已确认，触发 $actionLabel")

                    performHapticFeedback()
                    executeLongPressAction(longPressAction, currentSettings, keyLabel)
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                if (isVolumeUp) isVolumeUpPressed = false else isVolumeDownPressed = false
                volumeLongPressJob?.cancel()
                stopVolumeRepeat()

                if (isLongPressTriggered) {
                    Log.d(TAG, "$keyLabel 抬起 (长按处理完毕)")
                    if (isVoiceCaptureTriggered) stopVoiceCaptureService()
                    if (isInAppVoiceTriggered) VoiceCaptureHandleActivity.stopActiveCapture()
                } else if (doubleTapEnabled) {
                    // 双击占用整键：先挂起单击补调，等 300ms 确认无第二击再补。
                    // 刚被双击消费过的抬起不再补调，否则双击触发动作的同时音量会多跳一下。
                    if (doubleTapJustFired) {
                        doubleTapJustFired = false
                        Log.d(TAG, "$keyLabel 双击后的抬起，不补调音量")
                    } else {
                        lastUpAt = SystemClock.uptimeMillis()
                        lastUpKeyCode = event.keyCode
                        pendingSingleTapJob?.cancel()
                        pendingSingleTapJob = serviceScope.launch {
                            delay(DOUBLE_TAP_WINDOW_MS)
                            Log.d(TAG, "$keyLabel 单点（无第二击），模拟音量调节")
                            try {
                                audioManager.adjustSuggestedStreamVolume(
                                    if (isVolumeUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                                    AudioManager.USE_DEFAULT_STREAM_TYPE,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "模拟调节音量失败", e)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "$keyLabel 抬起 (短按)，模拟系统音量调节")
                    try {
                        audioManager.adjustSuggestedStreamVolume(
                            if (isVolumeUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                            AudioManager.USE_DEFAULT_STREAM_TYPE,
                            AudioManager.FLAG_SHOW_UI
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "模拟调节音量失败", e)
                    }
                }

                isLongPressTriggered = false
                isVoiceCaptureTriggered = false
                isInAppVoiceTriggered = false
                return true
            }

            else -> {
                cancelVolumeLongPressState()
                return true
            }
        }
    }

    /** 长按确认后的动作分发；音量+ 与音量− 共用。 */
    private suspend fun executeLongPressAction(
        longPressAction: Int,
        currentSettings: MySettings,
        keyLabel: String
    ) {
        when (longPressAction) {
            ACTION_VOLUME_LONG_PRESS_SCREENSHOT -> {
                startAnalysis(MySettings.normalizeScreenshotDelayMs(currentSettings.screenshotDelayMs).milliseconds)
            }
            ACTION_VOLUME_LONG_PRESS_FLOATING -> {
                // toggle 语义（对齐声物记 showOverlay）：
                // 已显示 -> 立即隐藏；未显示 -> 显示并自动展开面板。
                if (FloatingScheduleService.isShowing) {
                    floatingCenter.hideFloatingService()
                } else {
                    startFloatingService()
                }
            }
            ACTION_VOLUME_LONG_PRESS_VOICE -> {
                if (FloatingScheduleService.isShowing && !currentSettings.floatingVoiceLongPressEnabled) {
                    Log.d(TAG, "悬浮窗长按闪记已关闭，忽略$keyLabel 语音触发")
                    return
                }
                // 悬浮窗录音为开关制：录音中再触发即停止，避免与「按住说话」的松手停录语义重叠
                if (QuickMemoVoiceCaptureService.instance?.isCapturingNow() == true ||
                    FloatingScheduleService.instance?.isCapturingNow() == true) {
                    stopVoiceCaptureService()
                } else {
                    startVoiceCaptureService()
                }
            }
            ACTION_VOLUME_LONG_PRESS_QUICK_RECORD -> {
                isInAppVoiceTriggered = true
                launchInAppVoiceCapture()
            }
            ACTION_VOLUME_LONG_PRESS_IN_APP_NOTE -> {
                openInAppQuickMemo()
            }
            ACTION_VOLUME_LONG_PRESS_FLOATING_NOTE -> {
                startFloatingService()
            }
            ACTION_VOLUME_LONG_PRESS_PTT_RECORD -> {
                if (FloatingScheduleService.isShowing && !currentSettings.floatingVoiceLongPressEnabled) {
                    Log.d(TAG, "悬浮窗长按闪记已关闭，忽略$keyLabel 按住说话")
                    return
                }
                isVoiceCaptureTriggered = true
                startVoiceCaptureService()
            }
        }
    }

    /** 拉起 App 内全屏录音页；松手时由 onKeyEvent 的 ACTION_UP 停录。 */
    private fun launchInAppVoiceCapture() {
        if (!settingsQueryApi.settings.value.voiceInputEnabled) {
            Log.w(TAG, "闪记未开启，无法启动 App 内录音")
            showResultNotification("闪记未开启", "请先在实验室中开启闪记")
            return
        }
        try {
            VoiceCaptureHandleActivity.prepareForLaunch()
            startActivity(
                Intent(this, VoiceCaptureHandleActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "启动 App 内录音页失败", e)
        }
    }

    /** 拉起主 App 并切到闪记入口，供用户在 App 内新建笔记。 */
    private fun openInAppQuickMemo() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_QUICK_MEMO, true)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "拉起 App 内闪记失败", e)
        }
    }

    private fun cancelVolumeLongPressState() {
        volumeLongPressJob?.cancel()
        isLongPressTriggered = false
        isVoiceCaptureTriggered = false
        isInAppVoiceTriggered = false
        isVolumeUpPressed = false
        isVolumeDownPressed = false
        pendingSingleTapJob?.cancel()
        pendingSingleTapJob = null
        stopVolumeRepeat()
        lastUpAt = 0L
        lastUpKeyCode = 0
    }

    private fun performHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "震动反馈失败", e)
        }
    }

    private fun startFloatingService() {
        if (!floatingCenter.canDrawOverlays(this)) {
            Log.w(TAG, "悬浮窗权限未授予，无法启动悬浮窗")
            app.notificationCenter.showFloatingPermissionDeniedNotification(NOTIFICATION_ID_RESULT)
            return
        }
        serviceScope.launch {
            floatingCenter.startFloatingService()
        }
    }

    private fun startVoiceCaptureService() {
        if (!settingsQueryApi.settings.value.voiceInputEnabled) {
            Log.w(TAG, "闪记未开启，无法启动闪记录音")
            showResultNotification("闪记未开启", "请先在实验室中开启闪记")
            return
        }
        if (!floatingCenter.canDrawOverlays(this)) {
            Log.w(TAG, "悬浮窗权限未授予，无法启动闪记录音")
            app.notificationCenter.showFloatingPermissionDeniedNotification(NOTIFICATION_ID_RESULT)
            return
        }
        serviceScope.launch {
            floatingCenter.startVoiceCaptureService()
        }
    }

    private fun stopVoiceCaptureService() {
        if (!floatingCenter.canDrawOverlays(this)) return
        serviceScope.launch {
            floatingCenter.stopVoiceCaptureService()
        }
    }

    private fun getLauncherPackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    override fun onUnbind(intent: Intent?): Boolean {
        imagePinJob?.cancel()
        logServiceHealth("unbound")
        cancelCurrentAnalysis()
        if (instance === this) {
            instance = null
            lastDisconnectedAt = System.currentTimeMillis()
        }
        recognitionFailedSubscriptionJob?.cancel()
        recognitionFailedSubscriptionJob = null
        ingestSucceededSubscriptionJob?.cancel()
        ingestSucceededSubscriptionJob = null
        ingestFailedSubscriptionJob?.cancel()
        ingestFailedSubscriptionJob = null
        keyFilterSettingsJob?.cancel()
        keyFilterSettingsJob = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        logServiceHealth("destroyed")
        if (instance === this) {
            instance = null
            lastDisconnectedAt = System.currentTimeMillis()
        }
        recognitionFailedSubscriptionJob?.cancel()
        recognitionFailedSubscriptionJob = null
        ingestSucceededSubscriptionJob?.cancel()
        ingestSucceededSubscriptionJob = null
        ingestFailedSubscriptionJob?.cancel()
        ingestFailedSubscriptionJob = null
        keyFilterSettingsJob?.cancel()
        keyFilterSettingsJob = null
        cancelCurrentAnalysis()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ANALYSIS) {
            cancelCurrentAnalysis()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun cancelCurrentAnalysis() {
        if (analysisJob?.isActive == true) logServiceHealth("analysis_cancel_requested")
        analysisJob?.cancel()
        // 由任务 finally 释放互斥；取消旧任务后不能提前放行新截图。
        cancelProgressNotification()
    }

    /**
     * 终极适配版：解决国产系统控制中心收起与三星/类原生回退冲突
     */
    fun closeNotificationPanel(): Boolean {
        var syncSuccess = false
        val tag = "PanelFixV3"

        // --- 层级 1：标准 Android 12+ API ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // 无障碍服务自带执行权限，无需 WRITE_SECURE_SETTINGS
                syncSuccess = performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } catch (e: Exception) {
                Log.w(tag, "API 12+ 指令执行异常", e)
            }
        }

        // --- 层级 2：传统广播 (仅在层级 1 明确失败或版本不支持时触发) ---
        if (!syncSuccess) {
            try {
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                // 注意：广播发出不代表成功收起，仅标记指令已送达
            } catch (e: Exception) {
                Log.w(tag, "系统广播发送失败", e)
            }
        }

        // --- 层级 3：智能动态"补刀"逻辑 ---
        Handler(Looper.getMainLooper()).postDelayed({
            val rootNode = rootInActiveWindow
            val currentPackage = rootNode?.packageName?.toString() ?: ""
            
            // 扩展国产 ROM 包名库，支持可配置扩展
            val systemUiPackages = mutableSetOf(
                "com.android.systemui",    // 通用/原生/MIUI/OneUI
                "com.coloros.systemui",   // OPPO/ColorOS
                "com.oppo.systemui",      // OPPO 旧版
                "com.vivo.systemui",      // vivo/OriginOS
                "com.huawei.systemui",    // 华为/EMUI
                "com.hihonor.systemui",   // 荣耀/MagicUI
                "com.meizu.systemui"      // 魅族/Flyme
            )

            // 检查当前是否仍处于系统 UI 界面（排除桌面和 App）
            val isStillOnSystemUi = systemUiPackages.any { currentPackage.contains(it) }

            if (isStillOnSystemUi) {
                Log.d(tag, "检测到面板钉子户: $currentPackage，执行 Back 补刀")
                // GLOBAL_ACTION_BACK 是 Android CDD 规定的交互兜底
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                // 面板已消失，不执行任何操作，保护三星/原生用户不回退
                Log.d(tag, "面板已安全避让，当前包名: $currentPackage")
            }
            
            rootNode?.recycle()
        }, 180) // 微调至 180ms，避开部分设备 150ms 时的动画临界态

        return syncSuccess
    }

    /** 磁贴主动截图；复用截图互斥和系统回调，结果只进入图片挂起。 */
    fun startImagePinCapture() {
        fun hint(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        if (instance !== this || !serviceScope.isActive) { hint("无障碍服务暂不可用"); return }
        ImagePinPolicy.captureBlockReason(this, settingsQueryApi.settings.value)?.let { hint(it); return }
        if (!isAnalyzing.compareAndSet(false, true)) { hint("当前截图或识别尚未结束，请稍后重试"); return }
        imagePinJob = serviceScope.launch(CoroutineName("image_pin_capture"), start = CoroutineStart.UNDISPATCHED) {
            try {
                closeNotificationPanel()
                delay(MySettings.normalizeScreenshotDelayMs(settingsQueryApi.settings.value.screenshotDelayMs))
                ImagePinPolicy.captureBlockReason(this@TextAccessibilityService, settingsQueryApi.settings.value)?.let { error(it) }
                val keyguard = getSystemService(android.app.KeyguardManager::class.java)
                check(!keyguard.isKeyguardLocked) { "请解锁后重新点击截图挂起" }
                val root = rootInActiveWindow
                val panelVisible = try { root?.packageName?.toString()?.endsWith(".systemui") == true }
                    finally { root?.recycle() }
                check(!panelVisible) { "控制中心尚未收起，请稍后重试" }
                val screenshot = captureScreenshot()
                try {
                    check(!keyguard.isKeyguardLocked) { "请解锁后重新点击截图挂起" }
                    withContext(Dispatchers.IO) {
                        val hardware = checkNotNull(Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)) {
                            "截图无法读取"
                        }
                        val bitmap = try { checkNotNull(hardware.copy(Bitmap.Config.ARGB_8888, false)) }
                            finally { hardware.recycle() }
                        try { app.imagePinController.pinScreenshot(bitmap) } finally { bitmap.recycle() }
                    }
                } finally { screenshot.hardwareBuffer.close() }
                hint("截图已挂起")
            } catch (e: TimeoutCancellationException) {
                hint("截图超时，请重试")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                hint(if (e is IllegalStateException && e.message?.startsWith("screenshot:") == false)
                    e.message ?: "截图挂起失败" else "当前页面无法截图，请稍后重试或换个页面")
            } finally {
                isAnalyzing.set(false)
            }
        }
    }

    fun startAnalysis(delayDuration: Duration = 500.milliseconds, fromShortcut: Boolean = false) {
        Log.i(
            RECOGNITION_LOG_TAG,
            "accessibility start requested delayMs=${delayDuration.inWholeMilliseconds} " +
                "shortcut=$fromShortcut connected=${isConnected()} busy=${isAnalyzing.get()}"
        )
        startRecognition(delayDuration)
    }

    private fun startRecognition(delayDuration: Duration) {
        if (!isAnalyzing.compareAndSet(false, true)) return
        analysisJob = serviceScope.launch(CoroutineName("recognition"), start = CoroutineStart.UNDISPATCHED) {
            try {
                delay(delayDuration)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) takeScreenshotAndAnalyze()
                else showResultNotification(SystemNormalDisplay.androidVersionTooLow(), useOcrCapsule = true)
            } catch (e: Exception) {
                cancelProgressNotification()
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                showResultNotification(RecognitionNormalDisplay.screenshotFailed("截图失败，请重试"), useOcrCapsule = true)
            } finally { isAnalyzing.set(false) }
        }
    }

    private suspend fun takeScreenshotAndAnalyze() {
        val screenshot = captureScreenshot()
        try {
            showProgressNotification(RecognitionNormalDisplay.analyzing())
            withContext(Dispatchers.IO) { processScreenshot(screenshot) }
        } finally { screenshot.hardwareBuffer.close() }
    }

    private class ScreenshotFailure(val code: Int) : IllegalStateException("screenshot:$code")

    private suspend fun captureScreenshot(): ScreenshotResult {
        return withTimeout(ConfigCatalog.RECOGNITION_SCREENSHOT_TIMEOUT_MS.toLong()) {
            suspendCancellableCoroutine<ScreenshotResult> { continuation ->
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        if (!continuation.isActive) { screenshotResult.hardwareBuffer.close(); return }
                        continuation.resume(screenshotResult, onCancellation = { _, result, _ -> result.hardwareBuffer.close() })
                    }
                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) continuation.resumeWithException(ScreenshotFailure(errorCode))
                    }
                })
            }
        }
    }

    private suspend fun processScreenshot(result: ScreenshotResult) {
        var ownedBitmap: Bitmap? = null
        try {
            val softwareBitmap = run {
                val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    ?: error("hardware bitmap wrap failed")
                try { bitmap.copy(Bitmap.Config.ARGB_8888, true) } finally { bitmap.recycle() }
            }
            ownedBitmap = softwareBitmap
            val imagesDir = File(filesDir, "event_screenshots")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFile = File(imagesDir, "IMG_$timestamp.jpg")

            FileOutputStream(imageFile).use { out ->
                softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            val settings = settingsQueryApi.settings.value
            if (!settings.isRecognitionConfigReady()) {
                Log.w(
                    RECOGNITION_LOG_TAG,
                    "accessibility image rejected configReady=false multimodal=true"
                )
                withContext(Dispatchers.Main) {
                    cancelProgressNotification()
                    showResultNotification(RecognitionNormalDisplay.configMissing(settings.recognitionConfigMissingMessage()), autoLaunch = true, useOcrCapsule = true, durationMs = 12000L)
                }
                softwareBitmap.recycle()
                return
            }

            val traceId = EventIdentity.newTraceId("accessibility")
            Log.i(
                RECOGNITION_LOG_TAG,
                "accessibility dispatching recognition trace=$traceId " +
                    "size=${softwareBitmap.width}x${softwareBitmap.height} " +
                    "multimodal=true"
            )
            val analysisResult = app.recognitionApi.analyzeImage(
                bitmap = softwareBitmap,
                settings = settings,
                context = applicationContext,
                sourceType = RECOGNITION_SOURCE_TYPE,
                sourceId = RECOGNITION_SOURCE_ID,
                sourceImagePath = imageFile.absolutePath,
                ingestRequested = true,
                traceId = traceId
            )
            softwareBitmap.recycle()

            withContext(Dispatchers.Main) {
                when (analysisResult) {
                    is AnalysisResult.Success -> {
                        val validEvents = analysisResult.data.filter { it.title.isNotBlank() }
                        if (validEvents.isEmpty()) {
                            cancelProgressNotification()
                            showResultNotification(RecognitionNormalDisplay.analysisCompletedNoValidSchedule(), useOcrCapsule = true, durationMs = 5000L)
                            Handler(Looper.getMainLooper()).postDelayed({
                                cancelResultNotification()
                            }, 5000)
                            return@withContext
                        }
                    }
                    is AnalysisResult.Empty -> Unit
                    is AnalysisResult.Failure -> {
                        cancelProgressNotification()
                        showResultNotification(
                            analysisResult.failure.title,
                            analysisResult.failure.detail,
                            useOcrCapsule = true,
                            durationMs = 8000L
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "处理截图出错", e)
            withContext(Dispatchers.Main) {
                cancelProgressNotification()
                showResultNotification(RecognitionNormalDisplay.analysisError(e.message), useOcrCapsule = true, durationMs = 8000L)
            }
        } finally {
            ownedBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun showProgressNotification(content: NormalNotificationContent) {
        if (shouldUseOcrCapsule()) {
            capsuleCenter.showOcrProgress(
                title = content.title,
                content = content.contentText,
                actions = listOf(
                    CapsuleActionSpec(
                        label = "取消",
                        receiverAction = EventActionReceiver.ACTION_CANCEL_RECOGNITION
                    )
                )
            )
        } else {
            app.notificationCenter.showRecognitionStatusNotification(
                notificationId = NOTIFICATION_ID_PROGRESS,
                content = content,
                isProgress = true,
                autoLaunch = false
            )
        }
    }

    private fun cancelProgressNotification() {
        if (shouldUseOcrCapsule()) {
            capsuleCenter.clearOcrCapsule()
            capsuleCenter.clearModelLoading()
            return
        }
        app.notificationCenter.cancelNotification(NOTIFICATION_ID_PROGRESS)
    }

    private fun cancelResultNotification() {
        if (shouldUseOcrCapsule()) {
            capsuleCenter.clearOcrCapsule()
            capsuleCenter.clearModelLoading()
            return
        }
        app.notificationCenter.cancelNotification(NOTIFICATION_ID_RESULT)
    }

    private fun showResultNotification(
        title: String,
        content: String,
        autoLaunch: Boolean = false,
        useOcrCapsule: Boolean = false,
        durationMs: Long = 8000L
    ) {
        if (useOcrCapsule && shouldUseOcrCapsule()) {
            capsuleCenter.showOcrResult(title, content, durationMs)
        } else {
            app.notificationCenter.showRecognitionStatusNotification(
                notificationId = NOTIFICATION_ID_RESULT,
                content = NormalNotificationContent(title = title, contentText = content),
                isProgress = false,
                autoLaunch = autoLaunch,
                durationMs = durationMs
            )
        }
    }

    private fun showResultNotification(
        content: NormalNotificationContent,
        autoLaunch: Boolean = false,
        useOcrCapsule: Boolean = false,
        durationMs: Long = 8000L
    ) {
        showResultNotification(
            title = content.title,
            content = content.contentText,
            autoLaunch = autoLaunch,
            useOcrCapsule = useOcrCapsule,
            durationMs = durationMs
        )
    }

    private fun buildScreenshotFailureContent(errorCode: Int): String {
        return RecognitionNormalDisplay.screenshotFailureContent(errorCode)
    }

    private fun shouldUseOcrCapsule(): Boolean {
        return settingsQueryApi.settings.value.isLiveCapsuleEnabled
    }

}
