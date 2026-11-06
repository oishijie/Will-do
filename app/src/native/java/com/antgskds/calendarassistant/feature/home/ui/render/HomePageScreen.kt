package com.antgskds.calendarassistant.feature.home.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiAction
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiState
import com.antgskds.calendarassistant.feature.home.ui.render.material.MaterialHomePage
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination

@Composable
fun HomePageScreen(
    state: HomePageUiState,
    onAction: (HomePageUiAction) -> Unit,
    allEventsContent: @Composable (String, Dp) -> Unit,
    quickMemoContent: @Composable (String, Dp) -> Unit,
    currentPageKey: String,
    pageOrder: List<String>,
    uiSize: Int,
    pickupTimestamp: Long,
    isActionExpanded: Boolean,
    onActionExpandedChange: (Boolean) -> Unit,
    searchRequestId: Int,
    imageRequestId: Int,
    onRequestsHandled: () -> Unit,
    isSidebarOpen: Boolean,
    onPageChange: (String) -> Unit,
    onAddEventClick: () -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
    onRequestClearQuickMemos: () -> Unit,
    quickMemoCount: Int,
    onAddQuickMemo: () -> Unit = {},
    /** 顶栏「⋮ → 回收站」：闪记页切到归档区。 */
    onOpenQuickMemoArchived: () -> Unit = {},
    quickMemoFolders: List<com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoFolderEntity> = emptyList(),
    selectedQuickMemoFolderId: String? = null,
    onSelectQuickMemoFolder: (String?) -> Unit = {},
    onCreateQuickMemoFolder: (String) -> Unit = {},
    onRenameQuickMemoFolder: (String, String) -> Unit = { _, _ -> },
    onDeleteQuickMemoFolder: (String) -> Unit = {},
    onStartQuickMemoSelection: () -> Unit = {},
    onVoiceSchedule: () -> Unit = {},
    onScheduleExpandedChange: (Boolean) -> Unit,
    onScheduleProgressChange: (Float) -> Unit,
    onScheduleOffsetChange: (Float) -> Unit,
    onOpenWeatherDetail: () -> Unit,
    onNavigateToSettings: (SettingsDestination) -> Unit,
    onRememberCalendarViewMode: (String) -> Unit = {},
) {
    MaterialHomePage(
        state = state,
        onAction = onAction,
        allEventsContent = allEventsContent,
        quickMemoContent = quickMemoContent,
        currentPageKey = currentPageKey,
        pageOrder = pageOrder,
        uiSize = uiSize,
        pickupTimestamp = pickupTimestamp,
        isActionExpanded = isActionExpanded,
        onActionExpandedChange = onActionExpandedChange,
        searchRequestId = searchRequestId,
        imageRequestId = imageRequestId,
        onRequestsHandled = onRequestsHandled,
        isSidebarOpen = isSidebarOpen,
        onPageChange = onPageChange,
        onAddEventClick = onAddEventClick,
        onEditItem = onEditItem,
        onRequestDeleteItem = onRequestDeleteItem,
        onRequestClearQuickMemos = onRequestClearQuickMemos,
        quickMemoCount = quickMemoCount,
        onAddQuickMemo = onAddQuickMemo,
        onOpenQuickMemoArchived = onOpenQuickMemoArchived,
        quickMemoFolders = quickMemoFolders,
        selectedQuickMemoFolderId = selectedQuickMemoFolderId,
        onSelectQuickMemoFolder = onSelectQuickMemoFolder,
        onCreateQuickMemoFolder = onCreateQuickMemoFolder,
        onRenameQuickMemoFolder = onRenameQuickMemoFolder,
        onDeleteQuickMemoFolder = onDeleteQuickMemoFolder,
        onStartQuickMemoSelection = onStartQuickMemoSelection,
        onVoiceSchedule = onVoiceSchedule,
        onScheduleExpandedChange = onScheduleExpandedChange,
        onScheduleProgressChange = onScheduleProgressChange,
        onScheduleOffsetChange = onScheduleOffsetChange,
        onOpenWeatherDetail = onOpenWeatherDetail,
        onRememberCalendarViewMode = onRememberCalendarViewMode,
    )
}
