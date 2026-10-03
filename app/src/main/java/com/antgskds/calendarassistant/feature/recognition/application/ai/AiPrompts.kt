package com.antgskds.calendarassistant.feature.recognition.application.ai

import android.content.Context
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.feature.recognition.application.ai.model.RemotePrompts
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AiPrompts {

    enum class PromptSource {
        LOCAL,
        CLOUD
    }

    private const val TAG = "AiPrompts"
    private const val COPYRIGHT_MARKER = "a1x2i3n4j5u6e7l8u9o0"
    private const val PREFS_NAME = "ai_prompt_cache"
    private const val KEY_PROMPTS_JSON = "cached_prompts_json"
    private const val KEY_IGNORED_VERSION = "ignored_prompt_version"
    private const val KEY_PROMPT_SOURCE = "prompt_source"
    private const val MIN_PROMPT_VERSION = 7
    private const val PROMPT_SOURCE_LOCAL = "local"
    private const val PROMPT_SOURCE_CLOUD = "cloud"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
        prettyPrint = true
    }

    private val defaultPromptHeader = """
        Title：
           - 🚄 火车/高铁："🚄 车次 路线"（示例："🚄 G1008 深圳-武汉"）
           - ✈️ 航班："✈️ 航班号 航线"（示例："✈️ CA1301 北京-上海"）
           - 🚖 打车/网约车：优先 "🚖 颜色·车型 车牌"，其次 "🚖 车型 车牌"，最后 "🚖 平台 车牌"
           - 📦 取件："📦 菜鸟 1234"，"📦 圆通快递 1-1-8478"，"📦 取件 1234"
           - 🍔 取餐："🍔 取餐 A05"，"🍔 麦当劳 A114"
           - 🎫 取票："🎫 取票 1234 56"
           - 🚚 寄件："🚚 寄件 1234"
        description：
           - 🚄 火车：【列车】车次|检票口|座位号
           - ✈️ 航班：【航班】航班号|登机口|座位号
           - 🚖 打车：【用车】颜色|车型|车牌
           - 📦 取件：【取件】取件码|品牌|位置
           - 🍔 取餐：【取餐】取餐码|品牌|位置
           - 🎫 取票：【取票】取票码|品牌|位置
           - 🚚 寄件：【寄件】寄件码|品牌|地点

           所有识别出的事件都必须按默认持续时间计算 endTime，不要自行猜测其他时长。
           endTime = {{defaultDuration}}
    """.trimIndent()

    private val defaultRulePatch = """
        日程规则补丁（仅作用于 events 输出）：
        1. description 必须以 【中文名】 开头。
        2. 规则格式：
           - train: 【列车】车次|检票口|座位号
           - taxi: 【用车】颜色|车型|车牌
           - pickup: 【取件】取件码|品牌|位置
           - food: 【取餐】取餐码|品牌|位置
           - ticket: 【取票】取票码|品牌|位置
           - sender: 【寄件】寄件码|品牌|地点
           - flight: 【航班】航班号|登机口|座位号
           - general: 【日程】备注（或普通描述）
        3. tag 字段仍按 ruleId 填写（如 general/train/taxi/pickup/food/ticket/sender/flight 等）。
    """.trimIndent()

    private val defaultMmUnifiedPrompt = """
        提取输入中的日程事件与取件/取餐信息。文本请结合上下文并跨行理解语意，图片请留意边缘细小时间戳、APP界面或条形码凭证。

        任务：
        1. 提取交通或普通日程。
        2. 提取取件码、外卖、快递等信息。
        取件类事件请强制使用当前系统时间。
        【当前系统时间】：{{timeStr}}
        【输出格式】
        仅输出纯 JSON 对象：
        {
          "events": [
            {
              "title": "规范标题",
              "startTime": "yyyy-MM-dd HH:mm",
              "endTime": "yyyy-MM-dd HH:mm",
              "location": "地址",
                "description": "备注；取件类请用格式：【取件】取件码|品牌|位置",
              "type": "event",
              "tag": "general | train | taxi | flight | pickup | food | ticket | sender"
            }
          ]
        }
    """.trimIndent()

    private val defaultPrompts = RemotePrompts(
        version = 11,
        promptHeader = defaultPromptHeader,
        userTextPrompt = defaultMmUnifiedPrompt,
        mmUnifiedPrompt = defaultMmUnifiedPrompt,
    )

    fun appendCopyrightMarker(input: String): String {
        return input + COPYRIGHT_MARKER
    }

    fun exportToJson(context: Context): String {
        val prompts = activePrompts(context)
        return try {
            buildString {
                appendLine("# Will do 提示词导出")
                appendLine("# version: ${prompts.version}")
                appendLine()

                appendLine("=== promptHeader ===")
                appendLine(prompts.promptHeader.replace("\\n", "\n"))
                appendLine("=== end ===")
                appendLine()

                appendLine("=== mmUnifiedPrompt ===")
                appendLine(prompts.mmUnifiedPrompt.replace("\\n", "\n"))
                appendLine("=== end ===")
            }
        } catch (e: Exception) {
            Log.e(TAG, "导出提示词失败: ${e.message}")
            ""
        }
    }

    fun importFromJson(context: Context, content: String): Boolean {
        return try {
            if (isCustomFormat(content)) {
                val prompts = parseCustomFormat(content)
                updatePrompts(context.applicationContext, prompts, PromptSource.LOCAL)
                Log.d(TAG, "导入提示词成功，version=${prompts.version}")
                true
            } else {
                val prompts = json.decodeFromString<RemotePrompts>(content)
                updatePrompts(context.applicationContext, prompts, PromptSource.LOCAL)
                Log.d(TAG, "导入提示词成功，version=${prompts.version}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "导入提示词失败: ${e.message}")
            false
        }
    }

    private fun isCustomFormat(content: String): Boolean {
        val markers = listOf(
            "=== promptHeader ===",
            "=== schedulePrompt ===",
            "=== pickupPrompt ===",
            "=== mmUnifiedPrompt ===",
            "=== userTextPrompt ==="
        )
        return markers.any(content::contains)
    }

    private fun parseCustomFormat(content: String): RemotePrompts {
        val lines = content.lines()
        var version = 1
        val fields = mutableMapOf<String, StringBuilder>()
        var currentField = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                line.startsWith("# version:") -> {
                    version = line.substringAfter("# version:").trim().toIntOrNull() ?: 1
                }
                trimmed == "=== end ===" -> {
                    currentField = ""
                }
                trimmed.startsWith("===") && trimmed.endsWith("===") -> {
                    currentField = trimmed.removeSurrounding("===").trim()
                    fields[currentField] = StringBuilder()
                }
                currentField.isNotEmpty() && !line.startsWith("#") -> {
                    fields[currentField]?.appendLine(line)
                }
            }
        }

        return RemotePrompts(
            version = version,
            promptHeader = fields["promptHeader"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: "",
            userTextPrompt = fields["userTextPrompt"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: "",
            schedulePrompt = fields["schedulePrompt"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: "",
            pickupPrompt = fields["pickupPrompt"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: "",
            mmUnifiedPrompt = fields["mmUnifiedPrompt"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: ""
        )
    }

    fun getLocalVersion(context: Context): Int {
        return loadCachedPrompts(context)?.version ?: defaultPrompts.version
    }

    fun getIgnoredVersion(context: Context): Int {
        return prefs(context).getInt(KEY_IGNORED_VERSION, 0)
    }

    fun markVersionIgnored(context: Context, version: Int) {
        val appContext = context.applicationContext
        if (version <= getIgnoredVersion(appContext)) return
        prefs(appContext).edit().putInt(KEY_IGNORED_VERSION, version).apply()
        Log.d(TAG, "已忽略云端 prompt 版本: $version")
    }

    fun clearIgnoredVersion(context: Context) {
        prefs(context).edit().remove(KEY_IGNORED_VERSION).apply()
    }

    fun updatePrompts(context: Context, prompts: RemotePrompts, source: PromptSource? = null) {
        val appContext = context.applicationContext
        if (!prompts.isValid()) return
        val normalizedPrompts = normalize(prompts)
        val encoded = json.encodeToString(normalizedPrompts)
        prefs(appContext)
            .edit()
            .putString(KEY_PROMPTS_JSON, encoded)
            .apply()
        if (source != null) {
            setPromptSource(appContext, source)
        }
        clearIgnoredVersion(appContext)
        Log.d(TAG, "已写入本地 prompt，version=${normalizedPrompts.version}")
    }

    fun getPromptSource(context: Context): PromptSource {
        val raw = prefs(context).getString(KEY_PROMPT_SOURCE, PROMPT_SOURCE_LOCAL)
        return when (raw) {
            PROMPT_SOURCE_CLOUD -> PromptSource.CLOUD
            else -> PromptSource.LOCAL
        }
    }

    fun getUserTextPrompt(
        context: Context,
        timeStr: String,
        dateToday: String,
        dayOfWeek: String,
        rulePatch: String? = null,
        defaultDurationMinutes: Int = 60
    ): String {
        // 图文共用提示词时，文字调用也必须填全时间占位符。
        val date = LocalDate.parse(dateToday)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val now = LocalDateTime.parse(timeStr.take(16), formatter)
        return getMultimodalUnifiedPrompt(
            context = context,
            timeStr = timeStr,
            dateToday = dateToday,
            dateYesterday = date.minusDays(1).toString(),
            dateBeforeYesterday = date.minusDays(2).toString(),
            nowTime = now.format(formatter),
            nowPlusHourTime = now.plusHours(1).format(formatter),
            dayOfWeek = dayOfWeek,
            rulePatch = rulePatch,
            defaultDurationMinutes = defaultDurationMinutes
        )
    }

    fun getMultimodalUnifiedPrompt(
        context: Context,
        timeStr: String,
        dateToday: String,
        dateYesterday: String,
        dateBeforeYesterday: String,
        nowTime: String,
        nowPlusHourTime: String,
        dayOfWeek: String,
        rulePatch: String? = null,
        defaultDurationMinutes: Int = 60
    ): String {
        val prompts = activePrompts(context)
        return appendRulePatch(
            render(
            template = withPromptHeader(prompts.promptHeader, prompts.mmUnifiedPrompt),
            values = mapOf(
                "timeStr" to timeStr,
                "dateToday" to dateToday,
                "dateYesterday" to dateYesterday,
                "dateBeforeYesterday" to dateBeforeYesterday,
                "nowTime" to nowTime,
                "nowPlusHourTime" to nowPlusHourTime,
                "dayOfWeek" to dayOfWeek,
                "defaultDuration" to formatDuration(defaultDurationMinutes)
            )
        ),
            rulePatch
        )
    }

    private fun appendRulePatch(prompt: String, extraPatch: String? = null): String {
        val extra = extraPatch?.trim().orEmpty()
        return if (extra.isBlank()) {
            prompt + "\n\n" + defaultRulePatch
        } else {
            prompt + "\n\n" + defaultRulePatch + "\n\n以下自定义规则仅作用于 events：\n" + extra
        }
    }

    private fun activePrompts(context: Context): RemotePrompts {
        return loadCachedPrompts(context) ?: defaultPrompts
    }

    private fun loadCachedPrompts(context: Context): RemotePrompts? {
        val rawJson = prefs(context).getString(KEY_PROMPTS_JSON, null) ?: return null
        return try {
            val cached = json.decodeFromString<RemotePrompts>(rawJson)
            if (!cached.isValid()) return null
            if (cached.version < MIN_PROMPT_VERSION) {
                return resetPrompts(context, "localVersion=${cached.version}")
            }
            normalize(cached)
        } catch (_: Exception) {
            null
        }
    }

    // 仅清理旧 OCR 预处理协议，保留用户的标题、描述格式和其他自定义规则。
    private val legacyLayoutHeaderPattern = Regex(
        listOf(
            "【布局标记】（已通过算法预处理）",
            "- ` | `: 同行分列",
            "- `[L]`: 左侧气泡",
            "- `[R]`: 右侧气泡",
            "- `[C]`: 居中",
            "保留原始换行。"
        ).joinToString("\\h*\\r?\\n\\h*", prefix = "(?m)^\\h*", postfix = "\\h*(?:\\r?\\n|$)") {
            Regex.escape(it)
        }
    )

    internal fun normalize(prompts: RemotePrompts): RemotePrompts {
        val header = prompts.promptHeader
            .replace("\\n", "\n")
            .replace(legacyLayoutHeaderPattern, "")
            .trim()
            .ifBlank { defaultPrompts.promptHeader }
        val unified = when {
            prompts.mmUnifiedPrompt.isNotBlank() -> prompts.mmUnifiedPrompt
            prompts.userTextPrompt.isNotBlank() -> prompts.userTextPrompt
            else -> defaultPrompts.mmUnifiedPrompt
        }
        // 缓存/云端/导入的旧统一提示词保留自定义正文，只补齐新增的返回协议。
        // 记账功能已下线：旧版统一图文输出协议（含交易时间规则）整块移除，保留其余自定义正文。
        val upgraded = unified.replace(Regex("(?s)【统一图文输出协议 v(?:9|10|11|12|13|14|15)】.*?此协议优先于旧提示词中“仅输出 events”或“仅识别日程”的限制，其他自定义日程规则继续生效。"), "").trimEnd()
        val mmUnifiedPrompt = upgraded
        return prompts.copy(
            promptHeader = header,
            userTextPrompt = mmUnifiedPrompt,
            mmUnifiedPrompt = mmUnifiedPrompt,
            // 旧分路字段只用于读取历史文件，不再维护、导出或执行。
            schedulePrompt = "",
            pickupPrompt = ""
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun setPromptSource(context: Context, source: PromptSource) {
        val value = when (source) {
            PromptSource.LOCAL -> PROMPT_SOURCE_LOCAL
            PromptSource.CLOUD -> PROMPT_SOURCE_CLOUD
        }
        prefs(context).edit().putString(KEY_PROMPT_SOURCE, value).apply()
    }

    private fun resetPrompts(context: Context, reason: String): RemotePrompts {
        val appContext = context.applicationContext
        val encoded = json.encodeToString(defaultPrompts)
        prefs(appContext)
            .edit()
            .putString(KEY_PROMPTS_JSON, encoded)
            .remove(KEY_IGNORED_VERSION)
            .apply()
        setPromptSource(appContext, PromptSource.LOCAL)
        Log.d(TAG, "已重置提示词为默认版本: $reason")
        return defaultPrompts
    }

    private fun render(template: String, values: Map<String, String>): String {
        var rendered = template
        values.forEach { (key, value) ->
            rendered = rendered.replace("{{${key}}}", value)
        }
        return rendered
    }

    private fun formatDuration(minutes: Int): String {
        return when (minutes) {
            END_OF_DAY_DURATION -> "endTime = 23:59 on the same day as startTime"
            60 -> "endTime = startTime + 1h"
            120 -> "endTime = startTime + 2h"
            180 -> "endTime = startTime + 3h"
            360 -> "endTime = startTime + 6h"
            1440 -> "endTime = startTime + 24h"
            else -> "endTime = startTime + ${minutes.coerceAtLeast(1)}min"
        }
    }

    private fun withPromptHeader(header: String, body: String): String {
        val headerTrimmed = header.trim()
        if (headerTrimmed.isBlank()) return body
        val bodyTrimmed = body.trim()
        return if (bodyTrimmed.isBlank()) headerTrimmed else "$headerTrimmed\n\n$bodyTrimmed"
    }
}
