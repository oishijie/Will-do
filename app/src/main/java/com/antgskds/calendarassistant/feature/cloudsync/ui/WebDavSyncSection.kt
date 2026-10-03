package com.antgskds.calendarassistant.feature.cloudsync.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionInput
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionTestResult
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.shared.ui.material.settings.AppSettingsDivider
import com.antgskds.calendarassistant.shared.ui.material.settings.SliderSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.SwitchSettingItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 多设备同步（WebDAV）配置区块。
 *
 * 自带全部输入态（地址/账号/密码/同步密码）、连接测试与同步状态展示，
 * 调用方只需提供持久化动作与已存储标记，因此可挂到任意设置页而无需共享状态。
 */
@Composable
fun WebDavSyncSection(
    settings: MySettings,
    webDavPasswordStored: Boolean,
    syncPassphraseStored: Boolean,
    syncStatusMessage: String,
    pendingAssetCount: Int,
    testWebDavConnection: suspend (WebDavConnectionInput) -> WebDavConnectionTestResult,
    readStoredWebDavPassword: () -> String,
    readStoredSyncPassphrase: () -> String,
    onSyncEnabledChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onForegroundSyncIntervalChange: (Int) -> Unit,
    onSyncNow: () -> Unit,
    onTestResult: (success: Boolean, message: String) -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    modifier: Modifier = Modifier,
    focusManager: FocusManager = LocalFocusManager.current,
) {
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var baseUrl by remember(settings.webDavBaseUrl) { mutableStateOf(settings.webDavBaseUrl) }
    var username by remember(settings.webDavUsername) { mutableStateOf(settings.webDavUsername) }
    var password by remember { mutableStateOf("") }
    var passwordStored by remember(webDavPasswordStored) { mutableStateOf(webDavPasswordStored) }
    var syncPassphrase by remember { mutableStateOf("") }
    var syncPassphraseStored by remember(syncPassphraseStored) { mutableStateOf(syncPassphraseStored) }

    suspend fun onTestClick() {
        if (baseUrl.isBlank()) {
            onTestResult(false, "请填写 WebDAV 地址")
            return
        }
        if (password.isBlank() && !passwordStored) {
            onTestResult(false, "请填写 WebDAV 密码")
            return
        }
        if (syncPassphrase.isBlank() && !syncPassphraseStored) {
            onTestResult(false, "请填写同步密码")
            return
        }
        loading = true
        val result = try {
            testWebDavConnection(
                WebDavConnectionInput(
                    baseUrl = baseUrl,
                    username = username,
                    password = password,
                    syncPassphrase = syncPassphrase,
                )
            )
        } finally {
            loading = false
        }
        if (result.success) {
            if (password.isNotBlank()) passwordStored = true
            if (syncPassphrase.isNotBlank()) syncPassphraseStored = true
        }
        onTestResult(result.success, result.message)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WebDavConfigForm(
            baseUrl = baseUrl,
            username = username,
            password = password,
            passwordStored = passwordStored,
            syncPassphrase = syncPassphrase,
            syncPassphraseStored = syncPassphraseStored,
            syncEnabled = settings.webDavSyncEnabled,
            wifiOnly = settings.webDavWifiOnly,
            foregroundSyncIntervalSeconds = settings.webDavForegroundSyncIntervalSeconds,
            loading = loading,
            onBaseUrlChange = { baseUrl = it },
            onUsernameChange = { username = it },
            onPasswordChange = { password = it },
            onPasswordFocusChange = { focused ->
                if (focused && passwordStored && password.isBlank()) {
                    password = readStoredWebDavPassword()
                }
            },
            onSyncPassphraseChange = { syncPassphrase = it },
            onSyncPassphraseFocusChange = { focused ->
                if (focused && syncPassphraseStored && syncPassphrase.isBlank()) {
                    syncPassphrase = readStoredSyncPassphrase()
                }
            },
            onSyncEnabledChange = onSyncEnabledChange,
            onWifiOnlyChange = onWifiOnlyChange,
            onForegroundSyncIntervalChange = onForegroundSyncIntervalChange,
            onTest = {
                focusManager.clearFocus()
                scope.launch { onTestClick() }
            },
            cardTitleStyle = cardTitleStyle,
            cardValueStyle = cardValueStyle,
            cardSubtitleStyle = cardSubtitleStyle,
        )

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("同步状态", style = cardTitleStyle)
                Text(syncStatusMessage, style = cardSubtitleStyle)
                if (pendingAssetCount > 0) {
                    Text("$pendingAssetCount 个附件等待同步", style = cardSubtitleStyle)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onSyncNow) { Text("立即同步") }
                }
            }
        }
    }
}

@Composable
private fun WebDavConfigForm(
    baseUrl: String,
    username: String,
    password: String,
    passwordStored: Boolean,
    syncPassphrase: String,
    syncPassphraseStored: Boolean,
    syncEnabled: Boolean,
    wifiOnly: Boolean,
    foregroundSyncIntervalSeconds: Int,
    loading: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordFocusChange: (Boolean) -> Unit,
    onSyncPassphraseChange: (String) -> Unit,
    onSyncPassphraseFocusChange: (Boolean) -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onForegroundSyncIntervalChange: (Int) -> Unit,
    onTest: () -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            SwitchSettingItem(
                title = "多设备同步",
                subtitle = "同步日程、随口记及其附件",
                checked = syncEnabled,
                onCheckedChange = onSyncEnabledChange,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )

            SwitchSettingItem(
                title = "仅在 Wi-Fi 下同步",
                subtitle = "移动网络下暂停自动同步",
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChange,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            SliderSettingItem(
                title = "前台同步间隔",
                subtitle = "应用在前台时自动同步的时间间隔",
                value = foregroundSyncIntervalSeconds.toFloat(),
                onValueChange = {
                    onForegroundSyncIntervalChange(it.roundToInt().coerceIn(1, 300))
                },
                valueRange = 1f..300f,
                steps = 0,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                showValueAsNumber = true,
                valueUnit = " 秒",
            )
            AppSettingsDivider()
            WebDavTextInputItem(
                title = "服务器地址",
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                placeholder = "https://example.com/dav",
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            AppSettingsDivider()
            WebDavTextInputItem(
                title = "用户名",
                value = username,
                onValueChange = onUsernameChange,
                placeholder = "WebDAV 用户名",
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            AppSettingsDivider()
            WebDavTextInputItem(
                title = "WebDAV 密码",
                value = password,
                onValueChange = onPasswordChange,
                placeholder = if (passwordStored) "••••••••" else "点击输入密码",
                secret = true,
                onFocusChange = onPasswordFocusChange,
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            AppSettingsDivider()
            WebDavTextInputItem(
                title = "同步密码",
                value = syncPassphrase,
                onValueChange = onSyncPassphraseChange,
                placeholder = if (syncPassphraseStored) "••••••••" else "点击输入同步密码",
                secret = true,
                onFocusChange = onSyncPassphraseFocusChange,
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onTest,
                    enabled = !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("连接")
                    }
                }
            }
        }
    }
}

/**
 * WebDAV 区块专用的文本输入行。
 *
 * 沿用设置页输入行的既有形态（标题列 + 右对齐 BasicTextField，聚焦时明文、
 * 未聚焦时遮罩），但不扩散为公共组件，避免影响其他设置页的现有行为。
 */
@Composable
private fun WebDavTextInputItem(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    secret: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val visualTransformation = if (secret && value.isNotEmpty() && !isFocused) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = cardTitleStyle,
            modifier = Modifier.width(124.dp),
            maxLines = 1,
        )

        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                onValueChange(newValue.text)
            },
            textStyle = cardValueStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            ),
            visualTransformation = visualTransformation,
            singleLine = true,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    val focused = focusState.isFocused
                    if (focused != isFocused) {
                        isFocused = focused
                        onFocusChange?.invoke(focused)
                    }
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
