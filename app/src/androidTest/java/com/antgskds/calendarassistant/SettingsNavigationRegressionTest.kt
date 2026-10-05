package com.antgskds.calendarassistant

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiState
import com.antgskds.calendarassistant.feature.settings.shell.ui.render.material.MaterialSettingsDetailScreen
import com.antgskds.calendarassistant.shared.management.catalog.PageCatalog
import org.junit.Rule
import org.junit.Test

class SettingsNavigationRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun phoneCanReturnToInitialPreferencePage() = navigateBackToPreference()

    private fun navigateBackToPreference() {
        var initialNavigation: ((SettingsDestination) -> Unit)? = null
        compose.setContent {
            MaterialTheme {
                MaterialSettingsDetailScreen(
                    state = SettingsDetailUiState(
                        initialRoute = requireNotNull(PageCatalog.routeFor(SettingsDestination.Preference)),
                        uiSize = 2, backgroundEnabled = false, backgroundMiuiBlurEnabled = false,
                        backgroundCardAlphaPercent = 100, pageHasAppBackground = false,
                        isDarkMode = false, hasAppUpdate = false, hapticEnabled = false,
                        navigationPredictiveBackEnabled = true, confirmationPredictiveBackEnabled = true,
                        archiveCount = 0,
                    ),
                    onAction = {},
                ) { _, destination, onNavigate, _ ->
                    if (destination == SettingsDestination.Preference) {
                        // Reuse the original callback after navigating away, as a remembered host may do.
                        if (initialNavigation == null) initialNavigation = onNavigate
                        TextButton(onClick = { initialNavigation?.invoke(SettingsDestination.Weather) }) {
                            Text("测试：打开天气设置")
                        }
                    } else {
                        TextButton(onClick = { initialNavigation?.invoke(SettingsDestination.Preference) }) {
                            Text("测试：回到偏好设置")
                        }
                    }
                }
            }
        }
        repeat(2) {
            compose.onNodeWithText("测试：打开天气设置").assertIsDisplayed().performClick()
            compose.onNodeWithText("测试：回到偏好设置").assertIsDisplayed().performClick()
            compose.onNodeWithText("测试：打开天气设置").assertIsDisplayed()
        }
    }
}
