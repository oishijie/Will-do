package com.antgskds.calendarassistant.shared.management.catalog

import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination

/**
 * 页面登记清单（代码内台账，不暴露给 App 用户）。
 *
 * ## 这是什么
 * 项目所有「设置页面」的唯一总台账。打开本文件即可一眼看全：有哪些页面、各是干嘛的、
 * 给谁看、对应路由。让不直接读代码的维护者也能管住「项目里到底有哪些页面、谁加的」。
 *
 * ## 怎么登记（agent 加新页面必须做）
 * 加一个新设置页时，**必须先在下面 [pages] 里登记一条 [PageEntry]**（带说明注释），
 * 然后才去设置页 connector 接渲染。架构守卫 PAGE_NOT_REGISTERED 会强制这条规则：
 * SettingsDestination 枚举里出现、但这里没登记的页面，会让 `checkArchitectureGuardrails` 失败。
 *
 * ## 边界
 * - 本清单只登记「元信息」（名字/路由/标题/可见级别/说明），不持有页面渲染逻辑。
 * - 各页业务装配仍在 main connector；导航壳从本台账读取 route/title，避免维护重复映射。
 * - 不要把本清单做成给 App 用户看的 UI——它是代码内的管理台账。
 */
object PageCatalog {

    /** 页面可见级别：决定这页给谁、从哪进。 */
    enum class PageVisibility {
        /** 普通用户可见（侧边栏/偏好页入口）。 */
        USER,
        /** 开发者/实验室专属（需开启开发者选项）。 */
        DEVELOPER,
        /** 操作类，不是真正的导航页（如退出、主题切换）。 */
        ACTION,
    }

    /**
     * 一个页面的登记项。
     * @param destination 页面的导航枚举值（SettingsDestination）。
     * @param route 内部路由名；ACTION 类可为 null。
     * @param title 页面标题，也是导航壳的唯一标题来源。
     * @param visibility 可见级别。
     * @param note 一句话说明这页是干嘛的（给维护者看）。
     */
    data class PageEntry(
        val destination: SettingsDestination,
        val route: String?,
        val title: String,
        val visibility: PageVisibility,
        val note: String,
    )

    /** 不单独占用 SettingsDestination 的子页面。 */
    data class NestedPageEntry(
        val route: String,
        val parentDestination: SettingsDestination,
        val title: String,
    )

    val pages: List<PageEntry> = listOf(
        // —— 其他设置（普通用户）——
        PageEntry(SettingsDestination.AI, "settings_ai", "模型与连接", PageVisibility.USER, "配置 AI 识别模型、识别与推理策略及 Agent 访问"),
        PageEntry(SettingsDestination.Weather, "settings_weather", "天气", PageVisibility.USER, "天气位置与预警设置"),
        PageEntry(SettingsDestination.Preference, "settings_preference", "通用偏好", PageVisibility.USER, "通用偏好：显示、操作与截图等应用级开关"),
        PageEntry(SettingsDestination.FeatureHub, "settings_feature_hub", "功能设置", PageVisibility.USER, "聚合入口：模型与连接、天气、随口记、悬浮与侧边栏、日程与通知"),
        PageEntry(SettingsDestination.QuickMemo, "settings_quick_memo", "随口记", PageVisibility.USER, "随口记总开关、录音展示、自动结束与语音转写模型"),
        PageEntry(SettingsDestination.FloatingEntry, "settings_floating_entry", "悬浮与侧边栏", PageVisibility.USER, "悬浮日程、侧边栏与悬浮球入口及其手势动作"),
        PageEntry(SettingsDestination.ScheduleNotification, "settings_schedule_notification", "日程与通知", PageVisibility.USER, "日历同步、默认时长、提前提醒与各类通知开关"),
        PageEntry(SettingsDestination.ScheduleColors, "settings_schedule_colors", "日程颜色", PageVisibility.USER, "自定义新建和识别日程使用的颜色色盘（从通用偏好进入）"),
        PageEntry(SettingsDestination.Archives, "settings_archives", "归档", PageVisibility.USER, "查看与恢复已归档的过期日程"),
        PageEntry(SettingsDestination.Backup, "settings_backup", "数据备份", PageVisibility.USER, "导入导出备份"),
        PageEntry(SettingsDestination.AppUpdate, "settings_app_update", "软件更新", PageVisibility.USER, "检查与下载新版本"),
        PageEntry(SettingsDestination.WidgetSettings, "settings_widget_settings", "桌面小组件", PageVisibility.USER, "桌面小组件外观设置（从通用偏好进入）"),

        // —— 操作类（不是导航页，直接执行）——
        PageEntry(SettingsDestination.Theme, "settings_theme", "主题设置", PageVisibility.ACTION, "切换深浅色/主题色"),
        PageEntry(SettingsDestination.About, "settings_about", "关于应用", PageVisibility.ACTION, "版本与关于信息"),
        PageEntry(SettingsDestination.Donate, "settings_donate", "捐赠开发者", PageVisibility.ACTION, "捐赠入口"),
        PageEntry(SettingsDestination.Logout, null, "退出应用", PageVisibility.ACTION, "退出登录/应用（不导航）"),

        // —— 实验室 / 开发者 ——
        PageEntry(SettingsDestination.Laboratory, "settings_laboratory", "实验室", PageVisibility.USER, "实验性功能开关与开发者选项入口"),
        PageEntry(SettingsDestination.Developer, "settings_developer", "开发者", PageVisibility.DEVELOPER, "测试中心：列表排序开关 + DebugActionRegistry 调试动作（从实验室进入）"),
        PageEntry(SettingsDestination.ConfigEditor, "settings_config_editor", "配置编辑", PageVisibility.DEVELOPER, "由 ConfigCatalog 驱动的底层配置编辑（从开发者页进入）"),
        PageEntry(SettingsDestination.OnboardingGuide, "settings_onboarding_guide", "初始化引导", PageVisibility.DEVELOPER, "首次启动引导页调试入口：权限与一级功能开关体检"),
        PageEntry(SettingsDestination.RegexRuleEditor, "settings_regex_rule_editor", "正则规则", PageVisibility.DEVELOPER, "编辑日程正则规则；测试仅解析、不入库"),
    )

    val weatherDetailPage = NestedPageEntry(
        route = "settings_weather_detail",
        parentDestination = SettingsDestination.Weather,
        title = "天气详情",
    )

    /** 按枚举值查登记项。 */
    fun find(destination: SettingsDestination): PageEntry? =
        pages.firstOrNull { it.destination == destination }

    fun resolveDestination(value: String): SettingsDestination =
        SettingsDestination.entries.firstOrNull { it.name == value }
            ?: SettingsDestination.Preference

    fun routeFor(destination: SettingsDestination): String? = find(destination)?.route

    fun titleFor(destination: SettingsDestination): String =
        requireNotNull(find(destination)) { "Settings destination is not registered: $destination" }.title

    val navigablePages: List<PageEntry>
        get() = pages.filter { it.route != null }

    /** 按可见级别筛选。 */
    fun byVisibility(visibility: PageVisibility): List<PageEntry> =
        pages.filter { it.visibility == visibility }
}
