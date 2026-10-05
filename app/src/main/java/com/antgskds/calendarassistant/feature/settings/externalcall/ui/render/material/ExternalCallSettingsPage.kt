package com.antgskds.calendarassistant.feature.settings.externalcall.ui.render.material

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.core.service.shortcut.ShortcutHandleActivity
import com.antgskds.calendarassistant.shared.api.WillDoAgentReceiver
import com.antgskds.calendarassistant.shared.operation.WillDoAgentContract
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.material.settings.ActionSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.AppSettingsDivider

/**
 * 外部调用页。
 *
 * 把本应用对外暴露的入口集中列出来，每条点一下即可复制标识，方便贴进
 * 自动化工具（Tasker / MacroDroid / 快捷指令）或第三方应用的集成配置里。
 *
 * ⚠️ 所有条目都**直接引用实现处的常量**（action / 类名 / authority），
 * 不在本文件里硬编码字符串——实现改了这里会自动跟随。
 *
 * ⛔ `ShortcutHandleActivity` 在清单里**没有 `<intent-filter>`**，外部无法隐式调起，
 * 必须显式指定组件。因此「快捷指令」里的 action 需要与「快捷活动」里的类名配合使用，
 * 页面顶部已做说明。
 */
@Composable
fun ExternalCallSettingsPage(
    hapticEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics(hapticEnabled)
    val bottomInset = LocalAppPageBottomPadding.current

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
    )
    val cardTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val hintStyle = MaterialTheme.typography.bodySmall.copy(
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
    )

    fun copyEntry(entry: ExternalCallEntry) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(entry.title, entry.identifier))
        haptics.confirm()
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    CompositionLocalProvider(LocalAppHapticsEnabled provides hapticEnabled) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "以下入口可被自动化工具或第三方应用调起。「快捷指令」需与「快捷活动」中的组件配合使用；" +
                    "Agent 接口需申请 AGENT_API 权限。",
                style = hintStyle,
            )

            Text("快捷指令", style = sectionTitleStyle)
            ExternalCallCard(
                entries = QUICK_COMMANDS,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                onCopy = { entry -> copyEntry(entry) },
            )

            Text("快捷活动", style = sectionTitleStyle)
            ExternalCallCard(
                entries = QUICK_ACTIVITIES,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                onCopy = { entry -> copyEntry(entry) },
            )

            Text("外部数据接口", style = sectionTitleStyle)
            ExternalCallCard(
                entries = EXTERNAL_APIS,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                onCopy = { entry -> copyEntry(entry) },
            )
        }
    }
}

@Composable
private fun ExternalCallCard(
    entries: List<ExternalCallEntry>,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    onCopy: (ExternalCallEntry) -> Unit,
) {
    AppSettingsCard {
        entries.forEachIndexed { index, entry ->
            if (index > 0) AppSettingsDivider()
            ActionSettingItem(
                title = entry.title,
                subtitle = entry.identifier,
                value = "",
                icon = Icons.Rounded.ContentCopy,
                enabled = true,
                hapticOnClick = false,
                onClick = { onCopy(entry) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
            )
        }
    }
}

/** 一条对外调用入口：展示名 + 可直接复制的标识。 */
private data class ExternalCallEntry(
    val title: String,
    val identifier: String,
)

private val SHORTCUT_ACTIVITY_CLASS: String = ShortcutHandleActivity::class.java.name

private val QUICK_COMMANDS: List<ExternalCallEntry> = listOf(
    ExternalCallEntry("截图识别", ShortcutHandleActivity.ACTION_QUICK_CAPTURE),
    ExternalCallEntry("悬浮窗 · 日程", ShortcutHandleActivity.ACTION_OPEN_FLOATING),
    ExternalCallEntry("悬浮窗 · 随手记", ShortcutHandleActivity.ACTION_OPEN_FLOATING_NOTE),
    ExternalCallEntry("语音随口记", ShortcutHandleActivity.ACTION_START_QUICK_MEMO_VOICE),
)

private val QUICK_ACTIVITIES: List<ExternalCallEntry> = listOf(
    ExternalCallEntry("快捷方式中转", SHORTCUT_ACTIVITY_CLASS),
)

private val EXTERNAL_APIS: List<ExternalCallEntry> = listOf(
    ExternalCallEntry("Agent 数据读取", WillDoAgentContract.BASE_URI),
    ExternalCallEntry("Agent 调用", WillDoAgentReceiver.ACTION_AGENT_CALL),
)
