package com.antgskds.calendarassistant.feature.home.ui.render

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.home.ui.contract.HomeShellUiState
import com.antgskds.calendarassistant.feature.home.ui.render.material.MaterialHomeScreen

@Composable
fun HomeScreenContent(
    state: HomeShellUiState,
    content: @Composable () -> Unit,
    chrome: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    MaterialHomeScreen(
        state = state,
        content = content,
        chrome = chrome,
        overlay = overlay,
    )
}
