package com.antgskds.calendarassistant.feature.quickmemo.ui.contract

/**
 * 记事页的分段：速记与闪记共用同一个底栏 Tab，用分段切换而不是拆两个 Tab。
 *
 * 两类内容数据模型差异很大（速记是扁平记录 + 提醒/语音/图片，闪记是段落流），
 * 因此不做「统一成同一个卡片」的强行合并，而是共用页面框架、各用各自合适的卡片。
 */
enum class MemoStreamSegment {
    /** 速记：语音/文字/图片速记，会触发日程建议。 */
    QUICK_MEMO,

    /** 闪记：网页/公众号/纯文本收藏，后台生成 AI 摘要。 */
    FLASH_NOTE;

    companion object {
        fun fromKey(key: String?): MemoStreamSegment =
            entries.firstOrNull { it.name == key } ?: QUICK_MEMO
    }
}
