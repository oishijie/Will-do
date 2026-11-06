package com.antgskds.calendarassistant.platform.web

import android.content.Context
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 电脑访问服务的开关编排器：设置页开关与冷启动自动恢复的唯一入口。
 *
 * 生命周期对齐声物记 DiaryServerController：
 * 1. 开启 = 先拉前台服务保活（进程优先级先上去）→ 再绑 HTTP；HTTP 失败则回滚前台服务
 * 2. 关闭 = 停 HTTP → 停前台服务 → 清持久化开关
 * 3. 开关落 SharedPreferences，冷启动 [autoStartIfEnabled] 静默恢复，
 *    电脑端收藏的地址始终有效
 */
class QuickMemoWebController private constructor(private val context: Context) {

    companion object {
        private const val TAG = "QuickMemoWebController"
        private const val PREFS = "quick_memo_web_server"
        private const val KEY_ENABLED = "enabled"

        @Volatile
        private var instance: QuickMemoWebController? = null

        fun get(context: Context): QuickMemoWebController =
            instance ?: synchronized(this) {
                instance ?: QuickMemoWebController(context.applicationContext).also { instance = it }
            }
    }

    private val _state = MutableStateFlow(WebServerState())
    val state: StateFlow<WebServerState> = _state.asStateFlow()

    private val server = QuickMemoWebServer.get(context)

    /** start() 被调用后置位，stop() 清零：启动线程完成时据此判断用户是否已中途关掉。 */
    @Volatile
    private var startRequested = false

    /** 服务当前是否已拉起（避免重复 startService / stopService 刷屏）。 */
    @Volatile
    private var serviceRequested = false

    /** 前台服务已拉起后由此确认 HTTP 真的跑起来了（服务侧后台线程调用）。 */
    @Synchronized
    fun ensureRunning() {
        if (server.isRunning) {
            _state.value = WebServerState(running = true, addresses = lanAddresses())
            return
        }
        _state.value = WebServerState(starting = true, addresses = lanAddresses())
        when (val result = server.start()) {
            QuickMemoWebServer.StartResult.Started -> {
                // 启动期间用户已关掉开关：别再写回 enabled=true，否则下次冷启动
                // 会“自作主张”重新拉起一个用户已关掉的服务
                if (!startRequested) {
                    server.stop()
                    _state.value = WebServerState(running = false, addresses = lanAddresses())
                    return
                }
                saveEnabled(true)
                _state.value = WebServerState(running = true, addresses = lanAddresses())
                Log.i(TAG, "电脑访问服务已开启")
            }
            else -> {
                _state.value = WebServerState(error = result.message, addresses = lanAddresses())
                Log.w(TAG, "电脑访问服务启动失败: ${result.message}")
                QuickMemoWebService.stop(context)
            }
        }
    }

    /** 只读同步真实状态（设置页进页调用），不触发启动。 */
    fun refreshState() {
        val running = server.isRunning
        if (running != _state.value.running) {
            _state.value = WebServerState(running = running, addresses = lanAddresses())
        }
    }

    /** 设置页开关：开。 */
    fun start() {
        if (_state.value.running || _state.value.starting) return
        if (serviceRequested) return
        serviceRequested = true
        startRequested = true
        _state.value = WebServerState(starting = true, addresses = lanAddresses())
        // 先拉前台服务，由它在 onStartCommand 里调 ensureRunning 绑 HTTP
        QuickMemoWebService.start(context)
    }

    /** 设置页开关：关。
     *
     * 不加 @Synchronized：启动线程可能正持着锁做端口探测，主线程等锁会卡
     * 界面（甚至 ANR）。server.stop() 自身幂等，不需要外层串行化。
     */
    fun stop() {
        startRequested = false
        serviceRequested = false
        server.stop()
        saveEnabled(false)
        QuickMemoWebService.stop(context)
        _state.value = WebServerState(running = false, addresses = lanAddresses())
        Log.i(TAG, "电脑访问服务已关闭")
    }

    /** 只停 HTTP，不动持久化开关与前台服务（服务被系统回收时走这里）。 */
    fun stopServerOnly() {
        server.stop()
        if (_state.value.running) {
            _state.value = WebServerState(running = false, addresses = lanAddresses())
        }
    }

    /** 冷启动恢复：上次开着就静默拉起。 */
    fun autoStartIfEnabled() {
        if (isEnabled() && !server.isRunning) {
            Log.i(TAG, "上次开启过电脑访问服务，自动恢复")
            start()
        }
    }

    fun isEnabled(): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun lanAddresses(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        addr.hostAddress?.let { result.add(it) }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "获取局域网地址失败", it) }
        // 私有网段优先：192.168 > 10 > 172 > 其他
        fun rank(ip: String): Int = when {
            ip.startsWith("192.168.") -> 0
            ip.startsWith("10.") -> 1
            ip.startsWith("172.") -> 2
            else -> 3
        }
        return result.distinct().sortedBy { rank(it) }
    }

    private fun saveEnabled(enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }.onFailure { Log.w(TAG, "开关持久化失败（不影响本次运行）", it) }
    }

    private val QuickMemoWebServer.StartResult.message: String
        get() = when (this) {
            QuickMemoWebServer.StartResult.Started -> "服务已启动"
            QuickMemoWebServer.StartResult.PortBusyByOther ->
                "端口 ${QuickMemoWebServer.PORT} 被其他应用占用，无法启动电脑访问服务"
            QuickMemoWebServer.StartResult.OwnInstanceStuck ->
                "端口 ${QuickMemoWebServer.PORT} 被旧服务实例占用且暂时无法释放，请稍后重试"
        }
}

/** 服务状态（设置页卡片订阅）。 */
data class WebServerState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val error: String? = null,
    val addresses: List<String> = emptyList(),
)
