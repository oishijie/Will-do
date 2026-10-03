package com.antgskds.calendarassistant.feature.settings.quickmemo.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.quickmemo.data.asr.QuickMemoAsrModelStore
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalSnackbar
import com.antgskds.calendarassistant.shared.ui.permission.rememberPermissionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun hasRecordAudioPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun hasNotificationPermission(context: Context): Boolean {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    return manager?.areNotificationsEnabled() == true
}

/**
 * 随口记二级设置页。
 *
 * 承载原「通用偏好」里的整块随口记设置；权限申请与副作用（启停悬浮入口）
 * 从通用偏好原样迁入，行为不变。
 */
@Composable
fun QuickMemoSettingsPage(viewModel: SettingsViewModel, uiSize: Int = 2) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionGate = rememberPermissionGate(snackbarHostState)
    val settings by viewModel.settings.collectAsState()
    val bottomInset = LocalAppPageBottomPadding.current
    val app = context.applicationContext as? App

    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }
    var asrModelStatus by remember { mutableStateOf(QuickMemoAsrModelStore.status(context)) }

    fun showToast(message: String, type: ToastType = ToastType.SUCCESS) {
        currentToastType = type
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        permissionGate.resumePending()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        permissionGate.resumePending()
    }

    fun requestRecordAudioPermission() {
        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun requestNotificationPermission() {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val asrModelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                QuickMemoAsrModelStore.importModelFile(context, uri)
            }
            asrModelStatus = QuickMemoAsrModelStore.status(context)
            val message = result.fold(
                onSuccess = { fileName -> "已导入 $fileName" },
                onFailure = { error -> error.message ?: "模型导入失败" },
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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

    androidx.compose.runtime.CompositionLocalProvider(
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
                Text("随口记", style = sectionTitleStyle)

                QuickMemoPreferenceCard(
                    settings = settings,
                    asrModelStatus = asrModelStatus,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    onVoiceInputEnabledChange = { enabled ->
                        viewModel.updatePreference(voiceInputEnabled = enabled)
                        if (enabled) {
                            // 随口记依赖侧边栏/悬浮窗承载入口，启用时确保入口已就位。
                            app?.floatingCenter?.startEdgeBarServiceIfPermitted()
                        } else if (!settings.isFloatingWindowEnabled) {
                            app?.floatingCenter?.stopEdgeBarService()
                            app?.floatingCenter?.stopFloatingBallService()
                        }
                    },
                    onFloatingLongPressChange = { enabled ->
                        if (enabled) {
                            permissionGate.require(
                                permissionName = "麦克风权限",
                                isGranted = { hasRecordAudioPermission(context) },
                                requestPermission = ::requestRecordAudioPermission,
                                onGranted = { viewModel.updatePreference(floatingVoiceLongPressEnabled = true) },
                            )
                        } else {
                            viewModel.updatePreference(floatingVoiceLongPressEnabled = false)
                        }
                    },
                    onRecordingDisplayModeChange = { mode ->
                        viewModel.updatePreference(quickMemoRecordingDisplayMode = mode)
                    },
                    onAutoStopEnabledChange = { enabled ->
                        viewModel.updateQuickMemoAutoStop(enabled = enabled)
                    },
                    onAutoStopSecondsChange = { seconds ->
                        viewModel.updateQuickMemoAutoStop(seconds = seconds)
                    },
                    onTextAutoPinChange = { enabled ->
                        if (enabled) {
                            permissionGate.require(
                                permissionName = "通知权限",
                                isGranted = { hasNotificationPermission(context) },
                                requestPermission = ::requestNotificationPermission,
                                onGranted = { viewModel.updatePreference(floatingTextQuickMemoAutoPinEnabled = true) },
                            )
                        } else {
                            viewModel.updatePreference(floatingTextQuickMemoAutoPinEnabled = false)
                        }
                    },
                    onVoiceAutoPinChange = { enabled ->
                        if (enabled) {
                            permissionGate.require(
                                permissionName = "通知权限",
                                isGranted = { hasNotificationPermission(context) },
                                requestPermission = ::requestNotificationPermission,
                                onGranted = { viewModel.updatePreference(voiceQuickMemoAutoPinEnabled = true) },
                            )
                        } else {
                            viewModel.updatePreference(voiceQuickMemoAutoPinEnabled = false)
                        }
                    },
                    onImportAsrModel = { asrModelImportLauncher.launch(arrayOf("*/*")) },
                )
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
}
