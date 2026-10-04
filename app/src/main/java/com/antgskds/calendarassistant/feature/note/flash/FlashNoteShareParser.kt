package com.antgskds.calendarassistant.feature.note.flash
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSource
import java.net.URI

/**
 * 从分享 Intent 中解析闪记来源。
 *
 * 分享来源五花八门（浏览器、微信、短信），这里只做尽力而为的提取：
 * 拿不到 URL 就直接放弃，不生成空条目。
 */
object FlashNoteShareParser {

    private val URL_PATTERN = Regex(
        """(?:https?://|www\.)[^\s，。；、"'<>）】]+""",
        RegexOption.IGNORE_CASE
    )

    /** 分享文案里常见的「标题 链接」噪声前缀，提取后需清理。 */
    private val TITLE_NOISE = Regex("""^(分享|收藏|推荐|来自)[:：\s]*""")

    private val WECHAT_HOSTS = setOf("mp.weixin.qq.com")

    /** 纯文本降级时的正文长度上限，防止超长文本撑爆单条笔记。 */
    private const val MAX_PLAIN_TEXT_LENGTH = 20000

    /**
     * 解析结果。[url] 为空表示无法提取，返回 null。
     */
    fun parse(sharedText: String?, sharedSubject: String?): FlashNoteSource? {
        val url = extractUrl(sharedText) ?: extractUrl(sharedSubject) ?: return null
        val normalized = normalizeUrl(url)
        val title = cleanTitle(sharedSubject) ?: cleanTitle(sharedText) ?: normalized.host.orEmpty()
        val source = FlashNoteSource(
            url = normalized.toString(),
            siteName = siteNameOf(normalized),
            articleTitle = title.take(120)
        )
        return source
    }

    /**
     * 提取可作为纯文本闪记收藏的内容。
     *
     * 用于降级路径：系统文字选择菜单（尤其 ColorOS）会把「选中文字」包成
     * `ACTION_SEND` 交给分享面板，此时 `EXTRA_TEXT` 里是没有链接的纯文本。
     * 分享文案里混有链接时，[parse] 会先走网页分支，这里只在无链接时被调用。
     *
     * @return 去首尾空白并限长后的文本；无可用内容时返回 null。
     */
    fun extractPlainText(sharedText: String?, sharedSubject: String?): String? {
        val body = sharedText?.trim().orEmpty()
        if (body.isEmpty()) return null
        // 标题去掉其中的链接片段，避免正文里混入 URL。
        val title = cleanTitle(sharedSubject).orEmpty()
        val content = if (title.isNotEmpty() && body.startsWith(title)) {
            body.removePrefix(title).trim().ifEmpty { body }
        } else {
            body
        }
        return content.takeIf { it.isNotBlank() }?.take(MAX_PLAIN_TEXT_LENGTH)
    }

    /** 从分享文案里截取第一个 URL，并去掉尾随的中英文标点。 */
    private fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return URL_PATTERN.find(text)?.value
    }

    /**
     * 规范化 URL：补全协议、去掉末尾标点。
     * 用 [URI] 解析而非字符串拼接，避免手写正则漏掉query 中的特殊字符。
     */
    private fun normalizeUrl(raw: String): URI {
        val withScheme = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
        val uri = runCatching { URI(withScheme) }.getOrNull()
            ?: return URI.create("https://invalid.invalid/")
        val path = uri.rawPath.orEmpty().trimEnd('/', '.', ',', ';', '!', '?', '&')
        return uri.resolve(path.ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: ""))
    }

    /** 清理标题：去噪声前缀、去首尾空白。 */
    private fun cleanTitle(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
            .replace(TITLE_NOISE, "")
            .replace(URL_PATTERN, "")
            .trim()
        return trimmed.ifBlank { null }
    }

    private fun siteNameOf(uri: URI): String {
        val host = uri.host?.lowercase().orEmpty()
        if (host in WECHAT_HOSTS) return "微信公众号"
        val bare = host.removePrefix("www.")
        return bare.ifBlank { "网页" }
    }
}
