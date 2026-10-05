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
    memoSegment: com.antgskds.calendarassistant.feature.quickmemo.ui.contract.MemoStreamSegment =
        com.antgskds.calendarassistant.feature.quickmemo.ui.contract.MemoStreamSegment.ALL,
    onMemoSegmentChange: (com.antgskds.calendarassistant.feature.quickmemo.ui.contract.MemoStreamSegment) -> Unit = {},
    onAddQuickMemo: () -> Unit = {},
    onAddFlashNote: () -> Unit = {},
    onVoiceSchedule: () -> Unit = {},
    onScheduleExpandedChange: (Boolean) -> Unit,
    onScheduleProgressChange: (Float) -> Unit,
    onScheduleOffsetChange: (Float) -> Unit,
    onOpenWeatherDetail: () -> Unit,
    onNavigateToSettings: (SettingsDestination) -> Unit,
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
        memoSegment = memoSegment,
        onMemoSegmentChange = onMemoSegmentChange,
        onAddQuickMemo = onAddQuickMemo,
        onAddFlashNote = onAddFlashNote,
        onVoiceSchedule = onVoiceSchedule,
        onScheduleExpandedChange = onScheduleExpandedChange,
        onScheduleProgressChange = onScheduleProgressChange,
        onScheduleOffsetChange = onScheduleOffsetChange,
        onOpenWeatherDetail = onOpenWeatherDetail,
    )
}
