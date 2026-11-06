package com.antgskds.calendarassistant.platform.floating.capsule

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 录音态胶囊。对齐声物记 OverlayVoiceMemoBar 的核心语义：
 * 停靠缘一侧固定停止按钮，另一侧计时随录音时长向屏内生长。
 */
@Composable
fun CapsuleRecordingBar(
    elapsedMs: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    dockLeft: Boolean = false,
    saving: Boolean = false,
    transcribing: Boolean = false,
) {
    val elapsedSeconds = (elapsedMs.coerceAtLeast(0L) / 1000f).coerceAtMost(300f)
    val targetWidth = if (saving || transcribing) OverlayConstants.RECORD_BAR_SAVING_WIDTH else
        (OverlayConstants.RECORD_BAR_BASE_WIDTH +
            OverlayConstants.RECORD_BAR_GROWTH_PER_SECOND * elapsedSeconds)
            .coerceAtMost(OverlayConstants.RECORD_BAR_MAX_WIDTH)
    val width by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(300, easing = LinearEasing),
        label = "record_bar_width",
    )
    Box(
        modifier = modifier
            .width(width)
            .height(OverlayConstants.RECORD_BAR_HEIGHT)
            .clip(RoundedCornerShape(OverlayConstants.RECORD_BAR_HEIGHT / 2))
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        if (saving || transcribing) {
            Text(
                text = if (transcribing) "转写中…" else "正在保存…",
                style = TextStyle(fontSize = 14.sp, color = Color.White),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (dockLeft) OverlayConstants.RECORD_STOP_SIZE else 0.dp,
                        end = if (dockLeft) 0.dp else OverlayConstants.RECORD_STOP_SIZE,
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(OverlayConstants.RECORD_DOT_COLOR),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatElapsed(elapsedMs),
                    style = TextStyle(fontSize = 15.sp, color = Color.White, fontFeatureSettings = "tnum"),
                )
            }
            Box(
                modifier = Modifier
                    .align(if (dockLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(
                        start = if (dockLeft) OverlayConstants.RECORD_STOP_SIZE else 0.dp,
                        end = if (dockLeft) 0.dp else OverlayConstants.RECORD_STOP_SIZE,
                    )
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
            Box(
                modifier = Modifier.align(if (dockLeft) Alignment.CenterStart else Alignment.CenterEnd),
            ) { StopButton(onStop) }
        }
    }
}

@Composable
private fun StopButton(onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .size(OverlayConstants.RECORD_STOP_SIZE)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onStop() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(OverlayConstants.RECORD_STOP_VISUAL_SIZE)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = "停止录音",
                tint = Color.White,
                modifier = Modifier.size(OverlayConstants.RECORD_STOP_ICON_SIZE),
            )
        }
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
