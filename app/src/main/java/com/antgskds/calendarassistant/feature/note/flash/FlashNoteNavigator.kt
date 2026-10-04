package com.antgskds.calendarassistant.feature.note.flash

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 闪记条目点击后的跳转处理。
 *
 * 微信文章无法从外部 App 直接跳转到指定文章（微信未开放该 API），因此对这类链接
 * 改为复制到剪贴板并提示用户；其余链接正常唤起浏览器。
 */
object FlashNoteNavigator {

    /**
     * @return true 表示已处理（跳转或复制），false 表示无可用链接，调用方应退回详情页。
     */
    fun open(context: Context, url: String, requiresClipboardRelay: Boolean): Boolean {
        if (url.isBlank()) return false

        if (requiresClipboardRelay) {
            val copied = copyToClipboard(context, url)
            Toast.makeText(
                context,
                if (copied) "链接已复制，请在微信中粘贴打开" else "链接已复制",
                Toast.LENGTH_LONG
            ).show()
            return true
        }

        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            // 极少见：设备上没有可处理 http 的应用。此时兜底复制，避免点击无反应。
            copyToClipboard(context, url)
            Toast.makeText(context, "未找到浏览器，链接已复制", Toast.LENGTH_LONG).show()
            true
        }
    }

    private fun copyToClipboard(context: Context, text: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("闪记原文链接", text))
        return true
    }
}
