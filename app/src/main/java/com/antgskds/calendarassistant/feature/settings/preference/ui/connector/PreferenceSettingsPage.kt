package com.antgskds.calendarassistant.feature.settings.preference.ui.connector


import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.edition.EditionCheckbox
import com.antgskds.calendarassistant.shared.ui.edition.EditionCategoricalPreference
import com.antgskds.calendarassistant.shared.ui.edition.EditionDropdownActionSettingItem
import com.antgskds.calendarassistant.shared.ui.edition.EditionSwitchSliderSettingItem

import com.antgskds.calendarassistant.shared.ui.material.settings.*
import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.platform.floating.EdgeBarService
import com.antgskds.calendarassistant.platform.floating.FloatingBallService
import com.antgskds.calendarassistant.platform.receiver.SmsNotificationListenerService
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.shared.ui.material.component.AppSheetAction
import com.antgskds.calendarassistant.shared.ui.material.component.AppModalBottomSheet
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.component.AppAlertDialog
import com.antgskds.calendarassistant.shared.ui.material.component.CenteredDialogTitle
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalSnackbar
import com.antgskds.calendarassistant.shared.ui.material.component.WheelPicker
import com.antgskds.calendarassistant.feature.settings.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.interaction.HapticValueChangeEffect
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.permission.rememberPermissionGate
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.settings.preference.ui.contract.PreferenceUiController
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.PreferenceUiControllerAdapter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PreferenceSection {
    DISPLAY,
    OPERATION,
    SCREENSHOT,
}

data class PreferenceItemVisibility(
    val showUiSize: Boolean = true,
    val showTomorrowEvents: Boolean = true,
    val showRememberCalendarViewMode: Boolean = true,
    val showWidgetSettings: Boolean = true,
    val showHapticFeedback: Boolean = true,
    val showNetworkSpeedCapsule: Boolean = true,
    val showAutoArchive: Boolean = true,
    val showScheduleColors: Boolean = true,
)

@Composable
fun PreferenceSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2,
    visibleSections: Set<PreferenceSection> = PreferenceSection.entries.toSet(),
    itemVisibility: PreferenceItemVisibility = PreferenceItemVisibility(),
    footerContent: @Composable ColumnScope.() -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToScheduleColors: () -> Unit = {},
) {
    MaterialPreferenceSettingsScreen(
        controller = remember(viewModel) { PreferenceUiControllerAdapter(viewModel) },
        uiSize = uiSize,
        visibleSections = visibleSections,
        itemVisibility = itemVisibility,
        footerContent = footerContent,
        onNavigateToWidgetSettings = onNavigateToWidgetSettings,
        onNavigateToScheduleColors = onNavigateToScheduleColors,
    )
}

@Composable
fun MaterialPreferenceSettingsScreen(
    controller: PreferenceUiController,
    uiSize: Int = 2,
    visibleSections: Set<PreferenceSection> = PreferenceSection.entries.toSet(),
    itemVisibility: PreferenceItemVisibility = PreferenceItemVisibility(),
    footerContent: @Composable ColumnScope.() -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToScheduleColors: () -> Unit = {},
) {
    val settings by controller.settings.collectAsState()
    val syncStatus by controller.syncStatus.collectAsState()
    val availableSyncCalendars by controller.availableSyncCalendars.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as? App
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionGate = rememberPermissionGate(snackbarHostState)
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }
    val bottomInset = LocalAppPageBottomPadding.current
    var showSourceCalendarSheet by remember { mutableStateOf(false) }
    var showEventDurationPicker by remember { mutableStateOf(false) }
    var showDailySummaryMorningTimePicker by remember { mutableStateOf(false) }
    var showDailySummaryEveningTimePicker by remember { mutableStateOf(false) }

    val selectedSourceCalendars by remember(syncStatus.sourceCalendarIds, availableSyncCalendars) {
        derivedStateOf {
            availableSyncCalendars.filter { syncStatus.sourceCalendarIds.contains(it.id) }
        }
    }
    val selectedSourceSummary by remember(syncStatus.sourceCalendarIds, selectedSourceCalendars) {
        derivedStateOf {
            formatSelectedCalendarSummary(syncStatus.sourceCalendarIds, selectedSourceCalendars)
        }
    }
    // Toast 辅助函数
    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    var hasOverlayPermission by remember {
        mutableStateOf(app?.permissionCenter?.canDrawOverlays(context) ?: Settings.canDrawOverlays(context))
    }

    fun refreshOverlayPermission() {
        hasOverlayPermission = app?.permissionCenter?.canDrawOverlays(context)
            ?: Settings.canDrawOverlays(context)
    }

    LaunchedEffect(Unit) {
        refreshOverlayPermission()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshOverlayPermission()
                controller.refreshSyncStatus()
                controller.refreshSyncCalendars()
                permissionGate.resumePending()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    fun startEdgeBarService() {
        app?.floatingCenter?.startEdgeBarServiceIfPermitted()
            ?: context.startService(Intent(context, EdgeBarService::class.java))
    }

    fun stopEdgeBarService() {
        app?.floatingCenter?.stopEdgeBarService()
            ?: context.stopService(Intent(context, EdgeBarService::class.java))
    }

    fun startFloatingBallService() {
        app?.floatingCenter?.startFloatingBallServiceIfPermitted()
            ?: context.startService(Intent(context, FloatingBallService::class.java))
    }

    fun stopFloatingBallService() {
        app?.floatingCenter?.stopFloatingBallService()
            ?: context.stopService(Intent(context, FloatingBallService::class.java))
    }

    // --- 字体样式优化 ---
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // 权限请求统一由 PermissionGate 续接，授权返回后再执行原开启动作。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionGate.resumePending()
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGate.resumePending()
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGate.resumePending()
    }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionGate.resumePending()
    }

    fun hasNotificationPermission(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && manager?.areNotificationsEnabled() == true
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        }
    }

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasCalendarPermission(): Boolean =
        app?.permissionCenter?.hasCalendarPermissions(context) == true ||
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED)

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun requestAccessibilityPermission() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun requestSmsPermission() {
        smsPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
        )
    }

    fun requestRecordAudioPermission() {
        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun enableSmsMonitoring() {
        controller.updatePreference(smsMonitoring = true)
        (context.applicationContext as? App)?.refreshSmsObserver(enabled = true)
        if (!SmsNotificationListenerService.isEnabled(context)) {
            Toast.makeText(context, "建议开启通知监听兜底（系统短信）", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestCalendarPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        } else {
            emptyArray()
        }
        calendarPermissionLauncher.launch(permissions)
    }

    fun enableCalendarSync() {
        controller.enableCalendarSyncAndSyncNow { result ->
            controller.refreshSyncCalendars()
            if (result.isSuccess) {
                showToast("日历同步已开启，并已立即同步")
            } else {
                showToast("日历同步开启失败", ToastType.ERROR)
            }
        }
    }

    fun requireNotificationWhenEnabling(enabled: Boolean, update: () -> Unit) {
        if (enabled) {
            permissionGate.require(
                permissionName = "通知权限",
                isGranted = ::hasNotificationPermission,
                requestPermission = ::requestNotificationPermission,
                onGranted = update,
            )
        } else {
            update()
        }
    }

    CompositionLocalProvider(LocalAppHapticsEnabled provides settings.hapticFeedbackEnabled) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 80.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================== 显示板块 ==================
            if (PreferenceSection.DISPLAY in visibleSections) {
            Text("显示", style = sectionTitleStyle)
            AppSettingsCard {
                if (itemVisibility.showUiSize) {
                    SliderSettingItem(
                        title = "界面大小",
                        subtitle = "调整界面缩放（相对于设备原生大小）",
                        value = settings.uiSize.toFloat(),
                        onValueChange = { controller.updateUiSize(it.toInt()) },
                        valueRange = 1f..3f,
                        steps = 1, // 离散：1, 2, 3
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                        showValueAsNumber = false // 显示文字：小/中/大
                    )
                    AppSettingsDivider()
                }
                if (itemVisibility.showTomorrowEvents) {
                    SwitchSettingItem(
                        title = "显示明日日程",
                        subtitle = "在今日日程列表底部预览明日安排",
                        checked = settings.showTomorrowEvents,
                        onCheckedChange = { controller.updatePreference(showTomorrow = it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    AppSettingsDivider()
                }
                if (itemVisibility.showRememberCalendarViewMode) {
                    SwitchSettingItem(
                        title = "记住日历视图",
                        subtitle = "首页沿用上次选择的今日/周/月/日程视图；关闭后每次回到今日视图",
                        checked = settings.rememberCalendarViewMode,
                        onCheckedChange = { controller.setRememberCalendarViewMode(it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    AppSettingsDivider()
                }
                if (itemVisibility.showWidgetSettings) {
                    ActionSettingItem(
                        title = "桌面小组件",
                        subtitle = "预览小组件并调整主题、透明度等显示设置",
                        value = "",
                        icon = Icons.Default.ChevronRight,
                        enabled = true,
                        onClick = onNavigateToWidgetSettings,
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                    )
                    AppSettingsDivider()
                }
            }

            }

            // ================== 操作板块 ==================
            if (PreferenceSection.OPERATION in visibleSections) {
            Text("操作", style = sectionTitleStyle)
            AppSettingsCard {
                if (itemVisibility.showHapticFeedback) {
                    SwitchSettingItem(
                        title = "触感反馈",
                        subtitle = "点击、长按和滑动到阈值时提供轻微反馈",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { controller.updatePreference(hapticFeedbackEnabled = it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    AppSettingsDivider()
                }
                    VolumeLongPressSettingItem(
                        title = "长按音量+",
                        subtitle = "自定义长按音量+动作",
                        checked = settings.volumeUpLongPressEnabled,
                        action = settings.volumeUpLongPressAction,
                        onCheckedChange = { isChecked ->
                            val updateVolumeShortcut = {
                                controller.updatePreference(
                                    volumeUpLongPressEnabled = isChecked,
                                    volumeUpLongPressAction = if (isChecked) settings.volumeUpLongPressAction.coerceIn(1, 3) else settings.volumeUpLongPressAction
                                )
                            }
                            if (isChecked) {
                                permissionGate.require(
                                    permissionName = "无障碍权限",
                                    isGranted = { TextAccessibilityService.isConnected() },
                                    requestPermission = ::requestAccessibilityPermission,
                                    onGranted = updateVolumeShortcut,
                                )
                            } else {
                                updateVolumeShortcut()
                            }
                        },
                        onActionChange = { action ->
                            controller.updatePreference(volumeUpLongPressAction = action)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    SwitchSettingItem(
                        title = "短信自动解析取件码",
                        subtitle = "监听短信自动识别快递取件码并入库",
                        checked = settings.isSmsMonitoringEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                permissionGate.require(
                                    permissionName = "短信权限",
                                    isGranted = ::hasSmsPermission,
                                    requestPermission = ::requestSmsPermission,
                                    onGranted = ::enableSmsMonitoring,
                                )
                            } else {
                                (context.applicationContext as? App)?.refreshSmsObserver(enabled = false)
                                controller.updatePreference(smsMonitoring = false)
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
            }

            }

            // ================== 截图板块 (新) ==================
            if (PreferenceSection.SCREENSHOT in visibleSections) {
            // 注意：现在它在 Column 内部，位于“日程”卡片之后
            Text("截图", style = sectionTitleStyle)
            AppSettingsCard {
                    SliderSettingItem(
                        title = "截图延迟",
                        subtitle = "截图与分析之间的等待时间",
                        value = MySettings.normalizeScreenshotDelayMs(settings.screenshotDelayMs).toFloat(),
                        onValueChange = { controller.updateScreenshotDelay(it.toLong()) },
                        valueRange = MySettings.SCREENSHOT_DELAY_MIN_MS.toFloat()..MySettings.SCREENSHOT_DELAY_MAX_MS.toFloat(),
                        steps = 0, // 0 = 无极调节
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                        showValueAsNumber = true, // 开启数字显示
                        valueUnit = "ms"
                    )
            }
            }

            footerContent()

        } // <--- Column 结束在这里，确保所有板块都在里面

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp + bottomInset),
            snackbar = { data -> UniversalSnackbar(data = data, type = currentToastType) }
        )

        if (showSourceCalendarSheet) {
            SourceCalendarPickerSheet(
                calendars = availableSyncCalendars,
                initialSelection = syncStatus.sourceCalendarIds.toSet(),
                onDismiss = { showSourceCalendarSheet = false },
                onConfirm = { selectedIds ->
                    controller.updateSourceCalendars(selectedIds) { result ->
                        if (result.isSuccess) {
                            showToast("同步来源日历已更新")
                            showSourceCalendarSheet = false
                        } else {
                            showToast("更新同步来源失败", ToastType.ERROR)
                        }
                    }
                }
            )
        }

        if (showEventDurationPicker) {
            EventDurationPickerDialog(
                selectedDuration = settings.defaultEventDurationMinutes,
                onDismiss = { showEventDurationPicker = false },
                onConfirm = { duration ->
                    controller.updatePreference(defaultEventDurationMinutes = duration)
                    showEventDurationPicker = false
                }
            )
        }

        if (showDailySummaryMorningTimePicker) {
            DailySummaryTimePickerDialog(
                selectedMinuteOfDay = settings.dailySummaryMorningMinuteOfDay,
                onDismiss = { showDailySummaryMorningTimePicker = false },
                title = "今日提醒时间",
                onConfirm = { minuteOfDay ->
                    controller.updateDailySummaryTimes(
                        morningMinuteOfDay = minuteOfDay,
                        onUpdated = { app?.runtimeCenter?.scheduleDailySummary() }
                    )
                    showDailySummaryMorningTimePicker = false
                }
            )
        }

        if (showDailySummaryEveningTimePicker) {
            DailySummaryTimePickerDialog(
                selectedMinuteOfDay = settings.dailySummaryEveningMinuteOfDay,
                onDismiss = { showDailySummaryEveningTimePicker = false },
                title = "明日预告时间",
                onConfirm = { minuteOfDay ->
                    controller.updateDailySummaryTimes(
                        eveningMinuteOfDay = minuteOfDay,
                        onUpdated = { app?.runtimeCenter?.scheduleDailySummary() }
                    )
                    showDailySummaryEveningTimePicker = false
                }
            )
        }
    }
    }
}

fun formatMinuteOfDay(minuteOfDay: Int): String {
    val safeMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(minuteOfDay)
    return "%02d:%02d".format(safeMinuteOfDay / 60, safeMinuteOfDay % 60)
}

// SwitchSettingItem 已抽至 SettingsRowComponents.kt（公共组件，同包，无需改 import）

// SideChoiceSettingItem 已抽至 SettingsRowComponents.kt

// ActionSettingItem 已抽至 SettingsRowComponents.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceCalendarPickerSheet(
    calendars: List<com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager.CalendarInfo>,
    initialSelection: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var selectedIds by remember(initialSelection, calendars) {
        mutableStateOf(initialSelection.intersect(calendars.map { it.id }.toSet()))
    }
    val haptics = rememberAppHaptics()
    val groupedCalendars = remember(calendars) {
        calendars.groupBy { calendar ->
            buildAccountGroupTitle(calendar)
        }
    }

    AppModalBottomSheet(
        title = "同步来源日历",
        subtitle = "将日程同步到系统日历",
        onDismissRequest = onDismiss,
        actions = listOf(AppSheetAction(
            text = "保存",
            enabled = calendars.isNotEmpty() && selectedIds.isNotEmpty(),
            onClick = { haptics.confirm(); onConfirm(calendars.filter { selectedIds.contains(it.id) }.map { it.id }) },
        )),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (calendars.isEmpty()) {
                Text(
                    text = "当前没有可读取的系统日历。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TextButton(onClick = { haptics.selection(); selectedIds = calendars.map { it.id }.toSet() }) {
                    Text("全选")
                }

                groupedCalendars.forEach { (groupTitle, groupCalendars) ->
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    groupCalendars.forEach { calendar ->
                        val checked = selectedIds.contains(calendar.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.selection()
                                    selectedIds = if (checked) {
                                        selectedIds - calendar.id
                                    } else {
                                        selectedIds + calendar.id
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditionCheckbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    haptics.selection()
                                    selectedIds = if (isChecked) {
                                        selectedIds + calendar.id
                                    } else {
                                        selectedIds - calendar.id
                                    }
                                }
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = buildCalendarMetaLine(calendar),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }


        }
    }
}

fun formatSelectedCalendarSummary(
    selectedIds: List<Long>,
    selectedCalendars: List<com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager.CalendarInfo>
): String {
    if (selectedIds.isEmpty()) {
        return "请选择需要从系统同步进 APP 的日历"
    }

    if (selectedCalendars.isEmpty()) {
        return "已选择 ${selectedIds.size} 个日历"
    }

    val names = selectedCalendars.map { it.name }.distinct()
    return if (names.size <= 2) {
        names.joinToString("、")
    } else {
        names.take(2).joinToString("、") + " 等 ${names.size} 个日历"
    }
}

private fun buildAccountGroupTitle(
    calendar: com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager.CalendarInfo
): String {
    val accountName = calendar.accountName?.takeIf { it.isNotBlank() } ?: "本地账户"
    val accountType = calendar.accountType?.takeIf { it.isNotBlank() }
    return if (accountType != null) {
        "$accountName  ($accountType)"
    } else {
        accountName
    }
}

private fun buildCalendarMetaLine(
    calendar: com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager.CalendarInfo
): String {
    val tags = mutableListOf<String>()
    if (!calendar.isVisible) tags += "已隐藏"
    if (!calendar.syncEvents) tags += "未启用系统同步"
    if (!calendar.isWritable) tags += "只读"
    return if (tags.isEmpty()) {
        "ID: ${calendar.id}"
    } else {
        "ID: ${calendar.id}  ${tags.joinToString(" · ")}"
    }
}

// SliderSettingItem 已抽至 SettingsRowComponents.kt

// VolumeLongPressSettingItem 已抽至 SettingsRowComponents.kt

// AdvanceReminderSettingItem 已抽至 SettingsRowComponents.kt

// EventDuration 簇（行/选择对话框/选项/格式化）已抽至 SettingsRowComponents.kt
