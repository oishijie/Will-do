package com.antgskds.calendarassistant.platform.floating.capsule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 从闪记文本里抽取一个可能的时间点，用于卡片「闹钟」操作。
 *
 * 只覆盖最常用的中文表达，抽不到就返回 null 让调用方降级：
 * - 「明天/后天/今天」+ 可选「上午/下午/晚上/中午」+ 「H点(半|M分)」
 * - 「H点半」「下午3点」「15:30」
 * 不追求覆盖全部自然语言，宁可返回 null 也不猜错时间。
 */
object CapsuleTimeParser {

    private val dayWords = mapOf(
        "今天" to 0,
        "明天" to 1,
        "后天" to 2,
    )

    private val meridiemWords = listOf("凌晨", "早上", "上午", "中午", "下午", "傍晚", "晚上")

    private val clockPattern = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""")
    private val hourPattern = Regex("""(\d{1,2})\s*[点时]\s*(半|\d{1,2}\s*分?)?""")

    fun parse(text: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val clockMatch = clockPattern.find(text)
        if (clockMatch != null) {
            val hour = clockMatch.groupValues[1].toIntOrNull() ?: return null
            val minute = clockMatch.groupValues[2].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            val date = resolveDate(text, now)
            return date.atTime(LocalTime.of(hour, minute)).let { adjustIfPast(it, now, text) }
        }

        val hourMatch = hourPattern.find(text) ?: return null
        var hour = hourMatch.groupValues[1].toIntOrNull() ?: return null
        val minute = when (val tail = hourMatch.groupValues[2].trim()) {
            "半" -> 30
            "" -> 0
            else -> tail.removeSuffix("分").trim().toIntOrNull() ?: 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null

        // 「下午3点」→ 15 点；「晚上12点」→ 0 点
        val meridiem = meridiemWords.firstOrNull { text.contains(it) }
        hour = when (meridiem) {
            "下午", "傍晚", "晚上" -> if (hour in 1..11) hour + 12 else hour
            "中午" -> if (hour in 1..10) hour + 12 else hour
            else -> hour
        }
        if (hour !in 0..23) return null

        val date = resolveDate(text, now)
        return date.atTime(LocalTime.of(hour, minute)).let { adjustIfPast(it, now, text) }
    }

    private fun resolveDate(text: String, now: LocalDateTime): LocalDate {
        val offset = dayWords.entries.firstOrNull { text.contains(it.key) }?.value ?: 0
        return now.toLocalDate().plusDays(offset.toLong())
    }

    /** 没说日期又说的是已经过去的时间，视为明天。 */
    private fun adjustIfPast(candidate: LocalDateTime, now: LocalDateTime, text: String): LocalDateTime {
        val hasDayWord = dayWords.keys.any { text.contains(it) }
        if (hasDayWord) return candidate
        return if (candidate.isBefore(now)) candidate.plusDays(1) else candidate
    }
}
