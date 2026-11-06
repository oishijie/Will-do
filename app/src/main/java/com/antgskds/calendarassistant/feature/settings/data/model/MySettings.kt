package com.antgskds.calendarassistant.feature.settings.data.model

import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey
import com.antgskds.calendarassistant.platform.widget.model.WidgetThemeMode
import kotlinx.serialization.Serializable

object LiveNotificationTemplateMode {
    const val AUTO = "AUTO"
    const val FULL = "FULL"
    const val COMPACT = "COMPACT"

    val ALL = listOf(AUTO, FULL, COMPACT)

    fun normalize(value: String): String {
        return if (value in ALL) value else AUTO
    }
}

object HomeCalendarViewModeName {
    const val TODAY = "TODAY"
    const val WEEK = "WEEK"
    const val MONTH = "MONTH"
    const val AGENDA = "AGENDA"

    val ALL = listOf(TODAY, WEEK, MONTH, AGENDA)

    fun normalize(value: String): String = if (value in ALL) value else TODAY
}

object RecognitionMode {
    const val AI_ONLY = 0
    const val REGEX_ONLY = 1
    const val REGEX_THEN_AI_ON_EMPTY = 2
    const val REGEX_THEN_AI_REVIEW = 3

    val ALL = listOf(AI_ONLY, REGEX_ONLY, REGEX_THEN_AI_ON_EMPTY, REGEX_THEN_AI_REVIEW)

    fun normalize(value: Int): Int {
        return if (value in ALL) value else AI_ONLY
    }

    fun label(value: Int): String {
        return when (normalize(value)) {
            AI_ONLY -> "仅 AI"
            REGEX_ONLY -> "仅正则"
            REGEX_THEN_AI_ON_EMPTY -> "正则优先，失败后 AI"
            REGEX_THEN_AI_REVIEW -> "正则先入库，AI 后台修正"
            else -> "仅 AI"
        }
    }

    fun description(value: Int): String {
        return when (normalize(value)) {
            AI_ONLY -> "沿用当前 AI 识别链路"
            REGEX_ONLY -> "只使用本地正则规则，不消耗 AI 额度"
            REGEX_THEN_AI_ON_EMPTY -> "先用正则，未匹配时再调用 AI"
            REGEX_THEN_AI_REVIEW -> "正则先创建日程，AI 后台复核并修正，会消耗 AI 额度"
            else -> "沿用当前 AI 识别链路"
        }
    }
}

val DEFAULT_EVENT_COLOR_PALETTE_HEX = listOf(
    "#91A3B0",
    "#B4C3A1",
    "#D1B29E",
    "#968D8D",
    "#BCCAD6",
    "#CFD1D3",
    "#A2B5BB",
    "#E2C4C4"
)

private val EVENT_COLOR_HEX_PATTERN = Regex("[0-9A-Fa-f]{6}")

fun normalizeEventColorHex(hex: String): String? {
    val normalized = hex.trim().removePrefix("#")
    if (!normalized.matches(EVENT_COLOR_HEX_PATTERN)) return null
    return "#${normalized.uppercase()}"
}

fun sanitizeEventColorPaletteHex(colors: List<String>): List<String> {
    val normalized = colors.mapNotNull(::normalizeEventColorHex).distinct()
    return normalized.ifEmpty { DEFAULT_EVENT_COLOR_PALETTE_HEX }
}

fun eventColorHexToArgb(hex: String, fallback: Int = 0xFF91A3B0.toInt()): Int {
    val normalized = normalizeEventColorHex(hex) ?: return fallback
    val rgb = normalized.removePrefix("#").toLongOrNull(16) ?: return fallback
    return (0xFF000000 or rgb).toInt()
}

fun eventColorPaletteToArgb(colors: List<String>): List<Int> =
    sanitizeEventColorPaletteHex(colors).map(::eventColorHexToArgb)

@Serializable
data class MySettings(
    // AI 模型配置
    val modelKey: String = "",
    val modelName: String = "",
    val modelUrl: String = "",
    val modelProvider: String = "", // 保留旧字段，防止数据丢失
    // 仅为旧备份兼容保留，加载和保存时固定为 true，不再控制识别路由。
    val useMultimodalAi: Boolean = true,
    val mmModelKey: String = "",
    val mmModelName: String = "",
    val mmModelUrl: String = "",
    val disableThinking: Boolean = false,
    val isLocalSemanticEnabled: Boolean = false,
    val selectedLocalModelId: String = "",

    // WebDAV 连接；密码由 Android Keystore 独立保护，不进入设置备份
    val webDavBaseUrl: String = "",
    val webDavUsername: String = "",
    val webDavSyncEnabled: Boolean = false,
    val webDavWifiOnly: Boolean = false,
    val webDavForegroundSyncIntervalSeconds: Int = 30,
    // 仅开发者使用；为空时固定使用 WillDo/sync/v2
    val webDavRemotePathOverride: String = "",

    // 功能开关
    val showTomorrowEvents: Boolean = false,
    val isDailySummaryEnabled: Boolean = false,
    val dailySummaryMorningMinuteOfDay: Int = DAILY_SUMMARY_DEFAULT_MORNING_MINUTE_OF_DAY, // 今日提醒时间，默认 06:00
    val dailySummaryEveningMinuteOfDay: Int = DAILY_SUMMARY_DEFAULT_EVENING_MINUTE_OF_DAY, // 明日预告时间，默认 22:00
    val isAdvanceReminderEnabled: Boolean = false, // 日程提前提醒总开关
    val advanceReminderMinutes: Int = 30, // 提前分钟数（30/45/60）
    val transitAutoCheckInEnabled: Boolean = false,
    val transitAutoCheckInMinutes: Int = TRANSIT_AUTO_CHECK_IN_DEFAULT_MINUTES,
    val hapticFeedbackEnabled: Boolean = true,

    // 列表排序方向（true=倒序，false=正序）。默认值 = 各列表当前行为，保证零行为变化。
    val homeListReverseOrder: Boolean = false,        // 首页今日/明日，现状正序
    val homeAgendaOnlyScheduled: Boolean = true,
    val homeAgendaReverseOrder: Boolean = false, // 日程视图：最后日期默认在底部
    val allEventsListReverseOrder: Boolean = false,   // 全部日程页，现状正序
    val archivesListReverseOrder: Boolean = true,     // 归档页，现状倒序

    // 首页日历视图记忆：开启后首页沿用上次选中的视图（今日/周/月/日程视图）。
    // 默认开启；关闭后每次进入首页都回到今日视图，与旧行为一致。
    val rememberCalendarViewMode: Boolean = true,
    val homeCalendarViewMode: String = HomeCalendarViewModeName.TODAY,


    // 识别设置
    val tempEventsUseRecognitionTime: Boolean = true, // 旧版默认为 true
    val recognitionMode: Int = RecognitionMode.AI_ONLY,
    val defaultEventDurationMinutes: Int = 60,
    val eventColorPaletteHex: List<String> = DEFAULT_EVENT_COLOR_PALETTE_HEX,
    val screenshotDelayMs: Long = 1000L,
    val isLiveCapsuleEnabled: Boolean = true,
    val liveNotificationTemplateMode: String = LiveNotificationTemplateMode.AUTO,
    val braceletModeEnabled: Boolean = false,

    // 【新增】取件码聚合开关（Beta）
    val isPickupAggregationEnabled: Boolean = false,

    // 短信自动解析取件码
    val isSmsMonitoringEnabled: Boolean = false,
    // 【开发者】日程入库去重；默认保持既有行为
    val scheduleIngestDedupEnabled: Boolean = true,

    // 【实验室】取件类事件时间兜底：取件/取餐/取票/寄件忽略 AI 返回时间，入库时使用当前时间
    val forceInstantCodeTimeToNow: Boolean = false,

    // 【实验室】预测性返回手势
    val predictiveBackEnabled: Boolean = true,

    // 【实验室】剪贴板取件类识别
    val clipboardCodeRecognitionEnabled: Boolean = false,
    val imagePinEnabled: Boolean = false,

    // 【实验室】闪记总开关
    val voiceInputEnabled: Boolean = false,

    // 【实验室】悬浮窗已呼出后，长按音量+是否允许进入闪记录音；默认保留旧行为
    val floatingVoiceLongPressEnabled: Boolean = true,
    // 【实验室】悬浮窗文本闪记保存后同步挂到实况通知；默认关闭，避免意外打扰
    val floatingTextQuickMemoAutoPinEnabled: Boolean = false,
    // 【实验室】语音闪记转写完成后同步挂到实况通知；默认关闭，避免意外打扰
    val voiceQuickMemoAutoPinEnabled: Boolean = false,
    // 【实验室】语音闪记录音达到指定时长后自动保存
    val quickMemoAutoStopEnabled: Boolean = false,
    val quickMemoAutoStopSeconds: Int = QUICK_MEMO_AUTO_STOP_DEFAULT_SECONDS,

    // 随手记：保存时是否调用模型生成 AI 摘要。
    // 默认关闭——随手记多为随手记下的一两句话，自动摘要既消耗额度又没什么价值；
    // 关闭后条目直接落 SKIPPED 状态，列表不会一直停在「摘要生成中」。
    // 【新增】归档配置
    val autoArchiveEnabled: Boolean = false, // 自动归档总开关
    val archiveDaysThreshold: Int = 0, // 归档阈值天数（过期多少天后归档，0=立即归档）

    // 主题设置
    val isDarkMode: Boolean = false,

    // 主题模式：1=跟随系统, 2=浅色, 3=深色
    val themeMode: Int = 1,

    // 主题配色方案：DEFAULT/PURPLE/BLUE/GREEN/PINK/ORANGE/TEAL/NEUTRAL=固定配色
    val themeColorScheme: String = "DEFAULT",
    val customThemeColorHex: String = "#6750A4",

    // 自定义背景：图片保存到 App 私有目录，图片取色结果复用自定义主题色链路
    val appBackgroundEnabled: Boolean = false,
    val appBackgroundImagePath: String = "",
    val appBackgroundImageScale: Float = APP_BACKGROUND_IMAGE_SCALE_DEFAULT,
    val appBackgroundImageOffsetX: Float = 0f,
    val appBackgroundImageOffsetY: Float = 0f,
    val appBackgroundSeedColorHex: String = "",
    val appBackgroundImageColorEnabled: Boolean = false,
    val appBackgroundMiuiBlurTestEnabled: Boolean = true,
    val appBackgroundWallpaperBlurEnabled: Boolean = false,
    val appBackgroundCardAlphaPercent: Int = APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT,
    val appBackgroundScrimAlphaPercent: Int = 0, // 旧设置兼容字段；主界面背景不再叠加蒙层
    val appBackgroundAverageLuminance: Float = -1f,

    // UI 大小设置：1=小, 2=中(默认), 3=大
    val uiSize: Int = 2,
    // 【开发者】三个 UI 大小档位对应的页面密度缩放系数
    val uiScaleSmall: Float = UI_SCALE_SMALL_DEFAULT,
    val uiScaleMedium: Float = UI_SCALE_MEDIUM_DEFAULT,
    val uiScaleLarge: Float = UI_SCALE_LARGE_DEFAULT,
    @Deprecated("UI edition is selected by product flavor; retained only for backup compatibility")
    val uiStyle: String = "MATERIAL3",

    // 桌面小组件：0=跟随软件, 1=浅色, 2=深色
    val widgetThemeMode: Int = WidgetThemeMode.FOLLOW_APP,
    val widgetBackgroundAlpha: Float = 0.9f,

    // 【实验室】网速胶囊开关
    val isNetworkSpeedCapsuleEnabled: Boolean = false,

    // 悬浮窗功能开关
    val isFloatingWindowEnabled: Boolean = false,
    val floatingEventRange: Int = 1, // 悬浮窗日程范围：0=全部, 1=今日, 2=今日+明日
    val floatingExpandSide: String = "RIGHT", // 悬浮窗展开方向：LEFT/RIGHT
    val quickMemoRecordingDisplayMode: Int = QuickMemoRecordingDisplayMode.LIVE_CAPSULE,

    // 天气配置
    val weatherEnabled: Boolean = false,
    val weatherApiUrl: String = "",
    val weatherApiKey: String = "",
    val weatherQWeatherApiUrl: String = "",
    val weatherQWeatherApiKey: String = "",
    val weatherCaiyunApiUrl: String = "",
    val weatherCaiyunToken: String = "",
    val weatherCity: String = "",
    val weatherLocationMode: String = "auto_fallback_manual",
    val weatherManualLocationId: String = "",
    val weatherManualLocationName: String = "",
    val weatherManualAdm1: String = "",
    val weatherManualAdm2: String = "",
    val weatherManualCountry: String = "",
    val weatherManualLat: Double = 0.0,
    val weatherManualLon: Double = 0.0,
    val weatherWarningEnabled: Boolean = true,
    val weatherRiskWarningEnabled: Boolean = true,
    val weatherWarningLookaheadHours: Int = 24,
    val weatherLocationStabilityRequiredHits: Int = 2,
    val weatherProvider: String = "qweather",
    val weatherRefreshInterval: Int = 30,
    // —— 通知策略（Policy Config：ConfigCatalog 登记、业务读取；默认 = 原硬编码常量）——
    val resultNotificationTimeoutMs: Int = 8000,
    val quickMemoSuggestionTimeoutMs: Int = 60000,
    val dailySummaryTimeoutMs: Int = 60000,
    val weatherNotificationTimeoutMs: Int = 180000,
    val ocrProgressTimeoutMs: Int = 120000,
    val modelLoadingTimeoutMs: Int = 600000,
    // —— 天气预测风险阈值（Policy Config；默认 = WeatherRiskAnalyzer 原硬编码值；可按地区/用户覆盖）——
    val weatherHighTempTriggerCelsius: Int = 35,
    val weatherHighTempHighCelsius: Int = 38,
    val weatherLowTempTriggerCelsius: Int = 0,
    val weatherLowTempHighCelsius: Int = -5,
    val weatherStrongCoolingDropCelsius: Int = 6,
    val weatherSevereCoolingDropCelsius: Int = 10,
    val weatherCoolingLookbackHours: Int = 6,
    val weatherWindScaleTrigger: Int = 5,
    val weatherWindScaleMedium: Int = 6,
    val weatherRainTriggerPrecipTenthMm: Int = 1,
    val weatherRainMediumPrecipTenthMm: Int = 10,
    val weatherRainHighPrecipTenthMm: Int = 70,
    val weatherRainTriggerPopPercent: Int = 60,
    val weatherRainMediumPopPercent: Int = 80,

    // 长按音量+动作
    val volumeUpLongPressEnabled: Boolean = false,
    val volumeUpLongPressAction: Int = 1, // 0=无 1=识屏 2=显示悬浮窗 3=悬浮窗录音 4=App内录音 5=App内笔记 6=悬浮窗笔记 7=按住说话

    // 长按音量−动作：与音量+ 共用同一套动作编号，默认关闭。
    val volumeDownLongPressEnabled: Boolean = false,
    val volumeDownLongPressAction: Int = 1,

    // 双击音量+ / 音量−动作：动作编号同上。开启后短按会延迟 300ms
    // 等第二击确认，且系统「按住连续调音量」改由应用代调。
    val volumeUpDoubleTapEnabled: Boolean = false,
    val volumeUpDoubleTapAction: Int = 1,
    val volumeDownDoubleTapEnabled: Boolean = false,
    val volumeDownDoubleTapAction: Int = 1,

    // 闪念胶囊把手：尺寸档位（100/75/50）与主题（DUO/BLUE_PURPLE/PILL3D）。
    val floatingHandleSizePercent: Int = 100,
    val floatingHandleTheme: String = "DUO",
    val floatingPanelMaxCards: Int = 10,
    val floatingFontSizeStep: Int = 0,
    /** 收起态把手自动隐藏秒数；-1 = 不自动隐藏。 */
    val floatingHandleAutoHideSeconds: Int = 10,
    /** 自动隐藏到期后是否缩成贴边竖线（关则彻底隐藏悬浮窗）。 */
    val floatingEdgeLineEnabled: Boolean = true,
    val floatingEdgeLineTapEnabled: Boolean = true,
    val floatingEdgeLineMarginDp: Int = 0,

    // 捐赠状态
    val hasDonated: Boolean = false,

    // 开发者选项
    val developerOptionsUnlocked: Boolean = false,
    val autoRecordLogs: Boolean = true,
    val developerOptionsEnabled: Boolean = false,
    val developerOptionsDisabledAtMillis: Long = 0L,
    val developerSimulateRootEnabled: Boolean = false,
    val developerDemoModeEnabled: Boolean = false,
    val quickMemoPinnedFixedTitleEnabled: Boolean = false,

    // Agent API
    val agentApiEnabled: Boolean = true,
    val agentThirdPartyAccessEnabled: Boolean = false,
    val agentConnectionManagementEnabled: Boolean = false,
    val agentDatabaseOperationsEnabled: Boolean = false
) {
    companion object {
        const val QUICK_MEMO_AUTO_STOP_DEFAULT_SECONDS = 10
        const val QUICK_MEMO_AUTO_STOP_MIN_SECONDS = 1
        const val QUICK_MEMO_AUTO_STOP_MAX_SECONDS = 15
        const val TRANSIT_AUTO_CHECK_IN_DEFAULT_MINUTES = 15
        val TRANSIT_AUTO_CHECK_IN_MINUTE_OPTIONS = listOf(10, 15, 30)

        const val UI_SCALE_MIN = 0.65f
        const val UI_SCALE_MAX = 1.00f
        const val UI_SCALE_SMALL_DEFAULT = 0.75f
        const val UI_SCALE_MEDIUM_DEFAULT = 0.80f
        const val UI_SCALE_LARGE_DEFAULT = 0.85f

        const val APP_BACKGROUND_IMAGE_SCALE_DEFAULT = 1f
        const val APP_BACKGROUND_IMAGE_SCALE_MAX = 4f
        const val APP_BACKGROUND_IMAGE_OFFSET_MIN = -1f
        const val APP_BACKGROUND_IMAGE_OFFSET_MAX = 1f

        fun normalizeAppBackgroundImageScale(scale: Float): Float {
            return scale.coerceIn(APP_BACKGROUND_IMAGE_SCALE_DEFAULT, APP_BACKGROUND_IMAGE_SCALE_MAX)
        }

        fun normalizeAppBackgroundImageOffset(offset: Float): Float {
            return offset.coerceIn(APP_BACKGROUND_IMAGE_OFFSET_MIN, APP_BACKGROUND_IMAGE_OFFSET_MAX)
        }

        fun normalizeQuickMemoAutoStopSeconds(seconds: Int): Int {
            return seconds.coerceIn(QUICK_MEMO_AUTO_STOP_MIN_SECONDS, QUICK_MEMO_AUTO_STOP_MAX_SECONDS)
        }

        fun normalizeTransitAutoCheckInMinutes(minutes: Int): Int {
            return TRANSIT_AUTO_CHECK_IN_MINUTE_OPTIONS.minBy { option ->
                kotlin.math.abs(option - minutes)
            }
        }

        fun normalizeUiScale(value: Float): Float {
            return value.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
        }

        const val SCREENSHOT_DELAY_MIN_MS = 500L
        const val SCREENSHOT_DELAY_MAX_MS = 2500L
        const val DAILY_SUMMARY_MIN_MINUTE_OF_DAY = 0
        const val DAILY_SUMMARY_MAX_MINUTE_OF_DAY = 1439
        const val DAILY_SUMMARY_DEFAULT_MORNING_MINUTE_OF_DAY = 6 * 60
        const val DAILY_SUMMARY_DEFAULT_EVENING_MINUTE_OF_DAY = 22 * 60
        const val APP_BACKGROUND_CARD_ALPHA_MIN_PERCENT = 0
        const val APP_BACKGROUND_CARD_ALPHA_MAX_PERCENT = 100
        const val APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT = 66

        fun normalizeScreenshotDelayMs(delayMs: Long): Long {
            return delayMs.coerceIn(SCREENSHOT_DELAY_MIN_MS, SCREENSHOT_DELAY_MAX_MS)
        }

        fun normalizeDailySummaryMinuteOfDay(minuteOfDay: Int): Int {
            return minuteOfDay.coerceIn(DAILY_SUMMARY_MIN_MINUTE_OF_DAY, DAILY_SUMMARY_MAX_MINUTE_OF_DAY)
        }

        fun normalizeRecognitionMode(mode: Int): Int {
            return RecognitionMode.normalize(mode)
        }

        fun normalizeAppBackgroundCardAlphaPercent(alphaPercent: Int): Int {
            return alphaPercent.coerceIn(
                APP_BACKGROUND_CARD_ALPHA_MIN_PERCENT,
                APP_BACKGROUND_CARD_ALPHA_MAX_PERCENT
            )
        }
    }
}

object QuickMemoRecordingDisplayMode {
    const val LIVE_CAPSULE = 0
    const val FLOATING_WINDOW = 1

    fun normalize(value: Int): Int {
        return if (value == FLOATING_WINDOW) FLOATING_WINDOW else LIVE_CAPSULE
    }
}

object FloatingBallGestureAction {
    const val NONE = 0
    const val OPEN_FLOATING_SCHEDULE = 1
    const val OPEN_QUICK_MEMO = 2
    const val QUICK_RECOGNITION = 3
    const val QUICK_MEMO_RECORDING = 4
    const val OPEN_APP_HOME = 5

    val ALL = listOf(
        NONE,
        OPEN_FLOATING_SCHEDULE,
        OPEN_QUICK_MEMO,
        QUICK_RECOGNITION,
        QUICK_MEMO_RECORDING,
        OPEN_APP_HOME
    )

    fun normalize(value: Int): Int = if (value in ALL) value else NONE

    fun label(value: Int): String {
        return when (normalize(value)) {
            OPEN_FLOATING_SCHEDULE -> "打开日程"
            OPEN_QUICK_MEMO -> "闪记"
            QUICK_RECOGNITION -> "快速识屏"
            QUICK_MEMO_RECORDING -> "闪记录音"
            OPEN_APP_HOME -> "打开首页"
            else -> "无操作"
        }
    }
}

/** 兼容旧版双模型配置；整组迁移，禁止拼接不同服务商的地址和密钥。 */
fun MySettings.migrateToMultimodalConfig(): MySettings {
    val hasMultimodalConfig = mmModelKey.isNotBlank() || mmModelName.isNotBlank() || mmModelUrl.isNotBlank()
    return if (hasMultimodalConfig) {
        if (useMultimodalAi) this else copy(useMultimodalAi = true)
    } else {
        // 保留原模型名称，不能将未知文本模型自动认定或改名为视觉模型。
        copy(useMultimodalAi = true, mmModelKey = modelKey, mmModelName = modelName, mmModelUrl = modelUrl)
    }
}
