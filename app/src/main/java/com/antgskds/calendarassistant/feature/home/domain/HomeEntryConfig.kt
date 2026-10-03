package com.antgskds.calendarassistant.feature.home.domain

object HomeEntryKey {
    const val TODAY = "today"
    const val ALL = "all"
    const val NOTE = "note"
    const val SETTINGS = "settings"
}

/** 常驻底栏的固定入口顺序：今日日程 / 全部日程 / 随口记 / 设置。 */
val fixedHomeBottomItems: List<String> =
    listOf(HomeEntryKey.TODAY, HomeEntryKey.ALL, HomeEntryKey.NOTE, HomeEntryKey.SETTINGS)

/** 宽屏 NavigationRail 的日程页入口（不含设置，Rail 有独立设置路由）。 */
val railHomeItems: List<String> =
    listOf(HomeEntryKey.TODAY, HomeEntryKey.ALL, HomeEntryKey.NOTE)

/** 首页内容区支持滑动切换的页面（设置不参与横滑）。 */
val swipeableHomeItems: List<String> =
    listOf(HomeEntryKey.TODAY, HomeEntryKey.ALL, HomeEntryKey.NOTE)

fun homeEntryLabel(key: String): String {
    return when (key) {
        HomeEntryKey.TODAY -> "今日日程"
        HomeEntryKey.ALL -> "全部日程"
        HomeEntryKey.NOTE -> "随口记"
        HomeEntryKey.SETTINGS -> "设置"
        else -> key
    }
}
