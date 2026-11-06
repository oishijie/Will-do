package com.antgskds.calendarassistant.platform.quickmemo

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoExportFile
import com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoExportSnapshot
import com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoExportWriter
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.io.File
import java.util.UUID

/**
 * 导出临时文件的落盘与分享。
 *
 * 附件必须在应用自己的存储里：导出会把附件一并打进 ZIP，路径越界直接拒绝，
 * 避免把用户系统里的任意文件打进来。
 */
class QuickMemoExportFiles(private val context: Context) {
    private val directory = File(context.cacheDir, "quickmemo-export")

    fun prepare(snapshot: QuickMemoExportSnapshot): QuickMemoExportFile {
        val roots = listOf(context.filesDir, context.cacheDir) +
            context.getExternalFilesDirs(null).filterNotNull()
        QuickMemoExportWriter.attachmentFiles(snapshot.memo).forEach { (_, file) ->
            require(roots.any { file.canonicalFile.toPath().startsWith(it.canonicalFile.toPath()) }) {
                "附件不在应用存储中"
            }
        }
        directory.mkdirs()
        val now = System.currentTimeMillis()
        directory.listFiles()
            ?.filter { it.isDirectory && now - it.lastModified() > ConfigCatalog.QUICK_MEMO_EXPORT_RETENTION_MS }
            ?.forEach {
                if (it.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath())) {
                    it.deleteRecursively()
                }
            }
        return QuickMemoExportWriter.write(snapshot, File(directory, UUID.randomUUID().toString()))
    }

    fun share(export: QuickMemoExportFile) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            export.file
        )
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = export.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("闪记", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "分享闪记"
            )
        )
    }

    fun save(export: QuickMemoExportFile, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            export.file.inputStream().use { it.copyTo(output) }
        } ?: error("无法写入所选文件")
    }

    fun discard(export: QuickMemoExportFile) {
        if (export.file.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath())) {
            export.file.delete()
            export.file.parentFile?.delete()
        }
    }
}
