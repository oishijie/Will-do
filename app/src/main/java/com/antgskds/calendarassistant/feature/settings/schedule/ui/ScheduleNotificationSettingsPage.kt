package com.antgskds.calendarassistant.feature.settings.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.settings.preference.ui.contract.PreferenceUiController
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.PreferenceUiControllerAdapter
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.formatMinuteOfDay
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.SourceCalendarPickerSheet
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalSnackbar
import com.antgskds.calendarassistant.shared.ui.material.settings.DailySummaryTimePickerDialog
import com.antgskds.calendarassistant.shared.ui.material.settings.EventDurationPickerDialog
import com.antgskds.calendarassistant.shared.ui.permission.rememberPermissionGate
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.settings.*
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.PreferenceItemVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.edition.EditionSwitchSliderSettingItem
import kotlin.math.roundToInt
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Warning
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.formatSelectedCalendarSummary

/**
 * 日程与通知二级设置页。
 *
 * 承载原「通用偏好」里的通知与日程两组设置，按分区从上到下堆叠（不做 Tab）；
 * 权限申请与日历同步副作用从通用偏好原样迁入，行为不变。
 */
@Composable
fun ScheduleNotificationSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2,
    onNavigateToScheduleColors: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionGate = rememberPermissionGate(snackbarHostState)
    val controller = remember(viewModel) { PreferenceUiControllerAdapter(viewModel) }
    val settings by controller.settings.collectAsState()
    val syncStatus by controller.syncStatus.collectAsState()
    val availableSyncCalendars by controller.availableSyncCalendars.collectAsState()
    val bottomInset = LocalAppPageBottomPadding.current
    val app = context.applicationContext as? com.antgskds.calendarassistant.App

    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }
    var showSourceCalendarSheet by remember { mutableStateOf(false) }
    var showEventDurationPicker by remember { mutableStateOf(false) }
    var showDailySummaryMorningTimePicker by remember { mutableStateOf(false) }
    var showDailySummaryEveningTimePicker by remember { mutableStateOf(false) }
    val itemVisibility = PreferenceItemVisibility()

    fun showToast(message: String, type: ToastType = ToastType.SUCCESS) {
        currentToastType = type
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.refreshSyncStatus()
                controller.refreshSyncCalendars()
                permissionGate.resumePending()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {
        permissionGate.resumePending()
    }

    val calendarPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionGate.resumePending()
    }

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

    fun hasCalendarPermission(): Boolean =
        app?.permissionCenter?.hasCalendarPermissions(context) == true ||
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED)

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

    fun requestNotificationPermission() {
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    fun requireNotificationWhenEnabling(enabled: Boolean, update: () -> Unit) {
        if (enabled) {
            permissionGate.require(
                permissionName = "通知权限",
                isGranted = { hasNotificationPermission(context) },
                requestPermission = ::requestNotificationPermission,
                onGranted = update,
            )
        } else {
            update()
        }
    }

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
    )
    val cardTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )



    CompositionLocalProvider(
        LocalAppHapticsEnabled provides settings.hapticFeedbackEnabled,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 32.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

            // ================== 通知板块 ==================
            Text("通知", style = sectionTitleStyle)
            AppSettingsCard {
                    SwitchSettingItem(
                        title = "每日提醒",
                        subtitle = "今日 ${formatMinuteOfDay(settings.dailySummaryMorningMinuteOfDay)}，明日 ${formatMinuteOfDay(settings.dailySummaryEveningMinuteOfDay)}",
                        checked = settings.isDailySummaryEnabled,
                        onCheckedChange = { isChecked ->
                            requireNotificationWhenEnabling(isChecked) {
                                controller.updatePreference(dailySummary = isChecked)
                                if (isChecked) {
                                    app?.runtimeCenter?.scheduleDailySummary()
                                }
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    AnimatedVisibility(
                        visible = settings.isDailySummaryEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                            Column {
                                AppSettingsDivider()
                            DailySummaryTimeSettingItem(
                                title = "今日提醒时间",
                                subtitle = "推送今日日程汇总",
                                minuteOfDay = settings.dailySummaryMorningMinuteOfDay,
                                onClick = { showDailySummaryMorningTimePicker = true },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )
                        AppSettingsDivider()
                            DailySummaryTimeSettingItem(
                                title = "明日预告时间",
                                subtitle = "推送明日日程预告",
                                minuteOfDay = settings.dailySummaryEveningMinuteOfDay,
                                onClick = { showDailySummaryEveningTimePicker = true },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )
                        }
                    }
                                AppSettingsDivider()
                    SwitchSettingItem(
                        title = "实况通知",
                        subtitle = "日程开始时显示实况通知",
                        checked = settings.isLiveCapsuleEnabled,
                        onCheckedChange = { isChecked ->
                            requireNotificationWhenEnabling(isChecked) {
                                controller.updatePreference(liveCapsule = isChecked)
                                if (isChecked) showToast("实况通知已开启", ToastType.INFO)
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    AnimatedVisibility(
                        visible = settings.isLiveCapsuleEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                                AppSettingsDivider()
                            SwitchSettingItem(
                                title = "取件码聚合（Beta）",
                                subtitle = "当同时存在多个取件码时合并显示为一个实况通知",
                                checked = settings.isPickupAggregationEnabled,
                                onCheckedChange = { isChecked ->
                                    controller.updatePreference(pickupAggregation = isChecked)
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle
                            )
                        }
                        }


                    AppSettingsDivider()

                    val transitAutoCheckInOptions = MySettings.TRANSIT_AUTO_CHECK_IN_MINUTE_OPTIONS
                    val transitAutoCheckInMinutes = MySettings.normalizeTransitAutoCheckInMinutes(
                        settings.transitAutoCheckInMinutes
                    )
                    val transitAutoCheckInIndex = transitAutoCheckInOptions.indexOf(transitAutoCheckInMinutes)
                    EditionSwitchSliderSettingItem(
                        title = "列车与航班信息自动切换",
                        subtitle = if (settings.transitAutoCheckInEnabled) {
                            "出发前 ${transitAutoCheckInMinutes} 分钟自动标记为已检票或已登机"
                        } else {
                            "出发前自动切换到车次、航班和座位信息"
                        },
                        checked = settings.transitAutoCheckInEnabled,
                        onCheckedChange = { enabled ->
                            requireNotificationWhenEnabling(enabled) {
                                controller.updatePreference(transitAutoCheckInEnabled = enabled)
                            }
                        },
                        optionTitle = "自动切换时间",
                        optionSummary = "可选 10、15 或 30 分钟",
                        value = transitAutoCheckInIndex.toFloat(),
                        valueText = "${transitAutoCheckInMinutes} 分钟",
                        onValueChange = { value ->
                            val index = value.roundToInt().coerceIn(transitAutoCheckInOptions.indices)
                            controller.updatePreference(
                                transitAutoCheckInMinutes = transitAutoCheckInOptions[index]
                            )
                        },
                        valueRange = 0f..transitAutoCheckInOptions.lastIndex.toFloat(),
                        steps = transitAutoCheckInOptions.size - 2,
                        valueLabels = transitAutoCheckInOptions.map { "$it 分钟" },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                if (itemVisibility.showNetworkSpeedCapsule) {
                    AppSettingsDivider()

                    SwitchSettingItem(
                        title = "网速胶囊",
                        subtitle = "在状态栏显示下载速度",
                        checked = settings.isNetworkSpeedCapsuleEnabled,
                        onCheckedChange = { isChecked ->
                            requireNotificationWhenEnabling(isChecked) {
                                controller.updatePreference(networkSpeedCapsule = isChecked)
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "网速胶囊会覆盖其他胶囊显示",
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

            }



            // ================== 日程板块 ==================
            Text("日程", style = sectionTitleStyle)
            AppSettingsCard {
                    SwitchSettingItem(
                        title = "日历同步",
                        subtitle = "将日程同步到系统日历",
                        checked = syncStatus.isEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                permissionGate.require(
                                    permissionName = "日历读写权限",
                                    isGranted = ::hasCalendarPermission,
                                    requestPermission = ::requestCalendarPermission,
                                    onGranted = ::enableCalendarSync,
                                )
                            } else {
                                controller.toggleCalendarSync(false)
                                showToast("日历同步已关闭")
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    AnimatedVisibility(
                        visible = syncStatus.isEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                AppSettingsDivider()

                            ActionSettingItem(
                                title = "同步来源日历",
                                subtitle = selectedSourceSummary,
                                value = if (syncStatus.sourceCalendarIds.isEmpty()) {
                                    "未选择"
                                } else {
                                    "${syncStatus.sourceCalendarIds.size} 个"
                                },
                                enabled = true,
                                onClick = {
                                    permissionGate.require(
                                        permissionName = "日历读写权限",
                                        isGranted = ::hasCalendarPermission,
                                        requestPermission = ::requestCalendarPermission,
                                        onGranted = { showSourceCalendarSheet = true },
                                    )
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )

                                AppSettingsDivider()

                            SliderSettingItem(
                                title = "兜底同步频率",
                                subtitle = "仅作兜底轮询，优先即时监听",
                                value = syncStatus.syncIntervalSeconds.toFloat(),
                                onValueChange = { seconds ->
                                    controller.updateSyncIntervalSeconds(seconds.toInt())
                                },
                                valueRange = 1f..300f,
                                steps = 0,
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle,
                                showValueAsNumber = true,
                                valueUnit = "s"
                            )

                        }
                    }

                if (itemVisibility.showAutoArchive) {
                                AppSettingsDivider()
                    SwitchSettingItem(
                        title = "自动归档",
                        subtitle = "日程过期后立即自动归档",
                        checked = settings.autoArchiveEnabled,
                        onCheckedChange = { isChecked ->
                            controller.updatePreference(autoArchive = isChecked)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                }

                                AppSettingsDivider()
                    EventDurationSettingItem(
                        title = "日程默认持续时间",
                        subtitle = "所有识别日程都按该时长生成结束时间",
                        durationMinutes = settings.defaultEventDurationMinutes,
                        onClick = { showEventDurationPicker = true },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle
                    )
                if (itemVisibility.showScheduleColors) {
                    AppSettingsDivider()
                    ActionSettingItem(
                        title = "日程颜色",
                        subtitle = "自定义新建和识别日程使用的色盘",
                        value = "${settings.eventColorPaletteHex.size} 个",
                        enabled = true,
                        onClick = onNavigateToScheduleColors,
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle
                    )

                    AdvanceReminderSettingItem(
                        title = "日程提前提醒",
                        subtitle = if (settings.isAdvanceReminderEnabled)
                            "提前 ${settings.advanceReminderMinutes} 分钟"
                        else
                            "日程开始时",
                        checked = settings.isAdvanceReminderEnabled,
                        minutes = settings.advanceReminderMinutes,
                        onCheckedChange = { isChecked ->
                            requireNotificationWhenEnabling(isChecked) {
                                controller.updatePreference(advanceReminderEnabled = isChecked)
                                if (isChecked && settings.advanceReminderMinutes > 0) {
                                    val hasDuplicate = controller.hasDuplicateAdvanceReminder(settings.advanceReminderMinutes)
                                    if (hasDuplicate) {
                                        showToast("检测到可能存在的重复提醒", ToastType.INFO)
                                    }
                                }
                            }
                        },
                        onMinutesChange = { minutes ->
                            controller.updatePreference(advanceReminderMinutes = minutes)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                }
            }
        }

                SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp + bottomInset),
                snackbar = { data -> UniversalSnackbar(data = data, type = currentToastType) },
            )
        }
    }

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
            },
        )
    }

    if (showEventDurationPicker) {
        EventDurationPickerDialog(
            selectedDuration = settings.defaultEventDurationMinutes,
            onDismiss = { showEventDurationPicker = false },
            onConfirm = { duration ->
                controller.updatePreference(defaultEventDurationMinutes = duration)
                showEventDurationPicker = false
            },
        )
    }

    if (showDailySummaryMorningTimePicker) {
        DailySummaryTimePickerDialog(
            title = "今日提醒时间",
            selectedMinuteOfDay = settings.dailySummaryMorningMinuteOfDay,
            onDismiss = { showDailySummaryMorningTimePicker = false },
            onConfirm = { minuteOfDay ->
                controller.updateDailySummaryTimes(
                    morningMinuteOfDay = minuteOfDay,
                    onUpdated = { app?.runtimeCenter?.scheduleDailySummary() },
                )
                showDailySummaryMorningTimePicker = false
            },
        )
    }

    if (showDailySummaryEveningTimePicker) {
        DailySummaryTimePickerDialog(
            title = "明日预告时间",
            selectedMinuteOfDay = settings.dailySummaryEveningMinuteOfDay,
            onDismiss = { showDailySummaryEveningTimePicker = false },
            onConfirm = { minuteOfDay ->
                controller.updateDailySummaryTimes(
                    eveningMinuteOfDay = minuteOfDay,
                    onUpdated = { app?.runtimeCenter?.scheduleDailySummary() },
                )
                showDailySummaryEveningTimePicker = false
            },
        )
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
    return manager?.areNotificationsEnabled() == true
}


