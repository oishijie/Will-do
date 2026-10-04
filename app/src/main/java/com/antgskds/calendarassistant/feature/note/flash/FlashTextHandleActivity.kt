package com.antgskds.calendarassistant.feature.note.flash

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSummaryState
import com.antgskds.calendarassistant.feature.note.flash.FlashNoteSummaryWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 纯文本闪记入口：在任意 App 里选中文字 → 「分享 → 闪记」即收藏。
 *
 * 走 [android.content.Intent.ACTION_PROCESS_TEXT]，与其他分享目标互不干扰，
 * 也不需要长按分享面板；选中文字后系统菜单里会出现「闪记」。
 */
class FlashTextHandleActivity : ComponentActivity() {

    private val app get() = application as App
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        if (running) return

        val selected = readSelectedText()
        if (selected.isNullOrBlank()) {
            stop("没有选中文字")
            return
        }
        running = true

        lifecycleScope.launch {
            try {
                val noteId = app.noteCenter.createTextFlashNote(text = selected)
                if (noteId == null) {
                    Toast.makeText(this@FlashTextHandleActivity, "已在闪记中", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                // 纯文本内容已完整入库，直接排队生成摘要。
                FlashNoteSummaryWorker.enqueue(
                    context = this@FlashTextHandleActivity,
                    noteId = noteId,
                    title = selected.lineSequence().first { it.isNotBlank() }.trim().take(80),
                    content = selected
                )
                Toast.makeText(
                    this@FlashTextHandleActivity,
                    "已收藏，AI 摘要生成中",
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

    /**
     * 读取选中文字。
     *
     * 不同厂商 ROM 对 PROCESS_TEXT 的 extra 键名不一致（`EXTRA_PROCESS_TEXT` 是标准键，
     * 但部分系统会带 `android.content.extra.PROCESS_TEXT`），这里一次性兜底读取。
     */
    private fun readSelectedText(): String? {
        val candidates = listOf(
            EXTRA_PROCESS_TEXT,
            "android.content.extra.PROCESS_TEXT",
            "android.intent.extra.PROCESS_TEXT"
        )
        for (key in candidates) {
            val value = intent?.getCharSequenceExtra(key)?.toString()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun stop(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        /** [android.content.Intent.EXTRA_PROCESS_TEXT] 的实际字面量。 */
        const val EXTRA_PROCESS_TEXT = "android.intent.extra.PROCESS_TEXT"
    }
}
