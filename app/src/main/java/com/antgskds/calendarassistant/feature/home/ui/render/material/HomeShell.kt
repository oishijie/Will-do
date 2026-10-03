package com.antgskds.calendarassistant.feature.home.ui.render.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.feature.home.ui.contract.HomeShellUiState
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme

@Composable
fun MaterialHomeScreen(
    state: HomeShellUiState,
    content: @Composable () -> Unit,
    chrome: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    AppBackgroundStyleTheme(
        enabled = state.backgroundEnabled,
        miuiBlurEnabled = state.backgroundMiuiBlurEnabled,
        cardAlphaPercent = state.backgroundCardAlphaPercent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.useNavigationRail) {
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (state.backgroundEnabled) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.background
                            }
                        )
                ) {
                    content()
                }
            }

            chrome()
            overlay()
        }
    }
}
