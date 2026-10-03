package com.antgskds.calendarassistant.feature.backup.ui.contract

import android.net.Uri
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2RuntimeStatus
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionInput
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionTestResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.AiPrompts
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupOptions
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BackupUiController {
    val settings: StateFlow<MySettings>
    val promptLocalVersion: StateFlow<Int>
    val promptSource: StateFlow<AiPrompts.PromptSource>
    val promptCheckInProgress: StateFlow<Boolean>
    val promptCheckFeedback: SharedFlow<PromptCheckFeedback>
    val webDavSyncStatus: StateFlow<SyncV2RuntimeStatus>
    fun getAttachmentCount(): Int
    fun estimateAttachmentBytes(): Long
    suspend fun exportBackupData(options: AppBackupOptions): String
    suspend fun exportBackupZip(uri: Uri, options: AppBackupOptions)
    suspend fun importBackupJson(content: String, options: AppBackupOptions): Result<AppBackupImportResult>
    suspend fun importBackupZip(uri: Uri, options: AppBackupOptions): Result<AppBackupImportResult>
    fun checkPromptUpdatesManually()
    fun refreshPromptInfo()

    // —— 多设备同步（WebDAV）——
    fun hasStoredWebDavPassword(): Boolean
    fun hasStoredSyncPassphrase(): Boolean
    fun readStoredWebDavPassword(): String
    fun readStoredSyncPassphrase(): String
    fun updateWebDavSyncOptions(enabled: Boolean? = null, wifiOnly: Boolean? = null, foregroundIntervalSeconds: Int? = null)
    suspend fun testAndSaveWebDavConnection(input: WebDavConnectionInput): WebDavConnectionTestResult
    fun syncWebDavNow()
}
