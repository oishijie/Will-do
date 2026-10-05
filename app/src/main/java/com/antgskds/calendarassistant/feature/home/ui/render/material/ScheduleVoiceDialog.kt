package com.antgskds.calendarassistant.feature.home.ui.render.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 日程页「语音识别」的录音弹窗。
 *
 * 流程：说话 → 停止 → 本地转写 → 交给识别链路解析成日程。
 *
 * 用弹窗而不是就地静默录音：录音必须有**可见的进行中状态**和**明确的停止入口**，
 * 否则用户既不知道录上了没有，也不知道怎么结束。
 */
@Composable
internal fun ScheduleVoiceDialog(
    isSaving: Boolean,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        // 转写中不允许随手关掉，否则用户以为失败了。
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("语音识别") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text("正在转写并识别…", textAlign = TextAlign.Center)
                } else {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                    Text("正在聆听，说完点「停止」", textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onStop
            ) { Text("停止") }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onDismiss
            ) { Text("取消") }
        }
    )
}
