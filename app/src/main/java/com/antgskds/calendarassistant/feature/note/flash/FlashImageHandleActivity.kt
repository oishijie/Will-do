package com.antgskds.calendarassistant.feature.note.flash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.note.data.attachment.NoteAttachmentStore
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSource
import com.antgskds.calendarassistant.feature.note.flash.FlashNoteShareParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 图片闪记分享入口：收到系统分享的图片后存入 notes 附件目录并建条目。
 *
 * 按用户确认的方案**不做 OCR**，因此不生成摘要；分享文案里带链接时保留来源链接以便溯源。
 * 透明中转 Activity，不拉起主页面，收藏结果只以 Toast 反馈。
 */
class FlashImageHandleActivity : ComponentActivity() {

    private val app get() = application as App
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        if (running) return

        val uri = resolveSharedImageUri(intent)
        if (uri == null) {
            stop("未找到可导入的图片")
            return
        }
        running = true

        lifecycleScope.launch {
            try {
                val stored = withContext(Dispatchers.IO) {
                    NoteAttachmentStore.copyFromUri(this@FlashImageHandleActivity, uri)
                }
                if (!stored.isImage) {
                    withContext(Dispatchers.IO) { NoteAttachmentStore.delete(this@FlashImageHandleActivity, stored.relativePath) }
                    stop("只支持收藏图片文件")
                    return@launch
                }

                // 分享文案里可能同时带链接（如从浏览器分享图片），有则记录为来源。
                val source = FlashNoteShareParser.parse(
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT),
                    sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                )
                val noteId = app.noteCenter.createImageFlashNote(
                    attachmentPath = stored.relativePath,
                    attachmentName = stored.displayName,
                    attachmentMime = stored.mimeType,
                    sourceUrl = source?.url,
                    siteName = source?.siteName
                )

                Toast.makeText(
                    this@FlashImageHandleActivity,
                    if (noteId == null) "已在闪记中" else "已收藏图片",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                stop(e.message ?: "收藏失败")
                return@launch
            } finally {
                if (!isChangingConfigurations) finish()
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun resolveSharedImageUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            else -> null
        }
    }

    private fun stop(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
