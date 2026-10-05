package com.antgskds.calendarassistant.feature.note.flash
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSource

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSummaryState

/**
 * 闪记分享入口：透明中转 Activity，收到系统分享的链接后落库并提示。
 *
 * 不拉起主页面，避免打断用户当前浏览；收藏结果只以 Toast 反馈。
 */
class FlashNoteHandleActivity : ComponentActivity() {

    private val app get() = application as App
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        if (running) return

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val source = FlashNoteShareParser.parse(
            sharedText = sharedText,
            sharedSubject = sharedSubject
        )

        // 拿不到链接时不直接放弃：部分系统（如 ColorOS 的文字选择菜单）会把「选中文字」
        // 转成 ACTION_SEND 再抛给分享面板，此时 EXTRA_TEXT 里是纯文本、没有 URL。
        // 这类内容按纯文本闪记收藏，而不是提示"没有找到链接"。
        if (source == null) {
            val plainText = FlashNoteShareParser.extractPlainText(sharedText, sharedSubject)
            if (plainText == null) {
                stop("分享内容为空")
                return
            }
            saveAsTextFlashNote(plainText, sharedSubject)
            return
        }

        running = true
        lifecycleScope.launch {
            try {
                // 分享时前台窗口仍是原页面，直接取当前节点树抽正文。
                val extraction = FlashNoteContentExtractor.extractCurrentPage()
                val noteId = app.noteCenter.createFlashNote(
                    source = source,
                    content = extraction.content
                )
                if (noteId == null) {
                    Toast.makeText(this@FlashNoteHandleActivity, "已在闪记中", Toast.LENGTH_SHORT).show()
                } else {
                    // 微信文章无法从外部直接跳转，收藏时静默复制链接备用。
                    if (source.requiresClipboardRelay) copyToClipboard(source.url)
                    // 正文足够时才排队生成摘要，避免为空内容白耗模型额度。
                    if (!extraction.degraded) {
                        FlashNoteSummaryWorker.enqueue(
                            context = this@FlashNoteHandleActivity,
                            noteId = noteId,
                            title = source.articleTitle,
                            content = extraction.content
                        )
                    } else {
                        app.noteCenter.updateFlashSummary(noteId, FlashNoteSummaryState.FAILED)
                    }
                    val message = when {
                        extraction.content.isEmpty() -> "已收藏链接，正文未读取到"
                        extraction.degraded -> "已收藏，展开全文后可重新收藏以生成摘要"
                        else -> "已收藏，AI 摘要生成中"
                    }
                    Toast.makeText(this@FlashNoteHandleActivity, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                stop(e.message ?: "收藏失败")
                return@launch
            } finally {
                if (!isChangingConfigurations) finish()
            }
        }
    }

    /**
     * 降级：把没有链接的纯文本按「纯文本闪记」收藏。
     *
     * 走这条路的典型来源是系统文字选择菜单——部分 ROM（如 ColorOS）不直接发
     * PROCESS_TEXT，而是先把选中文字包成 ACTION_SEND 交给分享面板。
     */
    private fun saveAsTextFlashNote(text: String, subject: String?) {
        running = true
        lifecycleScope.launch {
            try {
                val noteId = app.noteCenter.createTextFlashNote(text = text, title = subject)
                if (noteId == null) {
                    Toast.makeText(this@FlashNoteHandleActivity, "已在闪记中", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val summaryEnabled = app.noteCenter.aiSummaryEnabled()
                if (summaryEnabled) {
                    FlashNoteSummaryWorker.enqueue(
                        context = this@FlashNoteHandleActivity,
                        noteId = noteId,
                        title = subject?.trim()?.takeIf { it.isNotBlank() }
                            ?: text.lineSequence().first { it.isNotBlank() }.trim().take(80),
                        content = text
                    )
                }
                Toast.makeText(
                    this@FlashNoteHandleActivity,
                    if (summaryEnabled) "已收藏文字，AI 摘要生成中" else "已收藏文字",
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

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("闪记原文链接", text))
    }

    private fun stop(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
