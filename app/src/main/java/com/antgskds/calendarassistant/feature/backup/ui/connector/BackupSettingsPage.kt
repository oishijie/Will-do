package com.antgskds.calendarassistant.feature.backup.ui.connector

import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.edition.EditionCheckbox
import com.antgskds.calendarassistant.shared.ui.edition.EditionRadioButton
import com.antgskds.calendarassistant.shared.ui.edition.EditionOutlinedButton

import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.recognition.application.ai.AiPrompts
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupOptions
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.shared.ui.material.component.AppSheetAction
import com.antgskds.calendarassistant.shared.ui.material.component.AppModalBottomSheet
import com.antgskds.calendarassistant.shared.ui.material.component.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalToast
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.backup.ui.contract.BackupUiController
import com.antgskds.calendarassistant.feature.backup.ui.connector.BackupUiControllerAdapter
import com.antgskds.calendarassistant.feature.cloudsync.ui.WebDavSyncSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 数据备份页的两个分区：本地导入导出、多设备同步。 */
private val BACKUP_TABS = listOf("备份与恢复", "多设备同步")

@Composable
fun BackupSettingsPage(viewModel: SettingsViewModel, mainViewModel: MainViewModel, uiSize: Int = 2) {
    MaterialBackupSettingsScreen(remember(viewModel, mainViewModel) { BackupUiControllerAdapter(viewModel, mainViewModel) }, uiSize)
}

@Composable
fun MaterialBackupSettingsScreen(controller: BackupUiController, uiSize: Int = 2) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentSettings by controller.settings.collectAsState()
    val haptics = rememberAppHaptics(currentSettings.hapticFeedbackEnabled)
    val bottomInset = LocalAppPageBottomPadding.current

    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }
    var showImportMethodDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showBackupExportSheet by remember { mutableStateOf(false) }
    var showBackupImportSheet by remember { mutableStateOf(false) }
    var exportOptions by remember { mutableStateOf(AppBackupOptions(includeEvents = true)) }
    var importOptions by remember {
        mutableStateOf(
            AppBackupOptions(
                includeEvents = true,
                includeSettings = true,
                includeAttachments = true,
                includePrompts = true,
                includeQuickMemos = true
            )
        )
    }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // 备份与恢复 / 多设备同步 两个分区
    var selectedTab by remember { mutableStateOf(0) }
    val webDavStatus by controller.webDavSyncStatus.collectAsState()
    var pendingImportIsZip by remember { mutableStateOf(false) }
    val promptLocalVersion by controller.promptLocalVersion.collectAsState()
    val promptSource by controller.promptSource.collectAsState()
    val promptCheckInProgress by controller.promptCheckInProgress.collectAsState()
    var attachmentCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(showBackupExportSheet, showBackupImportSheet) {
        withContext(Dispatchers.IO) {
            val count = controller.getAttachmentCount()
            withContext(Dispatchers.Main) {
                attachmentCount = count
            }
        }
    }

    fun showToast(message: String, type: ToastType) {
        when (type) {
            ToastType.ERROR -> haptics.error()
            ToastType.SUCCESS -> haptics.confirm()
            else -> Unit
        }
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // --- 字体样式优化 ---
    // 板块标题：Primary + ExtraBold
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    // 卡片标题：OnSurface + Medium
    val cardTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    // 说明文字：Grey + Transparent
    val cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val contentBodyStyle = MaterialTheme.typography.bodyMedium

    // 监听 Prompt 检查反馈
    LaunchedEffect(controller) {
        controller.promptCheckFeedback.collect { feedback ->
            showToast(feedback.message, feedback.type)
        }
    }
    // 数据备份导出 JSON
    val exportBackupJsonLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonData = controller.exportBackupData(exportOptions)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(jsonData.toByteArray())
                    } ?: throw IOException("无法打开导出文件")
                    withContext(Dispatchers.Main) { showToast("数据备份导出成功", ToastType.SUCCESS) }
                } catch (e: Exception) { withContext(Dispatchers.Main) { showToast("导出失败: ${e.message}", ToastType.ERROR) } }
            }
        }
    }

    // 数据备份导出 ZIP
    val exportBackupZipLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    controller.exportBackupZip(uri, exportOptions)
                    withContext(Dispatchers.Main) { showToast("数据备份导出成功", ToastType.SUCCESS) }
                } catch (e: Exception) { withContext(Dispatchers.Main) { showToast("导出失败: ${e.message}", ToastType.ERROR) } }
            }
        }
    }

    // 数据备份导入
    val importBackupLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri).orEmpty()
            val name = uri.lastPathSegment.orEmpty()
            val isZip = type.contains("zip", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)
            pendingImportUri = uri
            pendingImportIsZip = isZip
            importOptions = AppBackupOptions(
                includeEvents = true,
                includeSettings = true,
                includeAttachments = isZip,
                includePrompts = true,
                includeQuickMemos = isZip
            )
            showBackupImportSheet = true
        }
    }

    // 提示词导出
    val exportPromptsLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonData = AiPrompts.exportToJson(context)
                    if (jsonData.isNotBlank()) {
                        context.contentResolver.openOutputStream(uri)?.use { output -> output.write(jsonData.toByteArray()) }
                        withContext(Dispatchers.Main) { showToast("提示词导出成功", ToastType.SUCCESS) }
                    } else {
                        withContext(Dispatchers.Main) { showToast("导出失败：无法获取提示词", ToastType.ERROR) }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast("导出失败: ${e.message}", ToastType.ERROR) }
                }
            }
        }
    }

    // 提示词导入
    val importPromptsLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val success = AiPrompts.importFromJson(context, content)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                showToast("提示词导入成功", ToastType.SUCCESS)
                                controller.refreshPromptInfo()
                            } else {
                                showToast("导入失败：格式无效", ToastType.ERROR)
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast("导入失败: ${e.message}", ToastType.ERROR) }
                }
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppHapticsEnabled provides currentSettings.hapticFeedbackEnabled) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            BACKUP_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        haptics.click()
                        selectedTab = index
                    },
                    text = { Text(title, style = MaterialTheme.typography.titleSmall) },
                )
            }
        }

        if (selectedTab == 0) {
        Text("数据管理", style = sectionTitleStyle)

            BackupCard(
                title = "数据备份",
                desc = "备份/恢复日程、闪记、设置和提示词",
                onExport = {
                    haptics.click()
                    exportOptions = AppBackupOptions(includeEvents = true)
                    showBackupExportSheet = true
                },
                onImport = { haptics.click(); importBackupLauncher.launch(arrayOf("application/json", "application/zip", "application/octet-stream")) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )

            Text("提示词管理", style = sectionTitleStyle)

            val promptSourceText = if (promptSource == AiPrompts.PromptSource.CLOUD) {
                "云端 v$promptLocalVersion"
            } else {
                "本地"
            }
            BackupCard(
                title = "提示词来源：$promptSourceText",
                desc = "导入/导出提示词，或检查云端更新",
                onExport = {
                    haptics.click()
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportPromptsLauncher.launch("ai_prompts_$timestamp.json")
                },
                onImport = { haptics.click(); importPromptsLauncher.launch(arrayOf("application/json")) },
                swapButtons = true, // 导出在左，导入在右
                extraButton = {
                    EditionOutlinedButton(
                        onClick = { haptics.confirm(); controller.checkPromptUpdatesManually() },
                        enabled = !promptCheckInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (promptCheckInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("检查中...")
                        } else {
                            Text("检查更新")
                        }
                    }
                },
                extraButtonText = "检查更新",
                extraButtonOnTop = false,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )
        } else {
            Text("多设备同步", style = sectionTitleStyle)

            WebDavSyncSection(
                settings = currentSettings,
                webDavPasswordStored = controller.hasStoredWebDavPassword(),
                syncPassphraseStored = controller.hasStoredSyncPassphrase(),
                syncStatusMessage = webDavStatus.message,
                pendingAssetCount = webDavStatus.pendingAssetCount,
                testWebDavConnection = controller::testAndSaveWebDavConnection,
                readStoredWebDavPassword = controller::readStoredWebDavPassword,
                readStoredSyncPassphrase = controller::readStoredSyncPassphrase,
                onSyncEnabledChange = { controller.updateWebDavSyncOptions(enabled = it) },
                onWifiOnlyChange = { controller.updateWebDavSyncOptions(wifiOnly = it) },
                onForegroundSyncIntervalChange = { controller.updateWebDavSyncOptions(foregroundIntervalSeconds = it) },
                onSyncNow = {
                    haptics.click()
                    controller.syncWebDavNow()
                },
                onTestResult = { success, message ->
                    showToast(message, if (success) ToastType.SUCCESS else ToastType.ERROR)
                },
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )

        if (showBackupExportSheet) {
            BackupOptionsSheet(
                title = "导出数据备份",
                description = "选择要写入备份文件的数据",
                options = exportOptions,
                attachmentCount = attachmentCount,
                confirmText = "导出",
                onOptionsChange = { exportOptions = it },
                onDismiss = { showBackupExportSheet = false },
                onConfirm = { selectedOptions ->
                    haptics.confirm()
                    showBackupExportSheet = false
                    val includeEvents = selectedOptions.includeEvents || selectedOptions.includeAttachments
                    val normalized = selectedOptions.copy(includeEvents = includeEvents, includeAttachments = includeEvents)
                    exportOptions = normalized
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    if (normalized.includeEvents || normalized.includeQuickMemos) {
                        exportBackupZipLauncher.launch("willdo_backup_$timestamp.zip")
                    } else {
                        exportBackupJsonLauncher.launch("willdo_backup_$timestamp.json")
                    }
                }
            )
        }

        if (showBackupImportSheet && pendingImportUri != null) {
            BackupOptionsSheet(
                title = "导入数据备份",
                description = if (pendingImportIsZip) "选择要从 ZIP 备份恢复的数据。" else "选择要从 JSON 备份恢复的数据。",
                options = importOptions,
                attachmentCount = 0,
                confirmText = "导入",
                onOptionsChange = { importOptions = it },
                onDismiss = {
                    showBackupImportSheet = false
                    pendingImportUri = null
                },
                onConfirm = { selectedOptions ->
                    haptics.confirm()
                    val uri = pendingImportUri ?: return@BackupOptionsSheet
                    showBackupImportSheet = false
                    scope.launch(Dispatchers.IO) {
                        val result = if (pendingImportIsZip) {
                            controller.importBackupZip(uri, selectedOptions)
                        } else {
                            val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                            controller.importBackupJson(jsonString, selectedOptions)
                        }
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) showToast(formatBackupImportResult(result.getOrThrow()), ToastType.SUCCESS)
                            else showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                        }
                    }
                    pendingImportUri = null
                }
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupOptionsSheet(
    title: String,
    description: String,
    options: AppBackupOptions,
    attachmentCount: Int,
    confirmText: String,
    onOptionsChange: (AppBackupOptions) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (AppBackupOptions) -> Unit
) {
    val normalizedIncludeEvents = options.includeEvents || options.includeAttachments
    val normalizedOptions = options.copy(
        includeEvents = normalizedIncludeEvents,
        includeAttachments = normalizedIncludeEvents
    )
    LaunchedEffect(normalizedOptions) {
        if (normalizedOptions != options) onOptionsChange(normalizedOptions)
    }
    val eventSubtitle = if (attachmentCount > 0) {
        "日程、提醒、归档状态、重复规则，以及 $attachmentCount 个日程附件"
    } else {
        "日程、提醒、归档状态、重复规则"
    }

    AppModalBottomSheet(
        title = title,
        subtitle = description,
        onDismissRequest = onDismiss,
        actions = listOf(AppSheetAction(
            text = confirmText,
            onClick = { onConfirm(normalizedOptions) },
            enabled = normalizedOptions.includeEvents || normalizedOptions.includeSettings ||
                normalizedOptions.includePrompts || normalizedOptions.includeQuickMemos,
        )),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BackupOptionRow(
                title = "日程数据",
                subtitle = eventSubtitle,
                checked = normalizedOptions.includeEvents,
                onCheckedChange = { onOptionsChange(normalizedOptions.copy(includeEvents = it, includeAttachments = it)) }
            )
            BackupOptionRow(
                title = "设置数据",
                subtitle = "包含模型、天气、主题等完整设置",
                checked = normalizedOptions.includeSettings,
                onCheckedChange = { onOptionsChange(normalizedOptions.copy(includeSettings = it)) }
            )
            BackupOptionRow(
                title = "闪记",
                subtitle = "包含闪记文本、状态、录音和图片",
                checked = normalizedOptions.includeQuickMemos,
                onCheckedChange = { onOptionsChange(normalizedOptions.copy(includeQuickMemos = it)) }
            )
            BackupOptionRow(
                title = "提示词",
                subtitle = "当前本地提示词配置",
                checked = normalizedOptions.includePrompts,
                onCheckedChange = { onOptionsChange(normalizedOptions.copy(includePrompts = it)) }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "设置备份会包含 API Key 等私密配置，请妥善保存备份文件。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BackupOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { haptics.selection(); onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditionCheckbox(checked = checked, enabled = enabled, onCheckedChange = { haptics.selection(); onCheckedChange(it) })
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatBackupImportResult(result: AppBackupImportResult): String {
    val parts = mutableListOf<String>()
    result.eventsResult?.let { importResult ->
        parts += "新增 ${importResult.successCount} 条日程"
        if (importResult.skippedCount > 0) parts += "跳过 ${importResult.skippedCount} 条"
        if (importResult.archiveStatusUpdateCount > 0) parts += "更新 ${importResult.archiveStatusUpdateCount} 条"
    }
    if (result.settingsImported) parts += "设置"
    if (result.promptsImported) parts += "提示词"
    if (result.attachmentsImported > 0) parts += "附件 ${result.attachmentsImported} 个"
    if (result.quickMemosImported > 0) parts += "闪记 ${result.quickMemosImported} 条"
    return if (parts.isEmpty()) "导入完成" else "导入成功：${parts.joinToString("，")}"
}

@Composable
fun BackupCard(
    title: String,
    desc: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    showExport: Boolean = true,
    importLabel: String = "导入",
    extraButton: @Composable (() -> Unit)? = null,
    extraButtonText: String = "检查更新",
    extraButtonOnTop: Boolean = false,
    swapButtons: Boolean = false, // 是否交换导出/导入按钮顺序（导出在左，导入在右）
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = cardTitleStyle)
            Text(desc, style = cardSubtitleStyle)
            Spacer(modifier = Modifier.height(16.dp))
            if (extraButton != null && extraButtonOnTop) {
                extraButton()
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditionOutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(importLabel)
                    }
                    if (showExport) {
                        EditionOutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导出")
                        }
                    }
                }
            } else if (extraButton != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (swapButtons) {
                        // 导出在左，导入在右
                        if (showExport) {
                            EditionOutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("导出")
                            }
                        }
                        EditionOutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(importLabel)
                        }
                    } else {
                        // 导入在左，导出在右
                        EditionOutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(importLabel)
                        }
                        if (showExport) {
                            EditionOutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("导出")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                extraButton()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showExport) {
                        EditionOutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导出")
                        }
                    }
                    EditionOutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(if (showExport) 1f else 1f)
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(importLabel)
                    }
                }
            }
        }
    }
}
