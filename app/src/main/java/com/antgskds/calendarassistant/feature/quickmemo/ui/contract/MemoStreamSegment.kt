package com.antgskds.calendarassistant.feature.quickmemo.ui.contract

/**
 * 记事页的内容类型。
 *
 * 速记与闪记在同一个列表里按时间倒序混排，本枚举只作为顶部筛选器的值：
 * 选 [ALL] 看全部，选 [QUICK_MEMO] / [FLASH_NOTE] 只看其中一类。
 */
enum class MemoStreamSegment {
    /** 全部：速记 + 闪记混排。 */
    ALL,

    /** 速记：语音/文字/图片速记，会触发日程建议。 */
    QUICK_MEMO,

    /** 闪记：网页/公众号/纯文本/图片收藏，后台生成 AI 摘要。 */
    FLASH_NOTE;

    companion object {
        fun fromKey(key: String?): MemoStreamSegment =
            entries.firstOrNull { it.name == key } ?: ALL

        /** 该分段下应展示哪些类型。 */
        fun matches(segment: MemoStreamSegment, isFlash: Boolean): Boolean = when (segment) {
            ALL -> true
            QUICK_MEMO -> !isFlash
            FLASH_NOTE -> isFlash
        }
    }
}
