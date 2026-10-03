package com.antgskds.calendarassistant.shared.query

enum class CapsuleRouteMode {
    /** 小米 HyperOS 灵动岛，依赖 LSPosed/Xposed 注入，已下线且不会再被路由命中。 */
    MIUI_ISLAND,
    LIVE_CAPSULE,
    STANDARD_NOTIFICATION
}

interface CapsuleRoutingQueryApi {
    fun resolveMode(liveCapsuleEnabled: Boolean): CapsuleRouteMode
}
