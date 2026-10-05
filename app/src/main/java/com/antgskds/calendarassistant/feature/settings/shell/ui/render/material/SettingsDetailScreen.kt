package com.antgskds.calendarassistant.feature.settings.shell.ui.render.material

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.antgskds.calendarassistant.shared.management.catalog.PageCatalog
import com.antgskds.calendarassistant.shared.ui.material.component.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.shared.ui.material.component.AppPageScaffold
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBar
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBarBackButton
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiAction
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiState
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.app.ui.navigation.navBackwardEnterTransition
import com.antgskds.calendarassistant.app.ui.navigation.navBackwardExitTransition
import com.antgskds.calendarassistant.app.ui.navigation.navForwardEnterTransition
import com.antgskds.calendarassistant.app.ui.navigation.navForwardExitTransition
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

private fun NavGraphBuilder.settingsPageComposable(
    route: String,
    content: @Composable () -> Unit,
) {
    composable(
        route = route,
        enterTransition = { navForwardEnterTransition() },
        exitTransition = { navForwardExitTransition() },
        popEnterTransition = { navBackwardEnterTransition() },
        popExitTransition = { navBackwardExitTransition() },
    ) {
        content()
    }
}

@Composable
fun MaterialSettingsDetailScreen(
    state: SettingsDetailUiState,
    onAction: (SettingsDetailUiAction) -> Unit,
    pageContent: @Composable (
        route: String,
        destination: SettingsDestination,
        onNavigateTo: (SettingsDestination) -> Unit,
        onNavigateRoute: (String) -> Unit,
    ) -> Unit,
) {
    val latestState by rememberUpdatedState(state)
    val latestAction by rememberUpdatedState(onAction)
    val latestPageContent by rememberUpdatedState(pageContent)
    val settingsNavController = rememberNavController()
    val backStackEntry by settingsNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: state.initialRoute

    fun navigateToDestination(target: SettingsDestination) {
        if (target == SettingsDestination.Logout) {
            onAction(SettingsDetailUiAction.Logout)
            return
        }

        val targetRoute = PageCatalog.routeFor(target) ?: return
        if (targetRoute == settingsNavController.currentDestination?.route) {
            return
        }

        settingsNavController.navigate(targetRoute) {
            launchSingleTop = true
        }
    }

    fun navigateToRoute(targetRoute: String) {
        if (targetRoute == settingsNavController.currentDestination?.route) return
        settingsNavController.navigate(targetRoute) {
            launchSingleTop = true
        }
    }

    fun handleBackNavigation() {
        when {
            settingsNavController.previousBackStackEntry != null -> settingsNavController.popBackStack()
            else -> onAction(SettingsDetailUiAction.ExitSettings)
        }
    }

    AppBackgroundStyleTheme(
        enabled = state.backgroundEnabled,
        miuiBlurEnabled = state.backgroundMiuiBlurEnabled,
        cardAlphaPercent = state.backgroundCardAlphaPercent,
    ) {
        val settingsContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                NavHost(
                    navController = settingsNavController,
                    startDestination = state.initialRoute,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PageCatalog.navigablePages.forEach { page ->
                        val route = requireNotNull(page.route)
                        settingsPageComposable(route) {
                            MaterialSettingsPage(
                                route = route,
                                destination = page.destination,
                                title = page.title,
                                state = latestState,
                                onBack = ::handleBackNavigation,
                                onAction = latestAction,
                            ) {
                                latestPageContent(
                                    route,
                                    page.destination,
                                    ::navigateToDestination,
                                    ::navigateToRoute,
                                )
                            }
                        }
                    }

                    PageCatalog.weatherDetailPage.let { page ->
                        settingsPageComposable(page.route) {
                            MaterialSettingsPage(
                                route = page.route,
                                destination = page.parentDestination,
                                title = page.title,
                                state = latestState,
                                onBack = ::handleBackNavigation,
                                onAction = latestAction,
                            ) {
                                latestPageContent(
                                    page.route,
                                    page.parentDestination,
                                    ::navigateToDestination,
                                    ::navigateToRoute,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 设置页统一单栏全屏：手机与平板一致，页面切换只靠返回栈，不再提供侧栏/抽屉导航。
        settingsContent()
    }

    BackHandler(enabled = !state.navigationPredictiveBackEnabled) {
        handleBackNavigation()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialSettingsPage(
    route: String,
    destination: SettingsDestination,
    title: String,
    state: SettingsDetailUiState,
    onBack: () -> Unit,
    onAction: (SettingsDetailUiAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = rememberAppHaptics(state.hapticEnabled)
    val pageContainerColor = if (state.pageHasAppBackground) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.background
    }
    val scrollWholePage = destination == SettingsDestination.About || destination == SettingsDestination.AppUpdate
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showClearArchivesConfirm by rememberSaveable(route) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AppPageScaffold(
            containerColor = pageContainerColor,
            contentMaxWidth = 960.dp,
            scrollState = if (scrollWholePage) rememberScrollState() else null,
            edgeToEdgeContent = !scrollWholePage,
            topBar = {
                val navigationContent: @Composable () -> Unit = {
                    AppTopBarBackButton(
                        onClick = { haptics.click(); onBack() },
                        iconSize = when (state.uiSize) {
                            1 -> 24.dp
                            2 -> 28.dp
                            else -> 32.dp
                        },
                    )
                }
                val actionsContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    if (destination == SettingsDestination.Archives && state.archiveCount > 0) {
                        IconButton(
                            onClick = {
                                haptics.click()
                                showClearArchivesConfirm = true
                            },
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "清空归档",
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                AppTopBar(
                    title = title,
                    containerColor = pageContainerColor,
                    navigationIcon = navigationContent,
                    actions = actionsContent,
                )
            },
        ) {
            content()
        }

        PredictiveFloatingActionCard(
            visible = showClearArchivesConfirm,
            title = "确认清空",
            content = "此操作将永久删除 ${state.archiveCount} 条归档事件。\n删除后将无法恢复。",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            isLoading = false,
            predictiveBackEnabled = state.confirmationPredictiveBackEnabled,
            onConfirm = {
                showClearArchivesConfirm = false
                onAction(SettingsDetailUiAction.ClearArchives)
            },
            onDismiss = { showClearArchivesConfirm = false },
            modifier = Modifier.padding(bottom = bottomInset),
        )
    }
}
