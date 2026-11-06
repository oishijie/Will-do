package com.antgskds.calendarassistant.shared.ui.material.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.platform.web.QuickMemoWebController
import com.antgskds.calendarassistant.platform.web.QuickMemoWebServer

/**
 * 电脑访问服务开关卡片。
 *
 * 版式：标题行左侧图标 + 文字、右侧开关，下方一段说明；
 * 开启后追加展示可访问的局域网地址（多网卡时全部列出，192.168 段优先）。
 * 被胶囊设置页与主设置菜单共用。
 */
@Composable
fun QuickMemoWebServerCard(
    modifier: Modifier = Modifier,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember { QuickMemoWebController.get(context) }
    val serverState by controller.state.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val cardShape = RoundedCornerShape(28.dp)

    // 进页时同步一次真实状态（只读，不在主线程拉服务：绑定含阻塞探测，会卡 UI）
    LaunchedEffect(Unit) { controller.refreshState() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardColor, cardShape)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Computer,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "电脑访问服务",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = serverState.running || serverState.starting,
                onCheckedChange = { enabled ->
                    if (enabled) controller.start() else controller.stop()
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "开启后，同一 Wi-Fi 下的电脑浏览器打开下方地址，即可查看、编辑、删除闪记；" +
                "手机这边新增或修改的闪记也会实时出现在电脑上。运行期间通知栏有一条常驻通知保持服务不中断。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val error = serverState.error
        when {
            error != null -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = error,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            serverState.starting -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "正在启动…",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            serverState.running -> {
                Spacer(Modifier.height(12.dp))
                val addresses = serverState.addresses.ifEmpty { listOf("手机IP") }
                addresses.forEach { ip ->
                    val url = "http://$ip:${QuickMemoWebServer.PORT}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                            .clickable {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
                                android.widget.Toast.makeText(context, "已复制 $url", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = url,
                            fontSize = 14.sp,
                            color = accent,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "复制",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
