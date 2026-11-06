package com.antgskds.calendarassistant.platform.floating.capsule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.shared.ui.material.component.WheelTimePicker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 胶囊面板内的「添加日历提醒」浮层。
 *
 * 结构对齐声物记 CalendarConfirmSheet：
 * 标题 + 「响铃闹钟」开关 → 月历选日期 → 时分双拨轮 → 取消 / 添加到日历。
 *
 * 不用 `AppAlertDialog`：悬浮窗是独立 Window（无 Activity），真 Dialog
 * 弹不出来，只能在面板内叠一层。
 */
@Composable
fun CapsuleDateTimePicker(
    initial: LocalDateTime,
    eventTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    var enableAlarm by remember { mutableStateOf(true) }

    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    // 背景遮罩：点空白取消
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 420.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {}
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 待建日程的内容（即卡片文字）+ 响铃闹钟开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "添加日历提醒",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "响铃闹钟",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.widthIn(min = 6.dp))
                Switch(
                    checked = enableAlarm,
                    onCheckedChange = { enableAlarm = it },
                )
            }

            // 日程内容：取自胶囊卡片文字，让用户看清要建的是什么
            Text(
                text = eventTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            // 可滚区：月历 + 拨轮（面板高度有限，小屏时能滚）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CapsuleMonthCalendar(
                    selected = date,
                    onSelect = { date = it },
                )
                WheelTimePicker(
                    initialHour = hour,
                    initialMinute = minute,
                ) { h, m ->
                    hour = h
                    minute = m
                }
            }

            // 底部按钮：取消 / 添加到日历
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(24.dp),
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onDismiss() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "取消",
                        fontSize = 15.sp,
                        color = accent,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(accent, RoundedCornerShape(24.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            onConfirm(
                                LocalDateTime.of(date, LocalTime.of(hour, minute)),
                                enableAlarm,
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "添加到日历",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = onAccent,
                    )
                }
            }
        }
    }
}

/** 识别不到时间时的兜底：今天下一个整点；已过 23 点则落到明天 9 点。 */
fun defaultCapsulePickerTime(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
    val next = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
    return if (next.toLocalDate() == now.toLocalDate()) next
    else LocalDate.now().plusDays(1).atTime(9, 0)
}
