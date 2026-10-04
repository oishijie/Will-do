package com.antgskds.calendarassistant.feature.home.ui.connector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiAction
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.connector.QuickMemoPage
import com.antgskds.calendarassistant.feature.schedule.ui.connector.AllEventsRoute
import com.antgskds.calendarassistant.feature.home.ui.render.HomePageScreen
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination

@Composable
fun HomePageRoute(
    viewModel: MainViewModel,
    currentPageKey: String,
    pageOrder: List<String>,
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    isActionExpanded: Boolean = false,
    onActionExpandedChange: (Boolean) -> Unit = {},
    searchRequestId: Int = 0,
    imageRequestId: Int = 0,
    onRequestsHandled: () -> Unit = {},
    isSidebarOpen: Boolean = false,
    isWideNavigation: Boolean = false,
    isTwoPane: Boolean = false,
    openQuickMemoId: Long? = null,
    onQuickMemoOpened: () -> Unit = {},
    onPageChange: (String) -> Unit = {},
    onAddEventClick: () -> Unit = {},
    onEditItem: (ScheduleDisplayItem) -> Unit = {},
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit = {},
    onRequestDeleteQuickMemo: (QuickMemoEntity) -> Unit = {},
    onRequestClearQuickMemos: () -> Unit = {},
    quickMemoCount: Int = 0,
    onOpenQuickMemoDetail: (Long) -> Unit = {},
    onScheduleExpandedChange: (Boolean) -> Unit = {},
    onScheduleProgressChange: (Float) -> Unit = {},
    onScheduleOffsetChange: (Float) -> Unit = {},
    onOpenWeatherDetail: () -> Unit = {},
    onNavigateToSettings: (SettingsDestination) -> Unit = {},
    settingsOverride: MySettings? = null,
) {
    val mainState by viewModel.uiState.collectAsState()
    val demoModeEnabled = mainState.settings.developerOptionsEnabled &&
        mainState.settings.developerDemoModeEnabled
    val state = remember(mainState, settingsOverride) {
        HomePageUiState(
            selectedDate = mainState.selectedDate,
            today = mainState.today,
            timeRefreshToken = mainState.timeRefreshToken,
            revealedItemKey = mainState.revealedItemKey,
            currentDateEvents = mainState.currentDateEvents,
            tomorrowEvents = mainState.tomorrowEvents,
            datesWithEvents = mainState.datesWithEvents,
            calendarItems = mainState.calendarItems,
            agendaItems = mainState.homeAgendaItems,
            agendaFutureItems = mainState.homeAgendaFutureItems,
            agendaFutureDays = mainState.homeAgendaFutureDays,
            agendaReady = mainState.homeAgendaReady,
            settings = settingsOverride ?: mainState.settings,
            weatherData = mainState.weatherData,
        )
    }

    HomePageScreen(
        state = state,
        onAction = { action ->
            when (action) {
                HomePageUiAction.LoadMoreAgenda -> viewModel.loadMoreFutureHomeAgenda()
                is HomePageUiAction.SelectDate -> viewModel.updateSelectedDate(action.date)
                is HomePageUiAction.RevealItem -> viewModel.onRevealItem(action.itemKey)
                is HomePageUiAction.DeleteItem -> {
                    if (!demoModeEnabled) action.item.eventId?.let { eventId -> viewModel.deleteEvent(eventId) }
                }
                is HomePageUiAction.ArchiveItem -> if (!demoModeEnabled) viewModel.archiveItem(action.item.action)
            }
        },
        allEventsContent = { searchQuery, extraBottomPadding ->
            AllEventsRoute(
                viewModel = viewModel,
                onEditItem = onEditItem,
                uiSize = uiSize,
                searchQuery = searchQuery,
                extraBottomPadding = extraBottomPadding,
                twoPane = isTwoPane,
                onRequestDeleteItem = onRequestDeleteItem,
                hapticEnabled = state.settings.hapticFeedbackEnabled,
                onOpenWeatherDetail = onOpenWeatherDetail,
            )
        },
        quickMemoContent = { searchQuery, extraBottomPadding ->
            QuickMemoPage(
                viewModel = viewModel,
                searchQuery = searchQuery,
                uiSize = uiSize,
                extraBottomPadding = extraBottomPadding,
                twoPane = isTwoPane,
                openMemoId = openQuickMemoId,
                onMemoOpened = onQuickMemoOpened,
                onOpenDetail = onOpenQuickMemoDetail,
                onPendingDeleteChange = { memo -> memo?.let(onRequestDeleteQuickMemo) },
                hapticEnabled = state.settings.hapticFeedbackEnabled,
            )
        },
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
        isWideNavigation = isWideNavigation,
        isTwoPane = isTwoPane,
        onPageChange = onPageChange,
        onAddEventClick = onAddEventClick,
        onEditItem = { item -> if (!demoModeEnabled || isTwoPane) onEditItem(item) },
        onRequestDeleteItem = { item -> if (!demoModeEnabled) onRequestDeleteItem(item) },
        onRequestClearQuickMemos = { if (!demoModeEnabled) onRequestClearQuickMemos() },
        quickMemoCount = quickMemoCount,
        onScheduleExpandedChange = onScheduleExpandedChange,
        onScheduleProgressChange = onScheduleProgressChange,
        onScheduleOffsetChange = onScheduleOffsetChange,
        onOpenWeatherDetail = onOpenWeatherDetail,
        onNavigateToSettings = onNavigateToSettings,
    )
}
