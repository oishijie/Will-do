package com.antgskds.calendarassistant.feature.note.domain.flash

/**
 * 闪记摘要状态。
 *
 * 摘要由后台 Worker 异步生成，条目先落库再回填，因此需要把「尚未生成」与
 * 「已失败」这两态持久化到段落文本里，避免App 被杀后丢失状态。
 */
object FlashNoteSummaryState {
    /** 已排队或生成中。 */
    const val PENDING = "__PENDING__"

    /** 生成失败且已达重试上限，不再自动重试。 */
    const val FAILED = "__FAILED__"

    /**
     * 不生成摘要。
     *
     * 图片闪记按用户确认的方案不做 OCR，因此永远没有可摘要的文本；
     * 用独立状态而非空串，避免与「生成失败」混淆。
     */
    const val SKIPPED = "__SKIPPED__"

    fun isPlaceholder(text: String): Boolean =
        text == PENDING || text == FAILED || text == SKIPPED
}

/** 闪记来源站点类别，决定点击条目时的跳转方式。 */
enum class FlashNoteSourceKind {
    /** 普通网页，可直接唤起浏览器打开。 */
    WEB,

    /** 微信公众号文章。微信无公开 API 支持外部跳转到指定文章，只能复制链接后在微信内粘贴打开。 */
    WECHAT,

    /** 其他来源（如应用内链接），按普通网页处理。 */
    OTHER
}

/**
 * 一条闪记的来源信息。
 *
 * @param url 原文链接，也是去重依据。
 * @param siteName 来源站点名（如「微信公众号」）。
 * @param articleTitle 文章标题，来自分享时携带的标题或页面 title。
 */
data class FlashNoteSource(
    val url: String,
    val siteName: String = "",
    val articleTitle: String = ""
) {
    val kind: FlashNoteSourceKind = when {
        url.contains("mp.weixin.qq.com", ignoreCase = true) -> FlashNoteSourceKind.WECHAT
        url.startsWith("http://") || url.startsWith("https://") -> FlashNoteSourceKind.WEB
        else -> FlashNoteSourceKind.OTHER
    }

    /** 微信文章无法直接跳转，需要复制链接后在微信内打开。 */
    val requiresClipboardRelay: Boolean get() = kind == FlashNoteSourceKind.WECHAT
}

/**
 * 摘要输入。摘要 Worker 只需要正文，不需要标题与链接。
 */
data class FlashNoteSummaryRequest(
    val noteId: Long,
    val title: String,
    val content: String
)

/** 正文抽取结果。[degraded] 为 true 表示正文不足，需要提示用户展开全文后重新收藏。 */
data class FlashNoteExtraction(
    val content: String,
    val degraded: Boolean
) {
    companion object {
        /** 正文长度阈值：低于此值视为未展开全文（微信文章默认折叠）。 */
        const val MIN_CONTENT_LENGTH = 200
    }
}
