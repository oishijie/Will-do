package com.antgskds.calendarassistant.platform.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.shared.util.AppLogger as Log

/**
 * 电脑访问服务的前台保活载体。
 *
 * HTTP 服务跑在进程里，App 退到后台后若进程被系统冻结，浏览器就连不上了。
 * 这个前台服务只负责拉住进程优先级并挂一条常驻通知（对齐声物记
 * DiaryServerService 的角色），真正的 HTTP 由 [QuickMemoWebServer] 提供。
 */
class QuickMemoWebService : Service() {

    companion object {
        private const val TAG = "QuickMemoWebService"
        private const val CHANNEL_ID = "quick_memo_web_server"
        private const val NOTIFICATION_ID = 8801

        private const val ACTION_START = "com.antgskds.calendarassistant.action.START_WEB_SERVER"
        private const val ACTION_STOP = "com.antgskds.calendarassistant.action.STOP_WEB_SERVER"

        fun start(context: Context) {
            val intent = Intent(context, QuickMemoWebService::class.java).setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "前台服务拉起失败", it) }
        }

        fun stop(context: Context) {
            // 用 stopService 而不是 startService：发一个 STOP action 给 startService
            // 会让系统把这次启动也当「启动前台服务」计时，而服务直接 stopSelf()
            // 从未调 startForeground()，5 秒后必抛
            // ForegroundServiceDidNotStartInTimeException 把整个 App 带崩。
            runCatching {
                context.stopService(Intent(context, QuickMemoWebService::class.java))
            }.onFailure { Log.w(TAG, "前台服务停止失败", it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        // 先转前台：任何分支（含紧接着被停止）都必须先调这里，否则系统超时判死
        startForeground(NOTIFICATION_ID, buildNotification())

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 必须立刻返回：HTTP 绑定含端口探测与最多 ~2s 的等端口释放，
        // 在主线程同步执行会阻塞前台服务启动确认。放到后台线程。
        Thread({
            runCatching { QuickMemoWebController.get(this).ensureRunning() }
                .onFailure { Log.w(TAG, "启动电脑访问服务失败", it) }
        }, "quickmemo-web-start").start()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // 只停 HTTP，不改持久化开关：这里可能只是系统回收服务，不代表用户关掉了
        // 电脑访问。写 enabled=false 会让下次冷启动不再自动恢复。
        runCatching { QuickMemoWebController.get(this).stopServerOnly() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "电脑访问服务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "同一 Wi-Fi 下用电脑浏览器访问闪记时保持服务运行"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val port = QuickMemoWebServer.PORT
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("电脑访问服务运行中")
            .setContentText("同一 Wi-Fi 下用浏览器打开 http://手机IP:$port")
            .setSmallIcon(R.drawable.ic_qs_floating)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }
}
