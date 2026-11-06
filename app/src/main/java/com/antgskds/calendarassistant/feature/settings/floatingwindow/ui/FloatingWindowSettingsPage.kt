package com.antgskds.calendarassistant.feature.settings.floatingwindow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.settings.data.model.QuickMemoRecordingDisplayMode
import kotlinx.coroutines.launch

/**
 * 音量键槽位可绑定的动作。
 *
 * id 必须与 TextAccessibilityService 的 ACTION_VOLUME_LONG_PRESS_* 常量逐一对齐：
 * 0=无 / 1=识屏 / 2=悬浮窗 / 3=悬浮窗录音 / 4=App内录音 / 5=App内笔记。
 *
 * 6（悬浮窗笔记）与 2（显示悬浮窗）执行的是同一个 startFloatingService，已从可选列表移除；
 * 7（按住说话）与 3（悬浮窗录音）共用同一个录音服务，只差停止方式，也已移除。
 * id 编号保留不变，否则旧数据里已绑定的动作会错位到别的功能上。
 */
enum class VolumeKeyAction(
    val id: Int,
    val label: String,
    val icon: ImageVector,
) {
    NONE(0, "无动作", Icons.Rounded.Block),
    SCREENSHOT(1, "识屏", Icons.Rounded.SmartDisplay),
    SHOW_FLOATING(2, "显示悬浮窗", Icons.Rounded.Bolt),
    FLOATING_RECORD(3, "悬浮窗录音", Icons.Rounded.Mic),
    QUICK_RECORD(4, "APP内录音", Icons.Rounded.GraphicEq),
    IN_APP_NOTE(5, "APP内笔记", Icons.Rounded.Edit);

    companion object {
        /** 6/7 已下线，按“未绑定”处理，避免老数据在界面上找不到选中项。 */
        fun fromId(id: Int): VolumeKeyAction = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** 四个按键槽位。 */
enum class VolumeKeySlot(val key: String, val title: String) {
    LONG_UP("long_up", "长按音量加"),
    LONG_DOWN("long_down", "长按音量减"),
    DOUBLE_UP("double_up", "双击音量加"),
    DOUBLE_DOWN("double_down", "双击音量减");

    companion object {
        fun fromKey(key: String): VolumeKeySlot = entries.firstOrNull { it.key == key } ?: LONG_UP
    }
}

/** 把手主题：与声物记 HandleTheme 对齐。 */
enum class HandleThemeOption(val key: String, val label: String, val top: Color, val bottom: Color) {
    DUO("DUO", "双色药丸", Color(0xFFF5F6F3), Color(0xFF2E9F5C)),
    BLUE_PURPLE("BLUE_PURPLE", "蓝紫", Color(0xFF6F9AF0), Color(0xFFAE82E4)),
    PILL3D("PILL3D", "拟物胶囊", Color(0xFFF7F8F6), Color(0xFFE0524E));

    companion object {
        fun fromKey(key: String): HandleThemeOption = entries.firstOrNull { it.key == key } ?: DUO
    }
}

/**
 * 胶囊设置拆成两页：TRIGGER = 唤起与快捷操作（音量键 + 录音与转写），
 * APPEARANCE = 胶囊外观与展示（把手外观 + 展示内容）。
 */
enum class FloatingSettingsSection { TRIGGER, APPEARANCE }

/**
 * 闪记胶囊设置页。
 *
 * 版式对齐声物记「音量键快捷操作」：大圆角分组卡。选项多（4 个及以上）的设置
 * 用「设置行 + 滚轮弹窗」，只有 2~3 个选项的保留内联选择。
 */
@Composable
fun FloatingWindowSettingsPage(
    viewModel: com.antgskds.calendarassistant.app.ui.state.SettingsViewModel,
    modifier: Modifier = Modifier,
    section: FloatingSettingsSection = FloatingSettingsSection.TRIGGER,
) {
    val settings by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val cardShape = RoundedCornerShape(28.dp)

    // 麦克风权限门：开启「悬浮窗长按闪记」前需要录音权限
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val permissionGate = com.antgskds.calendarassistant.shared.ui.permission.rememberPermissionGate(snackbarHostState)
    val recordAudioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {
        permissionGate.resumePending()
    }
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {
        permissionGate.resumePending()
    }

    // 语音转写模型：状态与导入器（从原闪记页整块迁入）
    var asrModelStatus by remember {
        mutableStateOf(
            com.antgskds.calendarassistant.feature.quickmemo.data.asr.QuickMemoAsrModelStore
                .status(context)
        )
    }
    val asrModelImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.antgskds.calendarassistant.feature.quickmemo.data.asr.QuickMemoAsrModelStore
                    .importModelFile(context, uri)
            }
            asrModelStatus = com.antgskds.calendarassistant.feature.quickmemo.data.asr
                .QuickMemoAsrModelStore.status(context)
            val message = result.fold(
                onSuccess = { fileName -> "已导入 $fileName" },
                onFailure = { error -> error.message ?: "模型导入失败" },
            )
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val volumeKeyEnabled = settings.volumeUpLongPressAction != 0 ||
        settings.volumeDownLongPressAction != 0 ||
        settings.volumeUpDoubleTapAction != 0 ||
        settings.volumeDownDoubleTapAction != 0 ||
        settings.volumeUpLongPressEnabled ||
        settings.volumeDownLongPressEnabled ||
        settings.volumeUpDoubleTapEnabled ||
        settings.volumeDownDoubleTapEnabled

    fun applyBinding(slot: VolumeKeySlot, action: VolumeKeyAction) {
        scope.launch {
            when (slot) {
                VolumeKeySlot.LONG_UP -> viewModel.updatePreference(
                    volumeUpLongPressEnabled = action != VolumeKeyAction.NONE,
                    volumeUpLongPressAction = action.id,
                )
                VolumeKeySlot.LONG_DOWN -> viewModel.updatePreference(
                    volumeDownLongPressEnabled = action != VolumeKeyAction.NONE,
                    volumeDownLongPressAction = action.id,
                )
                VolumeKeySlot.DOUBLE_UP -> viewModel.updatePreference(
                    volumeUpDoubleTapEnabled = action != VolumeKeyAction.NONE,
                    volumeUpDoubleTapAction = action.id,
                )
                VolumeKeySlot.DOUBLE_DOWN -> viewModel.updatePreference(
                    volumeDownDoubleTapEnabled = action != VolumeKeyAction.NONE,
                    volumeDownDoubleTapAction = action.id,
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    // 当前打开的滚轮弹窗：null = 未打开。
    var picker by remember { mutableStateOf<OptionPickerSpec?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (section == FloatingSettingsSection.TRIGGER) {
        // ① 唤起方式：整张卡片
        SettingsGroupCard(shape = cardShape) {
            // 状态行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (volumeKeyEnabled) "已开启" else "未设置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "在任意界面通过音量键手势快速唤起录音、笔记或悬浮窗",
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VolumeKeySlot.entries.forEach { slot ->
                val current = VolumeKeyAction.fromId(
                    when (slot) {
                        VolumeKeySlot.LONG_UP -> settings.volumeUpLongPressAction
                        VolumeKeySlot.LONG_DOWN -> settings.volumeDownLongPressAction
                        VolumeKeySlot.DOUBLE_UP -> settings.volumeUpDoubleTapAction
                        VolumeKeySlot.DOUBLE_DOWN -> settings.volumeDownDoubleTapAction
                    }
                )
                val slotTitle = when (slot) {
                    VolumeKeySlot.LONG_UP -> "长按音量加（约0.4秒）"
                    VolumeKeySlot.LONG_DOWN -> "长按音量减（约0.4秒）"
                    VolumeKeySlot.DOUBLE_UP -> "双击音量加（0.3秒内）"
                    VolumeKeySlot.DOUBLE_DOWN -> "双击音量减（0.3秒内）"
                }
                Spacer(Modifier.height(16.dp))
                PickerRow(
                    title = slotTitle,
                    value = current.label,
                    onClick = {
                        picker = OptionPickerSpec(
                            title = slot.title,
                            labels = VolumeKeyAction.entries.map { it.label },
                            selectedIndex = VolumeKeyAction.entries.indexOf(current),
                            onConfirm = { applyBinding(slot, VolumeKeyAction.entries[it]) },
                        )
                    },
                )
            }
        }

        }

        if (section == FloatingSettingsSection.APPEARANCE) {
        // ② 把手外观
        SettingsGroupCard(shape = cardShape) {
            SectionTitle("把手外观")
            Spacer(Modifier.height(12.dp))
            Text(
                text = "大小",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(100 to "标准", 75 to "小", 50 to "迷你").forEach { (value, label) ->
                    PillOption(
                        label = label,
                        selected = settings.floatingHandleSizePercent == value,
                        accent = accent,
                        onAccent = onAccent,
                        onClick = {
                            scope.launch { viewModel.updatePreference(floatingHandleSizePercent = value) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "主题",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HandleThemeOption.entries.forEach { theme ->
                    PillOption(
                        label = theme.label,
                        selected = settings.floatingHandleTheme == theme.key,
                        accent = accent,
                        onAccent = onAccent,
                        leading = {
                            Column {
                                Box(
                                    Modifier
                                        .size(11.dp)
                                        .background(theme.top, CircleShape)
                                )
                                Box(
                                    Modifier
                                        .size(11.dp)
                                        .background(theme.bottom, CircleShape)
                                )
                            }
                        },
                        onClick = {
                            scope.launch { viewModel.updatePreference(floatingHandleTheme = theme.key) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "停靠侧",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("LEFT" to "左侧", "RIGHT" to "右侧").forEach { (value, label) ->
                    PillOption(
                        label = label,
                        selected = settings.floatingExpandSide == value,
                        accent = accent,
                        onAccent = onAccent,
                        onClick = {
                            scope.launch { viewModel.updatePreference(floatingExpandSide = value) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "缩小收起态把手的视觉大小，触控区域不变；小档与迷你档不显示竖排文字。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            val fontOptions = listOf(-2 to "特小", -1 to "小", 0 to "标准", 1 to "大", 2 to "特大")
            PickerRow(
                title = "面板字体大小",
                subtitle = "每档增减 1sp，仅调整面板文字；把手和录音胶囊不变。",
                value = fontOptions.firstOrNull { it.first == settings.floatingFontSizeStep }?.second ?: "标准",
                onClick = {
                    picker = OptionPickerSpec(
                        title = "面板字体大小",
                        labels = fontOptions.map { it.second },
                        selectedIndex = fontOptions.indexOfFirst { it.first == settings.floatingFontSizeStep },
                        onConfirm = { index ->
                            scope.launch { viewModel.updatePreference(floatingFontSizeStep = fontOptions[index].first) }
                        },
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            val cardCountOptions = (6..10).toList()
            PickerRow(
                title = "面板高度",
                subtitle = "减少可见条数会下移顶部工具条，列表底部位置保持不变。",
                value = "${settings.floatingPanelMaxCards} 条",
                onClick = {
                    picker = OptionPickerSpec(
                        title = "面板高度",
                        labels = cardCountOptions.map { "$it 条" },
                        selectedIndex = cardCountOptions.indexOf(settings.floatingPanelMaxCards),
                        onConfirm = { index ->
                            scope.launch { viewModel.updatePreference(floatingPanelMaxCards = cardCountOptions[index]) }
                        },
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            val autoHideOptions = listOf(5 to "5秒", 10 to "10秒", 30 to "30秒", -1 to "永不")
            PickerRow(
                title = "自动隐藏",
                subtitle = "把手显示后超过设定时间自动收起，展开面板时不计时。",
                value = autoHideOptions.firstOrNull { it.first == settings.floatingHandleAutoHideSeconds }?.second
                    ?: "${settings.floatingHandleAutoHideSeconds}秒",
                onClick = {
                    picker = OptionPickerSpec(
                        title = "自动隐藏",
                        labels = autoHideOptions.map { it.second },
                        selectedIndex = autoHideOptions.indexOfFirst { it.first == settings.floatingHandleAutoHideSeconds },
                        onConfirm = { index ->
                            scope.launch {
                                viewModel.updatePreference(floatingHandleAutoHideSeconds = autoHideOptions[index].first)
                            }
                        },
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = "隐藏后保留贴边竖线",
                subtitle = "开启时缩成一条细线驻留，关闭则彻底隐藏悬浮窗",
                checked = settings.floatingEdgeLineEnabled,
                onChange = { enabled ->
                    scope.launch { viewModel.updatePreference(floatingEdgeLineEnabled = enabled) }
                },
            )
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = "点按竖线展开把手",
                subtitle = "关闭后只能向屏幕内侧滑动竖线或用音量键展开",
                checked = settings.floatingEdgeLineTapEnabled,
                onChange = { enabled ->
                    scope.launch { viewModel.updatePreference(floatingEdgeLineTapEnabled = enabled) }
                },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "竖线距屏幕边缘",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0 to "贴边", 4 to "内移", 8 to "最里").forEach { (margin, label) ->
                    PillOption(
                        label = label,
                        selected = settings.floatingEdgeLineMarginDp == margin,
                        accent = accent,
                        onAccent = onAccent,
                        onClick = {
                            scope.launch { viewModel.updatePreference(floatingEdgeLineMarginDp = margin) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "只移动竖线视觉位置，触控区域不变。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ③ 展示内容：胶囊列表里放什么
        SettingsGroupCard(shape = cardShape) {
            SectionTitle("展示内容")
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = "闪记",
                subtitle = "闪记功能总开关，关闭后胶囊只展示日程",
                checked = settings.voiceInputEnabled,
                onChange = { enabled ->
                    scope.launch { viewModel.updatePreference(voiceInputEnabled = enabled) }
                },
            )
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = "悬浮日程",
                subtitle = "胶囊列表中展示日程卡片",
                checked = settings.isFloatingWindowEnabled,
                onChange = { enabled ->
                    scope.launch { viewModel.updatePreference(floatingWindow = enabled) }
                },
            )
        }
        }

        if (section == FloatingSettingsSection.TRIGGER) {
        // ④ 录音与转写：胶囊里录音的方式与后续处理
        SettingsGroupCard(shape = cardShape) {
            SectionTitle("录音与转写")
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = "悬浮窗长按闪记",
                subtitle = "呼出胶囊后，再次长按音量加开始录音",
                checked = settings.floatingVoiceLongPressEnabled,
                onChange = { enabled ->
                    if (enabled) {
                        permissionGate.require(
                            permissionName = "麦克风权限",
                            isGranted = {
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            },
                            requestPermission = {
                                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                            onGranted = {
                                scope.launch {
                                    viewModel.updatePreference(floatingVoiceLongPressEnabled = true)
                                }
                            },
                        )
                    } else {
                        scope.launch {
                            viewModel.updatePreference(floatingVoiceLongPressEnabled = false)
                        }
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "录音展示",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "所有入口录音时使用的方式",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillOption(
                    label = "实况通知",
                    selected = settings.quickMemoRecordingDisplayMode == QuickMemoRecordingDisplayMode.LIVE_CAPSULE,
                    accent = accent,
                    onAccent = onAccent,
                    onClick = {
                        scope.launch {
                            viewModel.updatePreference(
                                quickMemoRecordingDisplayMode = QuickMemoRecordingDisplayMode.LIVE_CAPSULE
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                PillOption(
                    label = "悬浮窗",
                    selected = settings.quickMemoRecordingDisplayMode == QuickMemoRecordingDisplayMode.FLOATING_WINDOW,
                    accent = accent,
                    onAccent = onAccent,
                    onClick = {
                        scope.launch {
                            viewModel.updatePreference(
                                quickMemoRecordingDisplayMode = QuickMemoRecordingDisplayMode.FLOATING_WINDOW
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = "文本闪记同步挂起",
                subtitle = "文本闪记保存后，同步挂起到实况通知",
                checked = settings.floatingTextQuickMemoAutoPinEnabled,
                onChange = { enabled ->
                    scope.launch { viewModel.updatePreference(floatingTextQuickMemoAutoPinEnabled = enabled) }
                },
            )
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = "语音闪记同步挂起",
                subtitle = "语音闪记转写后，同步挂起到实况通知",
                checked = settings.voiceQuickMemoAutoPinEnabled,
                onChange = { enabled ->
                    val enable = enabled
                    if (enable) {
                        permissionGate.require(
                            permissionName = "通知权限",
                            isGranted = { notificationEnabled(context) },
                            requestPermission = {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            },
                            onGranted = {
                                scope.launch { viewModel.updatePreference(voiceQuickMemoAutoPinEnabled = true) }
                            },
                        )
                    } else {
                        scope.launch { viewModel.updatePreference(voiceQuickMemoAutoPinEnabled = false) }
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = "自动结束录音",
                subtitle = if (settings.quickMemoAutoStopEnabled) {
                    "录音超过 ${com.antgskds.calendarassistant.feature.settings.data.model.MySettings.normalizeQuickMemoAutoStopSeconds(settings.quickMemoAutoStopSeconds)} 秒后自动结束并保存"
                } else {
                    "关闭时由用户手动结束并保存录音"
                },
                checked = settings.quickMemoAutoStopEnabled,
                onChange = { enabled ->
                    scope.launch { viewModel.updateQuickMemoAutoStop(enabled = enabled) }
                },
            )
            Spacer(Modifier.height(16.dp))
            // 语音转写模型：导入/更换 ASR 模型
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "语音转写模型",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (asrModelStatus.ready) "已导入，语音闪记将自动转写" else "未导入模型，请依次导入 model.int8.onnx 和 tokens.txt",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(accent, RoundedCornerShape(18.dp))
                        .clickable { asrModelImportLauncher.launch(arrayOf("*/*")) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (asrModelStatus.ready) "更换模型" else "导入模型",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = onAccent,
                    )
                }
            }
        }

        }

        Spacer(Modifier.height(24.dp))
    }

        picker?.let { spec ->
            OptionPickerDialog(
                spec = spec,
                onDismiss = { picker = null },
            )
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

/** 整页分组卡：大圆角、浅底、内边距宽松（对齐参考图的呼吸感）。 */
@Composable
private fun SettingsGroupCard(
    shape: RoundedCornerShape,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}


/** 滚轮弹窗的一次打开参数。 */
private class OptionPickerSpec(
    val title: String,
    val labels: List<String>,
    val selectedIndex: Int,
    val onConfirm: (Int) -> Unit,
)

/** 标题 + 说明 + 右侧当前值，点按打开滚轮弹窗。 */
@Composable
private fun PickerRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    val haptics = com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { haptics.click(); onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 与「日程默认持续时间」同款：居中标题 + 滚轮 + 取消/确定。 */
@Composable
private fun OptionPickerDialog(
    spec: OptionPickerSpec,
    onDismiss: () -> Unit,
) {
    val initialIndex = spec.selectedIndex.coerceIn(0, spec.labels.lastIndex)
    var selectedIndex by remember(spec) { mutableStateOf(initialIndex) }
    val haptics = com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics()
    com.antgskds.calendarassistant.shared.ui.material.component.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { com.antgskds.calendarassistant.shared.ui.material.component.CenteredDialogTitle(spec.title) },
        text = {
            com.antgskds.calendarassistant.shared.ui.material.component.WheelPicker(
                items = spec.labels,
                initialIndex = initialIndex,
                onSelectionChanged = { selectedIndex = it },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                haptics.confirm()
                spec.onConfirm(selectedIndex)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { haptics.click(); onDismiss() }) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    )
}

@Composable
private fun PillOption(
    label: String,
    selected: Boolean,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .background(
                if (selected) accent else MaterialTheme.colorScheme.surface,
                shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) onAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 通知权限是否已开（系统通知总开关）。 */
private fun notificationEnabled(context: android.content.Context): Boolean {
    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
        as? android.app.NotificationManager
    return manager?.areNotificationsEnabled() == true
}
