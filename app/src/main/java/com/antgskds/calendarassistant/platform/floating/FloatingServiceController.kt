package com.antgskds.calendarassistant.platform.floating

import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.feature.settings.data.model.QuickMemoRecordingDisplayMode
import com.antgskds.calendarassistant.platform.permission.AndroidPermissionChecker

class FloatingServiceController(
    private val appContext: Context,
    private val permissionCenter: AndroidPermissionChecker,
    private val settingsQueryApi: SettingsQueryApi
) {
    companion object {
        private const val TAG = "FloatingController"
    }

    fun canDrawOverlays(context: Context = appContext): Boolean {
        return permissionCenter.canDrawOverlays(context)
    }

    fun startFloatingService(): Boolean {
        if (!canDrawOverlays()) return false
        val settings = settingsQueryApi.settings.value
        if (!settings.isFloatingWindowEnabled && !settings.voiceInputEnabled) return false
        return try {
            appContext.startService(Intent(appContext, FloatingScheduleService::class.java))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start floating service failed", e)
            false
        }
    }

    /**
     * 彻底隐藏悬浮窗（toggle 用）。
     *
     * 对齐声物记 showOverlay 的 toggle 语义：已显示时再触发立即收起。
     * 用 ACTION_CLOSE 让服务自己走 requestClose，而不是从外部 stopService，
     * 否则语音录音等未收尾的资源会漏掉清理。
     */
    fun hideFloatingService(): Boolean {
        if (!FloatingScheduleService.isShowing) return false
        return try {
            appContext.startService(Intent(appContext, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_REQUEST_CLOSE
            })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Hide floating service failed", e)
            false
        }
    }

    fun startVoiceCaptureService(): Boolean {
        if (!canDrawOverlays()) return false
        if (!settingsQueryApi.settings.value.voiceInputEnabled) return false
        return try {
            if (!permissionCenter.hasRecordAudioPermission(appContext)) {
                appContext.startActivity(Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_REQUEST_RECORD_AUDIO_PERMISSION, true)
                })
                return false
            }
            val target = voiceCaptureServiceIntent(FloatingScheduleService.ACTION_START_VOICE_CAPTURE, QuickMemoVoiceCaptureService.ACTION_START)
            appContext.startService(target)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start voice capture service failed", e)
            false
        }
    }

    fun stopVoiceCaptureService(): Boolean {
        return try {
            val target = when {
                FloatingScheduleService.instance?.isCapturingNow() == true ->
                    Intent(appContext, FloatingScheduleService::class.java).apply {
                        action = FloatingScheduleService.ACTION_STOP_VOICE_CAPTURE
                    }
                QuickMemoVoiceCaptureService.instance?.isCapturingNow() == true ->
                    Intent(appContext, QuickMemoVoiceCaptureService::class.java).apply {
                        action = QuickMemoVoiceCaptureService.ACTION_STOP
                    }
                else -> voiceCaptureServiceIntent(
                    FloatingScheduleService.ACTION_STOP_VOICE_CAPTURE,
                    QuickMemoVoiceCaptureService.ACTION_STOP
                )
            }
            appContext.startService(target)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Stop voice capture service failed", e)
            false
        }
    }

    fun startPickupQrCard(eventId: Long): Boolean {
        return startEventMediaCard(eventId, emptyList())
    }

    fun startEventMediaCard(eventId: Long, imagePaths: List<String>): Boolean {
        if (eventId <= 0L || !canDrawOverlays()) return false
        return try {
            appContext.startService(Intent(appContext, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_SHOW_PICKUP_QR_CARD
                putExtra(FloatingScheduleService.EXTRA_PICKUP_EVENT_ID, eventId)
                putStringArrayListExtra(FloatingScheduleService.EXTRA_MEDIA_IMAGE_PATHS, ArrayList(imagePaths))
            })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start event media card failed", e)
            false
        }
    }

    fun startImagePinMediaCard(id: Long, imagePaths: List<String>): Boolean {
        if (id <= 0 || imagePaths.isEmpty() || imagePaths.any { it.isBlank() } || !canDrawOverlays()) return false
        return runCatching {
            appContext.startService(Intent(appContext, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_SHOW_IMAGE_PIN
                putExtra("image_pin_id", id)
                putStringArrayListExtra(FloatingScheduleService.EXTRA_MEDIA_IMAGE_PATHS, ArrayList(imagePaths))
            })
            true
        }.getOrDefault(false)
    }

    fun startQuickMemoMediaCard(memoId: Long, imagePath: String, bodyText: String): Boolean {
        if (memoId <= 0L || imagePath.isBlank() || !canDrawOverlays()) return false
        return try {
            appContext.startService(Intent(appContext, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_SHOW_QUICK_MEMO_MEDIA_CARD
                putExtra(FloatingScheduleService.EXTRA_QUICK_MEMO_ID, memoId)
                putExtra(FloatingScheduleService.EXTRA_MEDIA_IMAGE_PATH, imagePath)
                putExtra(FloatingScheduleService.EXTRA_MEDIA_TITLE, bodyText)
            })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start quick memo media card failed", e)
            false
        }
    }

    private fun voiceCaptureServiceIntent(floatingAction: String, liveAction: String): Intent {
        val settings = settingsQueryApi.settings.value
        return if (QuickMemoRecordingDisplayMode.normalize(settings.quickMemoRecordingDisplayMode) == QuickMemoRecordingDisplayMode.FLOATING_WINDOW) {
            Intent(appContext, FloatingScheduleService::class.java).apply {
                action = floatingAction
            }
        } else {
            Intent(appContext, QuickMemoVoiceCaptureService::class.java).apply {
                action = liveAction
            }
        }
    }
}
