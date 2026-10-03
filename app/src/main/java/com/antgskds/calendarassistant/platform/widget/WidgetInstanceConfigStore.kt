package com.antgskds.calendarassistant.platform.widget

import android.content.Context
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

class WidgetInstanceConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(appWidgetId: Int, fallbackType: WidgetType, settings: MySettings): WidgetInstanceConfig {
        val prefix = keyPrefix(appWidgetId)
        val storedType = WidgetType.fromStorageKey(prefs.getString(prefix + KEY_TYPE, null)) ?: fallbackType
        val themeMode = prefs.getInt(prefix + KEY_THEME_MODE, defaultAppearance(fallbackType, settings).themeMode)
        val alpha = prefs.getFloat(prefix + KEY_ALPHA, defaultAppearance(fallbackType, settings).backgroundAlpha)
        return WidgetInstanceConfig(
            type = storedType,
            appearance = WidgetAppearanceConfig(themeMode, alpha).normalized()
        )
    }

    fun ensureConfig(appWidgetId: Int, type: WidgetType, settings: MySettings): WidgetInstanceConfig {
        val prefix = keyPrefix(appWidgetId)
        if (!prefs.contains(prefix + KEY_TYPE)) {
            val appearance = defaultAppearance(type, settings).normalized()
            prefs.edit()
                .putString(prefix + KEY_TYPE, type.storageKey)
                .putInt(prefix + KEY_THEME_MODE, appearance.themeMode)
                .putFloat(prefix + KEY_ALPHA, appearance.backgroundAlpha)
                .apply()
            return WidgetInstanceConfig(type, appearance)
        }
        return getConfig(appWidgetId, type, settings)
    }

    fun saveConfig(appWidgetId: Int, config: WidgetInstanceConfig) {
        val prefix = keyPrefix(appWidgetId)
        val appearance = config.appearance.normalized()
        prefs.edit()
            .putString(prefix + KEY_TYPE, config.type.storageKey)
            .putInt(prefix + KEY_THEME_MODE, appearance.themeMode)
            .putFloat(prefix + KEY_ALPHA, appearance.backgroundAlpha)
                .apply()
    }

    fun saveDefaultAppearance(type: WidgetType, appearance: WidgetAppearanceConfig) {
        val prefix = defaultKeyPrefix(type)
        val normalized = appearance.normalized()
        prefs.edit()
            .putInt(prefix + KEY_THEME_MODE, normalized.themeMode)
            .putFloat(prefix + KEY_ALPHA, normalized.backgroundAlpha)
            .apply()
    }

    fun deleteConfig(appWidgetId: Int) {
        val prefix = keyPrefix(appWidgetId)
        prefs.edit()
            .remove(prefix + KEY_TYPE)
            .remove(prefix + KEY_THEME_MODE)
            .remove(prefix + KEY_ALPHA)
            .apply()
    }

    fun defaultAppearance(type: WidgetType, settings: MySettings): WidgetAppearanceConfig {
        val fallback = WidgetAppearanceConfig(settings.widgetThemeMode, settings.widgetBackgroundAlpha).normalized()
        val prefix = defaultKeyPrefix(type)
        if (!prefs.contains(prefix + KEY_THEME_MODE) && !prefs.contains(prefix + KEY_ALPHA)) return fallback
        return WidgetAppearanceConfig(
            themeMode = prefs.getInt(prefix + KEY_THEME_MODE, fallback.themeMode),
            backgroundAlpha = prefs.getFloat(prefix + KEY_ALPHA, fallback.backgroundAlpha)
        ).normalized()
    }

    private fun keyPrefix(appWidgetId: Int): String = "widget_$appWidgetId."
    private fun defaultKeyPrefix(type: WidgetType): String = "default_${type.storageKey}."

    private companion object {
        const val PREFS_NAME = "widget_instance_config"
        const val KEY_TYPE = "type"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ALPHA = "alpha"
    }
}
