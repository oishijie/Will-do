package com.antgskds.calendarassistant.platform.entry.text

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.antgskds.calendarassistant.App

/** 系统分享与文字选取入口，原文直接保存，不调用识别。 */
class TextShareQuickMemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        // 不因 Activity 重建再次处理同一外部请求。
        if (savedInstanceState != null) { finish(); return }
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> readProcessText()
            else -> null
        }?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "分享内容没有文字", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        (application as App).saveSharedTextToQuickMemo(text)
        finish()
    }

    /**
     * 不同厂商 ROM 对 PROCESS_TEXT 的 extra 键名不一致，一次性兜底读取。
     */
    private fun readProcessText(): CharSequence? {
        val candidates = listOf(
            Intent.EXTRA_PROCESS_TEXT,
            "android.content.extra.PROCESS_TEXT",
            "android.intent.extra.PROCESS_TEXT"
        )
        for (key in candidates) {
            val value = intent?.getCharSequenceExtra(key)?.toString()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
