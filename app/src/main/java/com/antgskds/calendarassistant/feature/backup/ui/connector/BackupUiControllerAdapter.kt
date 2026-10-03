package com.antgskds.calendarassistant.feature.backup.ui.connector

import android.net.Uri
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupOptions
import com.antgskds.calendarassistant.feature.backup.ui.contract.BackupUiController
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionInput
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel

class BackupUiControllerAdapter(
    private val settingsViewModel: SettingsViewModel,
    private val mainViewModel: MainViewModel
) : BackupUiController {
    override val settings = settingsViewModel.settings
    override val promptLocalVersion = mainViewModel.promptLocalVersion
    override val promptSource = mainViewModel.promptSource
    override val promptCheckInProgress = mainViewModel.promptCheckInProgress
    override val promptCheckFeedback = mainViewModel.promptCheckFeedback
    override fun getAttachmentCount() = settingsViewModel.getAttachmentCount()
    override fun estimateAttachmentBytes() = settingsViewModel.estimateAttachmentBytes()
    override suspend fun exportBackupData(options: AppBackupOptions) = settingsViewModel.exportBackupData(options)
    override suspend fun exportBackupZip(uri: Uri, options: AppBackupOptions) = settingsViewModel.exportBackupZip(uri, options)
    override suspend fun importBackupJson(content: String, options: AppBackupOptions): Result<AppBackupImportResult> = settingsViewModel.importBackupJson(content, options)
    override suspend fun importBackupZip(uri: Uri, options: AppBackupOptions): Result<AppBackupImportResult> = settingsViewModel.importBackupZip(uri, options)
    override fun checkPromptUpdatesManually() = mainViewModel.checkPromptUpdatesManually()
    override fun refreshPromptInfo() = mainViewModel.refreshPromptInfo()

    // —— 多设备同步（WebDAV）——
    override val webDavSyncStatus = settingsViewModel.webDavSyncStatus
    override fun hasStoredWebDavPassword() = settingsViewModel.hasStoredWebDavPassword()
    override fun hasStoredSyncPassphrase() = settingsViewModel.hasStoredSyncPassphrase()
    override fun readStoredWebDavPassword() = settingsViewModel.readStoredWebDavPassword()
    override fun readStoredSyncPassphrase() = settingsViewModel.readStoredSyncPassphrase()
    override fun updateWebDavSyncOptions(
        enabled: Boolean?,
        wifiOnly: Boolean?,
        foregroundIntervalSeconds: Int?,
    ) = settingsViewModel.updateWebDavSyncOptions(enabled, wifiOnly, foregroundIntervalSeconds)
    override suspend fun testAndSaveWebDavConnection(input: WebDavConnectionInput) =
        settingsViewModel.testAndSaveWebDavConnection(input)
    override fun syncWebDavNow() = settingsViewModel.syncWebDavNow()
}
