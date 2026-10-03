package com.antgskds.calendarassistant.feature.capsule.data

import com.antgskds.calendarassistant.shared.query.CapsuleRouteMode
import com.antgskds.calendarassistant.shared.query.CapsuleRoutingQueryApi

/**
 * 本地路由判定。
 *
 * 小米 HyperOS 灵动岛依赖 LSPosed/Xposed 注入，已整体下线：原
 * `OsUtils.isHyperOS() && XposedModuleStatus.isActive()` 分支移除，实况通知只剩
 * 原生胶囊与标准通知两条通道。
 */
class LocalCapsuleRoutingQueryApi : CapsuleRoutingQueryApi {
    override fun resolveMode(liveCapsuleEnabled: Boolean): CapsuleRouteMode {
        if (!liveCapsuleEnabled) return CapsuleRouteMode.STANDARD_NOTIFICATION
        return CapsuleRouteMode.LIVE_CAPSULE
    }
}
