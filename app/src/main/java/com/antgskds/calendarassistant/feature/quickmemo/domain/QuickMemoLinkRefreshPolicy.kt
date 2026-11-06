package com.antgskds.calendarassistant.feature.quickmemo.domain

import java.net.URI

/** 刷新短链时只换链接，不动用户改过的正文。 */
object QuickMemoLinkRefreshPolicy {
    fun needsRefresh(savedUrl: String?, incoming: QuickMemoLink): Boolean {
        if (savedUrl == incoming.url) return false
        if (incoming.dedupKey.startsWith("coolapk:feed:") && hasToken(savedUrl) && !hasToken(incoming.url)) return false
        return true
    }

    private fun hasToken(url: String?): Boolean = runCatching {
        URI(url ?: "").rawQuery.orEmpty().split('&').any {
            it.substringBefore('=') == "s" && it.substringAfter('=', "").isNotBlank()
        }
    }.getOrDefault(false)

    fun refreshedBody(body: String, savedUrl: String?, newUrl: String): String {
        if (savedUrl == null) return body
        if (body == savedUrl) return newUrl
        val split = body.lastIndexOf('\n')
        return if (split >= 0 && body.substring(split + 1) == savedUrl) {
            body.substring(0, split + 1) + newUrl
        } else {
            body
        }
    }
}
