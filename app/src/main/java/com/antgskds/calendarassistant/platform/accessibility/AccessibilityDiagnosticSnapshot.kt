package com.antgskds.calendarassistant.platform.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import org.json.JSONArray
import org.json.JSONObject

/** 在事件回调线程读取；返回 JSON 后不再持有系统节点。 */
object AccessibilityDiagnosticSnapshot {
    const val HEALTH_LOG_TAG = "WillDoAccessHealth"

    /** 固定窗口能力与 XML 一致，更新后旧系统配置也能补齐；按键过滤独立管理。 */
    internal fun connectionBaseFlags(previous: Int?, current: Int): Int =
        ((previous ?: current) and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()) or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS

    fun serviceConfiguration(info: AccessibilityServiceInfo?): JSONObject {
        if (info == null) return JSONObject().put("available", false)
        val packages = info.packageNames
        return JSONObject().put("available", true)
            .put("flags", info.flags).put("eventTypes", info.eventTypes)
            .put("feedbackType", info.feedbackType).put("notificationTimeoutMs", info.notificationTimeout)
            .put("capabilities", info.capabilities)
            .put("packageNames", packages?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
    }

    private val healthRecordPrefix = Regex(
        "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} (DEBUG|INFO|WARN|ERROR)/$HEALTH_LOG_TAG: "
    )

    /** 诊断包只附带专用状态日志，不混入其他业务日志及异常堆栈。 */
    internal fun healthHistory(log: String): String = log.lineSequence()
        .filter { healthRecordPrefix.containsMatchIn(it) }
        .joinToString("\n")
}
