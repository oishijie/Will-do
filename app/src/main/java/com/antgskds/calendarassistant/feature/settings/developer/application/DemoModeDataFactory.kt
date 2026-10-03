package com.antgskds.calendarassistant.feature.settings.developer.application

import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTodoState
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.FLAG_ALL_DAY
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.STATE_COMPLETED
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.STATE_PENDING
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherAlertData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherDailyForecast
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherHourlyForecast
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherRiskAlert
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/** 只生成内存展示数据；调用方不得把结果交给入库、同步或通知链路。 */
object DemoModeDataFactory {
    // 仅供界面波形演示的标识，不是文件路径，不交给真实播放器。
    const val QUICK_MEMO_AUDIO_PATH = "demo://quick-memo/voice"
    private val colors = listOf(
        0xFF536BA5.toInt(), 0xFF6A78B8.toInt(), 0xFF557F75.toInt(),
        0xFF9A6C5A.toInt(), 0xFF7B659D.toInt(), 0xFF4F7896.toInt(),
    )

    fun events(today: LocalDate = LocalDate.now()): List<Event> {
        val zone = ZoneId.systemDefault()
        val random = Random(today.toEpochDay())
        val result = mutableListOf<Event>()
        result += event(-1, "团队周会", today, 9, 0, 10, 0, "创新楼 302", EventTags.GENERAL, colors[0], zone)
        result += event(-2, "产品方案评审", today, 9, 30, 11, 0, "线上会议", EventTags.GENERAL, colors[1], zone)
        result += event(-3, "图书馆还书", today, 14, 0, 14, 30, "校图书馆", EventTags.GENERAL, colors[2], zone)
        result += event(-4, "领取快递 A-5271", today, 17, 20, 18, 0, "东门驿站", EventTags.PICKUP, colors[3], zone)
        result += allDayEvent(-5, "校园开放日", today, colors[4], zone)

        val titles = listOf(
            "完成课程作业", "健身训练", "项目进度同步", "社团活动", "预约体检",
            "朋友聚餐", "阅读计划", "毕业设计讨论", "购买生活用品", "观看电影",
            "整理旅行清单", "实验室值班", "英语口语练习", "志愿服务", "部门分享会",
        )
        val locations = listOf("教学楼 A201", "体育馆", "线上会议", "大学生活动中心", "校医院", "城市广场")
        repeat(23) { index ->
            var offset = random.nextInt(-12, 36)
            if (offset == 0) offset = index % 9 + 1
            val date = today.plusDays(offset.toLong())
            val startHour = listOf(8, 10, 13, 15, 18, 20)[random.nextInt(6)]
            val duration = listOf(30, 45, 60, 90, 120)[random.nextInt(5)]
            val start = date.atTime(startHour, listOf(0, 15, 30)[random.nextInt(3)])
            val end = if (index == 7) start.plusDays(2).withHour(12) else start.plusMinutes(duration.toLong())
            result += event(
                id = -(index + 6L),
                title = titles[index % titles.size],
                start = start,
                end = end,
                location = locations[index % locations.size],
                tag = if (index == 7) EventTags.TRAIN else EventTags.GENERAL,
                color = colors[index % colors.size],
                zone = zone,
                state = if (end.toLocalDate().isBefore(today)) STATE_COMPLETED else STATE_PENDING,
            )
        }
        return result
    }

    fun quickMemos(today: LocalDate = LocalDate.now()): List<QuickMemoEntity> {
        val base = today.atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val bodies = listOf(
            "周五之前整理完平板布局的宣传截图",
            "下次开会讨论月视图中跨天日程的展示方式",
            "记得买咖啡豆、牛奶和打印纸",
            "课程设计想法：增加按周复习计划和完成情况统计",
            "旅行清单：充电器、相机、雨伞、身份证",
            "今天的灵感：把零散信息统一放进时间线",
        )
        return bodies.mapIndexed { index, body ->
            QuickMemoEntity(
                id = -(index + 1L),
                bodyText = if (index == 0) "$body（演示语音，无实际录音）" else body,
                type = if (index == 0) com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType.VOICE else com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType.TEXT,
                audioPath = if (index == 0) QUICK_MEMO_AUDIO_PATH else null,
                audioDurationMs = if (index == 0) 18_000L else 0L,
                transcriptionStatus = if (index == 0) com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTranscriptionStatus.SUCCESS else com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTranscriptionStatus.NONE,
                createdAt = base - index * 3_600_000L,
                updatedAt = base - index * 3_600_000L,
                sortRank = (bodies.size - index).toLong(),
                todoState = if (index in 0..2) QuickMemoTodoState.ACTIVE else QuickMemoTodoState.NONE,
            )
        }
    }

    fun weather(today: LocalDate = LocalDate.now()): WeatherData {
        val zone = ZoneId.systemDefault()
        val now = today.atTime(8, 0).atZone(zone)
        val hourly = List(24) { index ->
            val time = now.plusHours(index.toLong())
            val temp = 24 + ((6 - kotlin.math.abs(12 - (time.hour))).coerceIn(-2, 5))
            WeatherHourlyForecast(
                fxTime = time.toOffsetDateTime().toString(),
                temp = temp.toString(),
                icon = if (index in 7..10) "305" else if (index < 6) "150" else "101",
                text = if (index in 7..10) "小雨" else "多云",
                windDir = "东南风",
                windScale = "2-3",
                windSpeed = "12",
                humidity = (58 + index % 12).toString(),
                pop = if (index in 7..10) "65" else "10",
                precip = if (index in 7..10) "0.8" else "0.0",
                pressure = "1008",
                cloud = "55",
            )
        }
        val daily = List(7) { index ->
            val date = today.plusDays(index.toLong())
            WeatherDailyForecast(
                fxDate = date.toString(),
                tempMax = (29 - index % 3).toString(),
                tempMin = (20 + index % 2).toString(),
                iconDay = listOf("101", "305", "100", "101", "306", "101", "100")[index],
                textDay = listOf("多云", "小雨", "晴", "多云", "中雨", "多云", "晴")[index],
                iconNight = "150",
                textNight = "多云",
                windDirDay = "东南风",
                windScaleDay = "2-3",
                windDirNight = "东风",
                windScaleNight = "1-2",
                humidity = (62 + index).toString(),
                precip = if (index == 1 || index == 4) "3.2" else "0.0",
                uvIndex = if (index == 2 || index == 6) "7" else "3",
                sunrise = "06:02",
                sunset = "18:12",
            )
        }
        return WeatherData(
            temperature = "26",
            feelsLike = "27",
            text = "多云",
            icon = "101",
            windDir = "东南风",
            windScale = "2",
            windSpeed = "12",
            humidity = "62",
            precip = "0.0",
            pressure = "1008",
            vis = "18",
            obsTime = now.toOffsetDateTime().toString(),
            city = "演示城市",
            locationName = "大学城",
            adm1 = "演示省",
            adm2 = "演示城市",
            locationSource = "demo",
            provider = "demo",
            updateTime = now.toInstant().toEpochMilli(),
            hourlyForecast = hourly,
            dailyForecast = daily,
            alerts = listOf(
                WeatherAlertData(
                    id = "demo-weather-alert",
                    senderName = "演示气象台",
                    eventName = "雷电黄色预警",
                    severity = "Moderate",
                    colorCode = "Yellow",
                    issuedTime = now.toOffsetDateTime().toString(),
                    headline = "午后可能出现短时雷阵雨",
                    description = "预计午后局部地区有雷阵雨，并伴有短时大风。",
                    instruction = "外出请携带雨具，注意临时大风。",
                )
            ),
            riskAlerts = listOf(
                WeatherRiskAlert(
                    id = "demo-rain-risk",
                    title = "通勤降雨提醒",
                    level = "MEDIUM",
                    fxTime = now.plusHours(8).toOffsetDateTime().toString(),
                    weatherText = "小雨",
                    message = "晚高峰可能有短时降雨，建议提前准备雨具。",
                )
            ),
            attributions = listOf("演示数据"),
        )
    }

    private fun event(
        id: Long,
        title: String,
        date: LocalDate,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        location: String,
        tag: String,
        color: Int,
        zone: ZoneId,
    ): Event = event(id, title, date.atTime(startHour, startMinute), date.atTime(endHour, endMinute), location, tag, color, zone)

    private fun event(
        id: Long,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        location: String,
        tag: String,
        color: Int,
        zone: ZoneId,
        description: String = "演示模式中的示例数据",
        state: Int = STATE_PENDING,
        flags: Int = 0,
    ) = Event(
        id = id,
        title = title,
        startTS = start.atZone(zone).toEpochSecond(),
        endTS = end.atZone(zone).toEpochSecond(),
        location = location,
        description = description,
        color = color,
        timeZone = zone.id,
        tag = tag,
        state = state,
        flags = flags,
    )

    private fun allDayEvent(id: Long, title: String, date: LocalDate, color: Int, zone: ZoneId): Event = event(
        id = id,
        title = title,
        start = date.atStartOfDay(),
        end = date.plusDays(1).atStartOfDay(),
        location = "校园",
        tag = EventTags.GENERAL,
        color = color,
        zone = zone,
        flags = FLAG_ALL_DAY,
    )
}
