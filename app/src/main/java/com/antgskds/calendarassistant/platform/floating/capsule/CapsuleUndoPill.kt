package com.antgskds.calendarassistant.platform.floating.capsule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 划走删除后的撤销气泡。
 *
 * 对齐声物记 UndoDeletePill：面板顶部浮一条胶囊，倒计时内可撤销；
 * 到期后由调用方结束撤销窗口；是否清除数据由上层决定。深色底 + 白色文字。
 */
@Composable
fun CapsuleUndoPill(
    message: String,
    visible: Boolean,
    undoWindowMs: Long,
    undoKey: Long,
    fontSizeStep: Int = 0,
    onUndo: (Long) -> Unit,
    onExpired: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 每次显示都重新计时；visible 变 false 时取消计时，避免旧计时误触发
    LaunchedEffect(visible, message, undoKey) {
        if (!visible) return@LaunchedEffect
        delay(undoWindowMs)
        onExpired(undoKey)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { -it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                fontSize = (13 + fontSizeStep).sp,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "撤销",
                fontSize = (13 + fontSizeStep).sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6F9AF0),
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onUndo(undoKey) }
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            )
        }
    }
}
