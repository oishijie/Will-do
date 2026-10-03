package com.antgskds.calendarassistant.feature.settings.floating.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.PreferenceUiControllerAdapter
import com.antgskds.calendarassistant.shared.ui.edition.EditionDropdownActionSettingItem
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalSnackbar
import com.antgskds.calendarassistant.shared.ui.material.settings.SliderSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.SwitchSettingItem
import com.antgskds.calendarassistant.shared.ui.permission.rememberPermissionGate
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.collectAsState
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.feature.settings.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.shared.ui.interaction.HapticValueChangeEffect
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.antgskds.calendarassistant.platform.floating.EdgeBarService
import com.antgskds.calendarassistant.platform.floating.FloatingBallService
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.antgskds.calendarassistant.shared.ui.material.settings.AppSettingsDivider
import com.antgskds.calendarassistant.shared.ui.material.settings.SideChoiceSettingItem
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import com.antgskds.calendarassistant.shared.ui.material.component.AppAlertDialog
import com.antgskds.calendarassistant.shared.ui.material.component.CenteredDialogTitle
import com.antgskds.calendarassistant.shared.ui.material.component.WheelPicker
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.edition.EditionCategoricalPreference

/**
 * 悬浮与侧边栏二级设置页。
 *
 * 承载原「通用偏好」里的悬浮日程、侧边栏、悬浮球三组入口设置；
 * 权限申请与悬浮服务启停副作用从通用偏好原样迁入，行为不变。
 */
@Composable
fun FloatingEntrySettingsPage(viewModel: SettingsViewModel, uiSize: Int = 2) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionGate = rememberPermissionGate(snackbarHostState)
    val controller = remember(viewModel) { PreferenceUiControllerAdapter(viewModel) }
    val settings by controller.settings.collectAsState()
    val bottomInset = LocalAppPageBottomPadding.current
    val app = context.applicationContext as? App

    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var gestureActionPicker by remember { mutableStateOf<FloatingGestureActionPickerState?>(null) }

    fun showToast(message: String, type: ToastType = ToastType.SUCCESS) {
        currentToastType = type
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun refreshOverlayPermission() {
        hasOverlayPermission =
            app?.permissionCenter?.canDrawOverlays(context) ?: Settings.canDrawOverlays(context)
    }

    fun openOverlayPermissionSettings() {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshOverlayPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                Text("悬浮与侧边栏", style = sectionTitleStyle)

                AppSettingsCard {
                    SwitchSettingItem(
                        title = "悬浮日程",
                        subtitle = "在悬浮窗中显示日程",
                        checked = settings.isFloatingWindowEnabled,
                        onCheckedChange = { isChecked ->
                            val updateFloatingWindow = {
                                controller.updatePreference(
                                    floatingWindow = isChecked,
                                    edgeBarEnabled = if (isChecked || settings.voiceInputEnabled) settings.edgeBarEnabled else false,
                                    floatingBallEnabled = if (isChecked || settings.voiceInputEnabled) settings.floatingBallEnabled else false
                                )
                                if (!isChecked && !settings.voiceInputEnabled) {
                                    stopEdgeBarService()
                                    stopFloatingBallService()
                                } else {
                                    if (settings.edgeBarEnabled) startEdgeBarService()
                                    if (settings.floatingBallEnabled) startFloatingBallService()
                                }
                            }
                            if (isChecked) {
                                permissionGate.require(
                                    permissionName = "悬浮窗权限",
                                    isGranted = { hasOverlayPermission },
                                    requestPermission = ::openOverlayPermissionSettings,
                                    onGranted = updateFloatingWindow,
                                )
                            } else {
                                updateFloatingWindow()
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
            }

            AnimatedVisibility(
                visible = settings.isFloatingWindowEnabled || settings.voiceInputEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                AppSettingsCard {
                    if (settings.isFloatingWindowEnabled) {
                        FloatingEventRangeSlider(
                            title = "悬浮窗日程范围",
                            subtitle = when (settings.floatingEventRange) {
                                0 -> "显示全部日程"
                                1 -> "只显示今日日程"
                                2 -> "显示今日和明日日程"
                                else -> "显示全部日程"
                            },
                            eventRange = settings.floatingEventRange,
                            onEventRangeChange = { range ->
                                controller.updatePreference(floatingEventRange = range)
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        AppSettingsDivider()
                    }

                        SideChoiceSettingItem(
                            title = "悬浮窗展开方向",
                            subtitle = if (settings.floatingExpandSide == "LEFT") {
                                "所有入口从左侧滑入"
                            } else {
                                "所有入口从右侧滑入"
                            },
                            selectedSide = settings.floatingExpandSide,
                            onSideSelected = { side -> controller.updatePreference(floatingExpandSide = side) },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        AppSettingsDivider()

                        SwitchSettingItem(
                            title = "侧边栏入口",
                            subtitle = "在屏幕边缘显示侧边栏",
                            checked = settings.edgeBarEnabled,
                            onCheckedChange = { isChecked ->
                                val updateEdgeBar = {
                                    controller.updateEdgeBarSettings(enabled = isChecked)
                                    if (isChecked) {
                                        startEdgeBarService()
                                    } else {
                                        stopEdgeBarService()
                                    }
                                }
                                if (isChecked) {
                                    permissionGate.require(
                                        permissionName = "悬浮窗权限",
                                        isGranted = { hasOverlayPermission },
                                        requestPermission = ::openOverlayPermissionSettings,
                                        onGranted = updateEdgeBar,
                                    )
                                } else {
                                    updateEdgeBar()
                                }
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        AnimatedVisibility(
                            visible = settings.edgeBarEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                AppSettingsDivider()
                                SideChoiceSettingItem(
                                    title = "侧边栏位置",
                                    subtitle = if (settings.edgeBarSide == "LEFT") {
                                        "唤起条固定在屏幕左侧"
                                    } else {
                                        "唤起条固定在屏幕右侧"
                                    },
                                    selectedSide = settings.edgeBarSide,
                                    onSideSelected = { side -> controller.updateEdgeBarSettings(side = side) },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle
                        )

                        AppSettingsDivider()

                                FloatingGestureActionSettingItem(
                                    title = "单击动作",
                                    currentAction = settings.edgeBarSingleTapAction,
                                    onActionSelected = { controller.updatePreference(edgeBarSingleTapAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.EDGE_BAR,
                                            FloatingGestureActionSlot.SINGLE_TAP
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                        AppSettingsDivider()

                                FloatingGestureActionSettingItem(
                                    title = "双击动作",
                                    currentAction = settings.edgeBarDoubleTapAction,
                                    onActionSelected = { controller.updatePreference(edgeBarDoubleTapAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.EDGE_BAR,
                                            FloatingGestureActionSlot.DOUBLE_TAP
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                        AppSettingsDivider()

                                FloatingGestureActionSettingItem(
                                    title = "长按动作",
                                    currentAction = settings.edgeBarLongPressAction,
                                    onActionSelected = { controller.updatePreference(edgeBarLongPressAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.EDGE_BAR,
                                            FloatingGestureActionSlot.LONG_PRESS
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                        AppSettingsDivider()

                                SliderSettingItem(
                                    title = "纵向位置",
                                    subtitle = "上下位置百分比",
                                    value = settings.edgeBarYPercent,
                                    onValueChange = { controller.updateEdgeBarSettings(yPercent = it.roundToInt().toFloat()) },
                                    valueRange = 0f..100f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "%"
                                )

                                SliderSettingItem(
                                    title = "宽度",
                                    subtitle = "侧边条宽度",
                                    value = settings.edgeBarWidthDp.toFloat(),
                                    onValueChange = { controller.updateEdgeBarSettings(widthDp = it.roundToInt()) },
                                    valueRange = 4f..100f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "dp"
                                )

                                SliderSettingItem(
                                    title = "高度",
                                    subtitle = "侧边条高度",
                                    value = settings.edgeBarHeightDp.toFloat(),
                                    onValueChange = { controller.updateEdgeBarSettings(heightDp = it.roundToInt()) },
                                    valueRange = 60f..240f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "dp"
                                )

                                SliderSettingItem(
                                    title = "颜色深浅",
                                    subtitle = "调整透明度，0% 时完全透明",
                                    value = settings.edgeBarAlpha * 100f,
                                    onValueChange = { controller.updateEdgeBarSettings(alpha = it.roundToInt() / 100f) },
                                    valueRange = 0f..100f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "%"
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    AssistChip(
                                        onClick = {
                                            controller.updateEdgeBarSettings(
                                                enabled = true,
                                                side = "RIGHT",
                                                yPercent = 50f,
                                                widthDp = 8,
                                                heightDp = 120,
                                                alpha = 0.4f,
                                                singleTapAction = FloatingBallGestureAction.OPEN_FLOATING_SCHEDULE,
                                                doubleTapAction = FloatingBallGestureAction.QUICK_RECOGNITION,
                                                longPressAction = FloatingBallGestureAction.QUICK_MEMO_RECORDING
                                            )
                                            startEdgeBarService()
                                        },
                                        shape = RoundedCornerShape(50.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            labelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        border = null,
                                        label = { Text("恢复默认") }
                                    )
                                }
                            }
                        }

                        AppSettingsDivider()

                        SwitchSettingItem(
                            title = "悬浮球入口",
                            subtitle = "在屏幕中显示悬浮球",
                            checked = settings.floatingBallEnabled,
                            onCheckedChange = { isChecked ->
                                val updateFloatingBall = {
                                    controller.updatePreference(floatingBallEnabled = isChecked)
                                    if (isChecked) {
                                        startFloatingBallService()
                                    } else {
                                        stopFloatingBallService()
                                    }
                                }
                                if (isChecked) {
                                    permissionGate.require(
                                        permissionName = "悬浮窗权限",
                                        isGranted = { hasOverlayPermission },
                                        requestPermission = ::openOverlayPermissionSettings,
                                        onGranted = updateFloatingBall,
                                    )
                                } else {
                                    updateFloatingBall()
                                }
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        AnimatedVisibility(
                            visible = settings.floatingBallEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                AppSettingsDivider()
                                FloatingGestureActionSettingItem(
                                    title = "单击动作",
                                    currentAction = settings.floatingBallSingleTapAction,
                                    onActionSelected = { controller.updatePreference(floatingBallSingleTapAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.FLOATING_BALL,
                                            FloatingGestureActionSlot.SINGLE_TAP
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                                AppSettingsDivider()
                                FloatingGestureActionSettingItem(
                                    title = "双击动作",
                                    currentAction = settings.floatingBallDoubleTapAction,
                                    onActionSelected = { controller.updatePreference(floatingBallDoubleTapAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.FLOATING_BALL,
                                            FloatingGestureActionSlot.DOUBLE_TAP
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                                AppSettingsDivider()
                                FloatingGestureActionSettingItem(
                                    title = "长按动作",
                                    currentAction = settings.floatingBallLongPressAction,
                                    onActionSelected = { controller.updatePreference(floatingBallLongPressAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.FLOATING_BALL,
                                            FloatingGestureActionSlot.LONG_PRESS
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                                AppSettingsDivider()
                                SliderSettingItem(
                                    title = "尺寸",
                                    subtitle = "悬浮球直径",
                                    value = settings.floatingBallSizeDp.toFloat(),
                                    onValueChange = { controller.updatePreference(floatingBallSizeDp = it.roundToInt()) },
                                    valueRange = 44f..72f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "dp"
                                )

                                SliderSettingItem(
                                    title = "透明度",
                                    subtitle = "调整悬浮球可见程度",
                                    value = settings.floatingBallAlpha * 100f,
                                    onValueChange = { controller.updatePreference(floatingBallAlpha = it.roundToInt() / 100f) },
                                    valueRange = 0f..100f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "%"
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    AssistChip(
                                        onClick = {
                                            controller.updatePreference(
                                                floatingBallEnabled = true,
                                                floatingBallXPercent = 86f,
                                                floatingBallYPercent = 50f,
                                                floatingBallSizeDp = 56,
                                                floatingBallAlpha = 0.9f,
                                                floatingBallSingleTapAction = FloatingBallGestureAction.OPEN_QUICK_MEMO,
                                                floatingBallDoubleTapAction = FloatingBallGestureAction.QUICK_RECOGNITION,
                                                floatingBallLongPressAction = FloatingBallGestureAction.QUICK_MEMO_RECORDING
                                            )
                                            startFloatingBallService()
                                        },
                                        shape = RoundedCornerShape(50.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            labelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        border = null,
                                        label = { Text("恢复默认") }
                                    )
                                }
                            }
                        }
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

    gestureActionPicker?.let { request ->
        FloatingGestureActionPickerDialog(
            request = request,
            selectedAction = request.currentAction(settings),
            onDismiss = { gestureActionPicker = null },
            onActionSelected = { action ->
                when (request.target) {
                    FloatingGestureActionTarget.EDGE_BAR -> when (request.slot) {
                        FloatingGestureActionSlot.SINGLE_TAP -> controller.updatePreference(edgeBarSingleTapAction = action)
                        FloatingGestureActionSlot.DOUBLE_TAP -> controller.updatePreference(edgeBarDoubleTapAction = action)
                        FloatingGestureActionSlot.LONG_PRESS -> controller.updatePreference(edgeBarLongPressAction = action)
                    }
                    FloatingGestureActionTarget.FLOATING_BALL -> when (request.slot) {
                        FloatingGestureActionSlot.SINGLE_TAP -> controller.updatePreference(floatingBallSingleTapAction = action)
                        FloatingGestureActionSlot.DOUBLE_TAP -> controller.updatePreference(floatingBallDoubleTapAction = action)
                        FloatingGestureActionSlot.LONG_PRESS -> controller.updatePreference(floatingBallLongPressAction = action)
                    }
                }
                gestureActionPicker = null
            },
        )
    }
}

@Composable
private fun FloatingGestureActionSettingItem(
    title: String,
    currentAction: Int,
    onActionSelected: (Int) -> Unit,
    onNativeClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle,
) {
    val actions = FloatingBallGestureAction.ALL
    val normalizedAction = FloatingBallGestureAction.normalize(currentAction)
    EditionDropdownActionSettingItem(
        title = title,
        subtitle = "当前：${FloatingBallGestureAction.label(normalizedAction)}",
        options = actions.map { action ->
            if (action == FloatingBallGestureAction.NONE) "无" else FloatingBallGestureAction.label(action)
        },
        selectedIndex = actions.indexOf(normalizedAction).coerceAtLeast(0),
        onSelectedIndexChange = { onActionSelected(actions[it]) },
        icon = Icons.Default.ChevronRight,
        onNativeClick = onNativeClick,
        cardTitleStyle = cardTitleStyle,
        cardSubtitleStyle = cardSubtitleStyle,
        cardValueStyle = cardValueStyle,
    )
}

private data class FloatingGestureActionPickerState(
    val target: FloatingGestureActionTarget,
    val slot: FloatingGestureActionSlot
) {
    val title: String = "${target.title}${slot.title}"
}

private enum class FloatingGestureActionTarget(val title: String) {
    EDGE_BAR("侧边栏"),
    FLOATING_BALL("悬浮球")
}

private enum class FloatingGestureActionSlot(val title: String) {
    SINGLE_TAP("单击动作"),
    DOUBLE_TAP("双击动作"),
    LONG_PRESS("长按动作")
}

private fun FloatingGestureActionPickerState.currentAction(settings: MySettings): Int {
    return when (target) {
        FloatingGestureActionTarget.EDGE_BAR -> when (slot) {
            FloatingGestureActionSlot.SINGLE_TAP -> settings.edgeBarSingleTapAction
            FloatingGestureActionSlot.DOUBLE_TAP -> settings.edgeBarDoubleTapAction
            FloatingGestureActionSlot.LONG_PRESS -> settings.edgeBarLongPressAction
        }
        FloatingGestureActionTarget.FLOATING_BALL -> when (slot) {
            FloatingGestureActionSlot.SINGLE_TAP -> settings.floatingBallSingleTapAction
            FloatingGestureActionSlot.DOUBLE_TAP -> settings.floatingBallDoubleTapAction
            FloatingGestureActionSlot.LONG_PRESS -> settings.floatingBallLongPressAction
        }
    }
}

@Composable
private fun FloatingGestureActionPickerDialog(
    request: FloatingGestureActionPickerState,
    selectedAction: Int,
    onDismiss: () -> Unit,
    onActionSelected: (Int) -> Unit
) {
    val options = FloatingBallGestureAction.ALL
    val selectedActionIndex = options.indexOf(FloatingBallGestureAction.normalize(selectedAction)).coerceAtLeast(0)
    var selectedIndex by remember(request, selectedAction) { mutableIntStateOf(selectedActionIndex) }
    val haptics = rememberAppHaptics()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(request.title) },
        text = {
            WheelPicker(
                items = options.map { FloatingBallGestureAction.label(it) },
                initialIndex = selectedActionIndex,
                onSelectionChanged = { selectedIndex = it }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.confirm()
                    onActionSelected(options[selectedIndex])
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { haptics.click(); onDismiss() }) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun FloatingEventRangeSlider(
    title: String,
    subtitle: String,
    eventRange: Int,
    onEventRangeChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    HapticValueChangeEffect(valueKey = eventRange)
    EditionCategoricalPreference(
        title = title,
        summary = subtitle,
        options = listOf("全部日程", "今日日程", "今日+明日"),
        selectedIndex = eventRange.coerceIn(0, 2),
        onSelectedIndexChange = onEventRangeChange,
        titleTextStyle = cardTitleStyle,
        summaryTextStyle = cardSubtitleStyle,
        showSelectedValue = false,
        labelsAboveSlider = true,
        sliderTopPadding = 12.dp,
    )
}
