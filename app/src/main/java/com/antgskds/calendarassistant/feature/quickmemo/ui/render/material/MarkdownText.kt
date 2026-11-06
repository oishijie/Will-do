package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import android.text.TextUtils
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Markdown 预览渲染。
 *
 * 只做渲染：闪记正文是单个 String，没有上游便签那套段落流模型，
 * 因此不接入 editor 侧的 EditHandler，编辑仍用纯文本输入框。
 */
@Composable
fun MarkdownText(
    markdown: String,
    textSizeSp: Float,
    textColor: Color,
    modifier: Modifier = Modifier,
    linkColor: Color = textColor,
    maxLines: Int = Int.MAX_VALUE,
    lineSpacingExtraPx: Float = 0f
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                ellipsize = TextUtils.TruncateAt.END
                linksClickable = false
                movementMethod = null
            }
        },
        update = { view ->
            view.maxLines = maxLines
            view.setTextColor(textColor.toArgb())
            view.setLinkTextColor(linkColor.toArgb())
            view.textSize = textSizeSp
            view.setLineSpacing(lineSpacingExtraPx, 1f)
            markwon.setMarkdown(view, markdown)
        }
    )
}
