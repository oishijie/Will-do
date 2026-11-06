package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarExtraHeight
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarShadowPadding
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoFolderEntity
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.edition.EditionCheckbox
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppDropdownMenu
import com.antgskds.calendarassistant.shared.ui.material.component.AppMenuItem
import com.antgskds.calendarassistant.shared.ui.material.component.AppModalBottomSheet
import com.antgskds.calendarassistant.shared.ui.material.component.AppSheetAction
import com.antgskds.calendarassistant.shared.ui.material.component.AppSheetActionRole
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundCardAlphaPercent
import com.antgskds.calendarassistant.app.ui.theme.material.background.rememberAppBackgroundStylePalette
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding

/** 多选模式底部工具条：全选 / 移动至 / 删除。 */
@Composable
fun QuickMemoSelectionToolbar(
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    enabled: Boolean,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundPalette = rememberAppBackgroundStylePalette(
        enabled = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled
    )
    val barHeight = IntegratedFloatingBarHeight + IntegratedFloatingBarExtraHeight
    val itemWidth = 72.dp
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barSurfaceAlpha = MySettings.normalizeAppBackgroundCardAlphaPercent(
        LocalAppBackgroundCardAlphaPercent.current
    ) / 100f
    val containerColor = if (backgroundMode) {
        backgroundPalette.surface.copy(alpha = barSurfaceAlpha)
    } else if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (backgroundMode) backgroundPalette.content else MaterialTheme.colorScheme.onSurfaceVariant
    val disabledColor = if (backgroundMode) {
        backgroundPalette.secondaryContent.copy(alpha = 0.46f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Surface(
        modifier = modifier
            .padding(bottom = LocalAppPageBottomPadding.current)
            .padding(horizontal = 16.dp)
            .padding(bottom = IntegratedFloatingBarShadowPadding)
            .height(barHeight),
        shape = CircleShape,
        color = if (backgroundMode) Color.Transparent else containerColor,
        contentColor = contentColor,
        shadowElevation = if (backgroundMode) 0.dp else 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            QuickMemoActionButton(
                icon = ImageVector.vectorResource(R.drawable.quickmemo_select_all),
                contentDescription = if (allSelected) "取消全选" else "全选",
                isActive = false,
                width = itemWidth,
                indicatorColor = Color.Transparent,
                contentColor = contentColor,
                disabledColor = disabledColor,
                onClick = onSelectAll
            )
            QuickMemoActionButton(
                icon = Icons.Rounded.DriveFileMove,
                contentDescription = "移动至",
                isActive = false,
                enabled = enabled,
                width = itemWidth,
                indicatorColor = Color.Transparent,
                contentColor = contentColor,
                disabledColor = disabledColor,
                onClick = onMove
            )
            QuickMemoActionButton(
                icon = Icons.Rounded.Delete,
                contentDescription = "删除",
                isActive = false,
                enabled = enabled,
                width = itemWidth,
                indicatorColor = Color.Transparent,
                contentColor = contentColor,
                disabledColor = disabledColor,
                onClick = onDelete
            )
        }
    }
}

/**
 * 顶栏「⋮」里追加的文件夹筛选项。
 *
 * [folderItems] 由调用方给出「全部 / 未分组 / 各文件夹」三项；
 * 新建与管理为独立动作，不进筛选组。
 */
@Composable
fun QuickMemoFolderFilterItems(
    folders: List<QuickMemoFolderEntity>,
    selectedFolderId: String?,
    onSelect: (String?) -> Unit,
    dividerBefore: Boolean = true,
): List<AppMenuItem> = buildList {
    add(
        AppMenuItem(
            text = "全部闪记",
            onClick = { onSelect(null) },
            icon = Icons.Rounded.Folder,
            selected = selectedFolderId == null,
            dividerBefore = dividerBefore
        )
    )
    add(
        AppMenuItem(
            text = "未分组",
            onClick = { onSelect("") },
            icon = Icons.Rounded.FolderOff,
            selected = selectedFolderId == ""
        )
    )
    folders.forEach { folder ->
        add(
            AppMenuItem(
                text = folder.name,
                onClick = { onSelect(folder.id) },
                icon = Icons.Rounded.Folder,
                selected = selectedFolderId == folder.id
            )
        )
    }
}

/** 批量移动的目标选择：未分组 + 各文件夹，可现场新建。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoFolderPicker(
    visible: Boolean,
    folders: List<QuickMemoFolderEntity>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember(visible) { mutableStateOf(false) }
    val busy by rememberUpdatedState(isLoading)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy },
    )
    if (visible && !creating) AppModalBottomSheet(
        title = "移动至",
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        actions = listOf(
            AppSheetAction("取消", onDismiss, AppSheetActionRole.Secondary, enabled = !busy),
            AppSheetAction("新建文件夹", { creating = true }, enabled = !busy),
        ),
    ) {
        ListItem(
            headlineContent = { Text("未分组") },
            leadingContent = { Icon(Icons.Rounded.FolderOff, null) },
            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onSelect(null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        folders.forEach { folder ->
            ListItem(
                headlineContent = { Text(folder.name) },
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onSelect(folder.id) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    QuickMemoFolderNameSheet(visible && creating, { creating = false }, onCreate)
}

/** 文件夹管理：单选后重命名或删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoManageFoldersSheet(
    visible: Boolean,
    folders: List<QuickMemoFolderEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    if (visible) AppModalBottomSheet(
        title = "管理文件夹",
        subtitle = "选择一个文件夹进行重命名或删除",
        onDismissRequest = onDismiss,
        actions = listOf(
            AppSheetAction("重命名", onRename, enabled = selectedId != null),
            AppSheetAction("删除", onDelete, enabled = selectedId != null),
        ),
    ) {
        folders.forEach { folder ->
            val checked = folder.id == selectedId
            ListItem(
                headlineContent = { Text(folder.name) },
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
                trailingContent = {
                    EditionCheckbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(48.dp))
                },
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = { haptics.selection(); onSelect(if (it) folder.id else null) },
                ),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoFolderNameSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    title: String = "新建文件夹",
    confirmText: String = "创建",
    initialName: String = "",
) {
    var name by remember(visible, initialName) { mutableStateOf(initialName) }
    val save = {
        if (name.isNotBlank()) {
            onSave(name)
            onDismiss()
        }
    }
    if (visible) AppModalBottomSheet(
        title = title,
        onDismissRequest = onDismiss,
        actions = listOf(
            AppSheetAction("取消", onDismiss, AppSheetActionRole.Secondary),
            AppSheetAction(confirmText, save, enabled = name.isNotBlank()),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(32.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("文件夹名称") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        }
    }
}

/** 删除文件夹的二次确认；删除后其中的闪记移至未分组。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoDeleteFolderSheet(
    visible: Boolean,
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (visible) AppModalBottomSheet(
        title = "删除文件夹",
        onDismissRequest = onDismiss,
        actions = listOf(
            AppSheetAction("取消", onDismiss, AppSheetActionRole.Secondary),
            AppSheetAction("删除文件夹", onConfirm, AppSheetActionRole.Destructive),
        ),
    ) {
        Text(
            text = "删除“$folderName”后，其中的闪记将移至未分组。",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}
