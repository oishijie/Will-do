package com.antgskds.calendarassistant.shared.management.catalog

/**
 * 辅助工具台账（代码内，不暴露给 App 用户）。
 *
 * ## 这是什么
 * 项目所有「Helper / Mapper / Support 类纯工具」的总地图——它们不持有业务状态、不发起流程，
 * 只做无副作用的转换/拼装/映射（如通知文案裁剪、图标映射、颜色映射、展示字段拼接）。
 * 打开本文件即可一眼看全：项目里有哪些可复用工具、各干嘛的、归哪条链路。
 *
 * ## 怎么登记（agent 新增 Helper/Mapper/Support 前必须做）
 * 新建一个工具类前，**先在下面 [helpers] 登记一条 [HelperEntry]**，写清它做什么转换、归哪条链路，
 * 再开始编码。先看本台账有没有已存在的同类工具可复用，避免到处重复造 Mapper/Helper。
 *
 * ## 边界
 * - 只登记元信息。Helper 本身必须是无副作用的纯转换：不读写数据库、不发网络、不构建通知、不持久状态。
 * - 有状态/有副作用/串流程的，属于 Pipeline 或 Center，登记到 [PipelineCatalog]，不在这里。
 */
object HelperCatalog {

    /** 工具所属的主链路（对应架构 入口→识别→入库→同步→通知，以及横切支撑）。 */
    enum class Chain {
        RECOGNITION,   // 识别
        INGEST,        // 入库
        SYNC,          // 同步
        NOTIFICATION,  // 通知
        SCHEDULE,      // 日程主体
        WEATHER,       // 天气
        SUPPORT,       // 横切支撑
    }

    /**
     * 一个工具类的登记项。
     * @param name 工具名。
     * @param chain 所属主链路。
     * @param entry 工具类路径（让维护者能定位代码）。
     * @param note 一句话说明它做什么转换。
     */
    data class HelperEntry(
        val name: String,
        val chain: Chain,
        val entry: String,
        val note: String,
    )

    val helpers: List<HelperEntry> = listOf(
        HelperEntry("演示数据生成", Chain.SUPPORT, "feature/settings/developer/application/DemoModeDataFactory", "按当天稳定生成只读日程、随口记和完整天气展示数据"),
        HelperEntry("无障碍诊断快照", Chain.SUPPORT, "platform/accessibility/AccessibilityDiagnosticSnapshot", "有界读取事件源及窗口节点并立即回收，输出不可变 JSON，遮蔽密码字段；汇总服务配置及仅含状态的健康日志"),
        HelperEntry("统一识别结果解析", Chain.RECOGNITION, "feature/recognition/application/ai/RecognitionJsonParser", "分别解析 events 与 bills，坏条目隔离；旧响应兼容，账单缺失字段留待确认"),
        HelperEntry("统一多模态配置", Chain.SUPPORT, "feature/recognition/application/ai/AiModelConfig", "统一图文模型配置；旧设置在 MySettings.migrateToMultimodalConfig 中兼容迁移，不覆盖已有多模态凭据"),
        HelperEntry("重复实例时间计算", Chain.SCHEDULE, "feature/schedule/domain/model/RepeatOccurrenceCalculator", "共用重复规则推进和下一次时间计算，供日程展示及随口记提醒使用"),
        // —— 识别 ——
        HelperEntry("AI 失败映射", Chain.RECOGNITION, "feature/recognition/application/ai/AiFailureMapper", "区分接口明确拒绝图片输入与鉴权、额度、网络等失败，保留可读接口原因"),
        HelperEntry("识别失败文案映射", Chain.RECOGNITION, "feature/recognition/application/ai/RecognitionFailureMessageMapper", "识别失败类型与详情 → 现有结果反馈；不支持图片时引导更换模型，不新增提醒入口"),
        HelperEntry("即时码二维码支持", Chain.RECOGNITION, "core/instantcode/InstantCodeQrSupport", "取件/取餐/取票/寄件图片二维码解析与卡片二维码生成"),
        HelperEntry("正则日程解析", Chain.RECOGNITION, "core/rule/RegexScheduleRecognizer", "可配置正则规则 → 日程草稿"),
        HelperEntry("正则规则偏好存储", Chain.RECOGNITION, "core/rule/RegexScheduleRulePrefs", "开发者可编辑正则规则 JSON 读写"),

        // —— 入库 / 日程 ——
        HelperEntry("首页日期共享选中块", Chain.SCHEDULE, "feature/home/ui/render/material/HomeDateSelectionTransition", "共用日历与日程视图选中背景的坐标过渡，圆角与主题色随目标连续变化"),
        HelperEntry("首页日程分组", Chain.SCHEDULE, "feature/home/domain/HomeAgendaMapper", "仅保留有日程的日期；按日期排序、搜索和选中日定位"),
        HelperEntry("日程展示助手", Chain.SCHEDULE, "feature/schedule/domain/ScheduleDisplayHelper", "日程展示字段拼装"),
        HelperEntry("WebDAV V2 编解码", Chain.SYNC, "feature/cloudsync/data/SyncV2Codec", "状态压缩加密、资产加密、HMAC 内容寻址和稳定哈希"),
        HelperEntry("Agent 协议编解码", Chain.SCHEDULE, "shared/api/AgentProtocolJson", "Agent API v2 请求、响应与业务 DTO 的 JSON 转换"),
        HelperEntry("Agent 日志脱敏", Chain.SUPPORT, "feature/settings/diagnostics/application/DiagnosticLogRedactor", "导出给 Agent 前遮盖 API Key、密码、Token 和鉴权请求头"),

        // —— 通知 ——
        HelperEntry("日程实况展示支持", Chain.NOTIFICATION, "shared/management/resource/notification/display/live/template/ScheduleLiveDisplaySupport", "日程胶囊展示字段裁剪/拼接"),
        HelperEntry("天气实况展示支持", Chain.NOTIFICATION, "shared/management/resource/notification/display/live/template/WeatherLiveDisplaySupport", "天气胶囊展示字段裁剪/拼接"),

        // —— 天气 ——
        HelperEntry("按日期天气摘要", Chain.WEATHER, "feature/weather/domain/WeatherDateSummaryMapper", "今日使用实时天气，未来按日期匹配预报；过去、缺失或无效天气不显示"),
        HelperEntry("天气预警图标映射", Chain.WEATHER, "feature/weather/domain/WeatherAlertIconMapper", "预警类型 → 图标"),
        HelperEntry("天气颜色映射", Chain.WEATHER, "feature/weather/domain/WeatherColorMapper", "天气状态 → 颜色"),
        HelperEntry("天气预报图标映射", Chain.WEATHER, "feature/weather/domain/WeatherForecastIconMapper", "预报代码 → 图标"),
        HelperEntry("天气图标映射", Chain.WEATHER, "feature/weather/domain/WeatherIconMapper", "天气代码 → 图标"),

        // —— 横切支撑 ——
        HelperEntry("行内摘要标题", Chain.SUPPORT, "shared/ui/material/component/InlineSummaryHeader", "日期与天气图标按文字自然换行，仅以空格和间隔点分隔，标题可独立设置字重"),
        HelperEntry("公共设置分隔线", Chain.SUPPORT, "shared/ui/material/settings/AppSettingsDivider", "设置卡片内统一 16 dp 水平缩进、0.5 dp 线宽与主题分隔色；开发者相关页面暂不迁移"),
        HelperEntry("公共列表侧滑容器", Chain.SUPPORT, "shared/ui/material/component/AppSwipeReveal", "日程和随口记共用单向拖动、动作区裁切、取消回弹与阈值触感，保留业务内容和既有展开参数"),
        HelperEntry("公共设置行", Chain.SUPPORT, "shared/ui/material/settings/SettingsRowComponents", "设置开关、点击项及滑块等统一展示入口；实验室四项开关复用 SwitchSettingItem"),
        HelperEntry("平板详情工作区", Chain.SUPPORT, "shared/ui/material/component/AppDetailWorkspace", "日程和随口记共用右侧内容与附件分区；保留手机弹层及既有保存入口"),
        HelperEntry("日历像素布局", Chain.SUPPORT, "feature/schedule/ui/render/material/CalendarLayout", "周月日历的表头、网格与事件复用整数像素边界，消除独立取整引起的错位；保留原半透明配色"),
        HelperEntry("统一页面顶部栏", Chain.SUPPORT, "shared/ui/material/component/AppTopBar", "默认标题居中，平板设置页可左对齐；统一顶部安全区及返回按钮，供页面骨架组合使用"),
        HelperEntry("统一页面骨架", Chain.SUPPORT, "shared/ui/material/component/AppPageScaffold", "统一背景、系统栏安全区、键盘避让和内容限宽；支持整页滚动及列表自行滚动，供设置、首页、天气、随口记、便签和小组件配置使用"),
        HelperEntry("页面内容安全留白", Chain.SUPPORT, "shared/ui/material/component/AppPageInsets", "骨架向自行滚动的内容提供底部留白，供列表 contentPadding 和浮动操作避让使用，业务无需自行读取导航栏高度"),
        HelperEntry("背景模式样式支持", Chain.SUPPORT, "app/ui/theme/material/background/SettingsBackgroundStyleSupport", "背景壁纸模式下的页面颜色映射"),
        HelperEntry("应用公共 UI 组件", Chain.SUPPORT, "shared/ui/material/component/AppUiComponents", "Material 默认路径下的卡片、弹窗和底部弹层外壳"),
        HelperEntry("应用玻璃表面", Chain.SUPPORT, "shared/ui/material/component/AppGlassSurface", "统一页面与弹层磨砂材质、裁切和无采样源时的背景回退"),
        HelperEntry("跨窗口玻璃背景", Chain.SUPPORT, "shared/ui/material/component/AppWindowBackdrop", "使用屏幕坐标对齐弹层与背景，嵌套弹层合成父场景，避免局部采样导致透明缺口"),
        HelperEntry("公共底部弹层", Chain.SUPPORT, "shared/ui/material/component/AppModalBottomSheet", "统一 Sheet 标题、正文滚动、高度上限与 0 至 3 个操作；SheetMaterialSurface 按外壳坐标绘制背景，隔离预测性返回的正文压缩，统一处理底部安全区"),
        HelperEntry("公共锚点菜单", Chain.SUPPORT, "shared/ui/material/component/AppDropdownMenu", "AppMenuItem 定义菜单项；独立 Popup 隔离背景采样，统一定位、选中态、材质及返回和外部点击关闭，供今日视图切换与宽屏更多操作复用"),
        HelperEntry("公共分段切换", Chain.SUPPORT, "shared/ui/material/component/AppSegmentedControl", "主题色选中项与颜色动画、等宽布局、公共背景材质和单次选择触感，统一主题模式、小组件设置及默认启动页切换"),
        HelperEntry("公共滚轮选择器", Chain.SUPPORT, "shared/ui/material/component/AppWheelPicker", "日期、时间及单列选择共用滚轮和弹窗入口，保留 175 dp 高度与 35 dp 行高"),
        HelperEntry("公共悬浮按钮", Chain.SUPPORT, "shared/ui/material/component/AppFloatingActionButton", "统一悬浮操作的表面材质与点击入口；玻璃模式使用 onSurface 图标和文字，普通模式保留调用方配色，保持 72 dp 尺寸及 34 dp 图标"),
        HelperEntry("小组件渲染支持", Chain.SUPPORT, "platform/widget/WidgetRenderingSupport", "桌面小组件 RemoteViews 渲染辅助"),
    )
}
