package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闪记已归档。
 *
 * 归档的闪记（`archived_at` 非空）在这里集中管理：恢复到主列表。
 * 归档不移除内容，所以不展示“彻底删除”，避免误删。
 * 主列表的左滑按钮与多选删除也是归档（同一语义），可从本页恢复。
 */
@Composable
fun QuickMemoArchivedPage(
    memos: List<QuickMemoEntity>,
    onRestore: (QuickMemoEntity) -> Unit,
    onBack: () -> Unit,
    /** 清空已归档（不可恢复）；为空时不展示入口。 */
    onPurgeAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    var showPurgeConfirm by remember { mutableStateOf(false) }

    if (showPurgeConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            title = { Text("清空已归档") },
            text = { Text("将彻底删除 ${memos.size} 条已归档闪记，含语音与图片，删除后无法恢复。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showPurgeConfirm = false
                    onPurgeAll()
                }) { Text("彻底删除") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPurgeConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹ 返回闪记",
                fontSize = 15.sp,
                color = accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(vertical = 8.dp),
            )
            if (memos.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "清空",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showPurgeConfirm = true }
                        .padding(vertical = 8.dp),
                )
            }
        }
        if (memos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "没有已归档的闪记",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(memos, key = { it.id ?: 0L }) { memo ->
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = memo.bodyText.ifBlank {
                                    when (memo.type) {
                                        QuickMemoType.IMAGE -> "图片闪记"
                                        QuickMemoType.VOICE -> "语音闪记"
                                        else -> "无内容"
                                    }
                                },
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatArchivedTime(memo.archivedAt ?: memo.updatedAt),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "恢复",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = onAccent,
                            modifier = Modifier
                                .background(accent, RoundedCornerShape(16.dp))
                                .clickable { onRestore(memo) }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatArchivedTime(ts: Long): String {
    return SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(ts))
}
