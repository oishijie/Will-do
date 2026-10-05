package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics

/**
 * 输入栏底部的一个方形图标按钮（图片 / 链接 / 实况通知 / 语音）。
 *
 * 只有图标、不带文字标签——输入框本身已经表达了「在写什么」，
 * 再在每个图标下面挂一行小字只会把卡片撑高、显脏。
 */
data class MemoInputAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    /** 录音中之类的激活态，激活时方底用主色实心。 */
    val active: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 键盘上方的记事输入栏（悬浮圆角卡片）。
 *
 * 布局照参考图：卡片上排是输入框（空时显示两行提示），下排一排方形图标，
 * 最右侧是圆形发送键，与图标同一行。左右留白让卡片浮在键盘上方，
 * 而不是顶到屏幕两侧。
 *
 * 挂载时自动抢焦点弹出键盘——用户是从顶栏菜单点进来的，
 * 再让他手动点一次输入框说不过去。
 */
@Composable
fun MemoInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    actions: List<MemoInputAction>,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val chipBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val chipTint = MaterialTheme.colorScheme.onSurfaceVariant
    val canSend = text.isNotBlank()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // 提示文字始终参与布局，只切换可见性（而不是 if 掉）。
                // 否则输入第一个字后占位消失、输入框塌成一行，整张卡片会
                // 突然变矮——参考图里输入文字后圆角矩形的长宽都是不变的。
                Column(
                    modifier = Modifier.alpha(if (text.isEmpty()) 1f else 0f)
                ) {
                    Text(
                        text = "需要记什么？",
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "换行可添加备注描述…",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                actions.forEach { action ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (action.active) MaterialTheme.colorScheme.primary else chipBg)
                            .clickable {
                                haptics.click()
                                action.onClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = if (action.active) MaterialTheme.colorScheme.onPrimary else chipTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (canSend) MaterialTheme.colorScheme.primary else chipBg)
                        .clickable(enabled = canSend) {
                            haptics.confirm()
                            onSend()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Send,
                        contentDescription = "发送",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                        else chipTint.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
