package com.antgskds.calendarassistant.platform.web

import android.content.Context
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTodoState
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

/**
 * 电脑访问服务：同一 Wi-Fi 下的浏览器打开 http://手机IP:9527 即可查看、编辑、
 * 删除闪记，手机端新增/修改会通过 SSE 推送到浏览器实时刷新。
 *
 * 结构对齐声物记的 diary_web_server：固定端口、自家旧实例身份探测与关停、
 * 每秒一次聚合签名比对变化、SSE 心跳保活。刻意保持最小实现：只做文本闪记的
 * 查看/编辑/删除，不含录音流与图片。
 *
 * 手写 HTTP/1.1 而非引入框架：只需要 5 个端点，且要精确控制 SSE 的 flush
 * 时机（框架的响应缓冲会把事件攒住，浏览器就不是实时的了）。
 */
class QuickMemoWebServer(private val context: Context) {

    companion object {
        const val PORT = 9527

        private const val TAG = "QuickMemoWebServer"

        /** /__identity 响应里携带的身份标记，用于判断端口是不是被自家旧实例占着。 */
        private const val IDENTITY = "willdo-quickmemo-web"

        @Volatile
        private var instance: QuickMemoWebServer? = null

        fun get(context: Context): QuickMemoWebServer =
            instance ?: synchronized(this) {
                instance ?: QuickMemoWebServer(context.applicationContext).also { instance = it }
            }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    @Volatile
    private var pollThread: Thread? = null

    @Volatile
    private var lastSignature: String = ""

    /** SSE 客户端连接。浏览器 EventSource 连上来后挂在这里，变化时逐个写事件。 */
    private val sseClients = CopyOnWriteArrayList<SseClient>()

    val isRunning: Boolean get() = serverSocket != null && !(serverSocket?.isClosed ?: true)

    private val dao get() = EventsDatabase.getInstance(context).quickMemoDao()

    /**
     * 启动服务。幂等：已在跑先停再启。
     *
     * 端口被占时先探测是不是自家旧实例（引擎重建后残留的 HttpServer），
     * 是则请求它自杀（只接受本机回环），释放端口后再绑定。
     */
    fun start(): StartResult {
        if (isRunning) stop()

        val wasOurs = shutdownOwnStaleInstance()
        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("0.0.0.0", PORT))
            }
        } catch (e: IOException) {
            Log.w(TAG, "绑定端口 $PORT 失败（此前探测到自家服务=$wasOurs）", e)
            return if (wasOurs) StartResult.OwnInstanceStuck else StartResult.PortBusyByOther
        }

        serverSocket = socket
        lastSignature = runCatching { changeSignature() }.getOrDefault("")
        Log.i(TAG, "电脑访问服务已启动: http://0.0.0.0:$PORT")

        acceptThread = thread(name = "quickmemo-web-accept", isDaemon = true) {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    if (!socket.isClosed) Log.w(TAG, "accept 失败", e)
                    break
                }
                thread(name = "quickmemo-web-client", isDaemon = true) {
                    handleClient(client)
                }
            }
        }

        pollThread = thread(name = "quickmemo-web-poll", isDaemon = true) {
            while (!socket.isClosed) {
                try {
                    Thread.sleep(1000)
                    val signature = changeSignature()
                    if (signature != lastSignature) {
                        lastSignature = signature
                        broadcast("data: changed\n\n")
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "变化轮询失败（下轮重试）", e)
                }
            }
        }

        return StartResult.Started
    }

    /** 停止服务（幂等）：关监听、断所有连接（含 SSE）、停轮询。 */
    fun stop() {
        pollThread?.interrupt()
        pollThread = null
        acceptThread?.interrupt()
        acceptThread = null
        sseClients.forEach { runCatching { it.socket.close() } }
        sseClients.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        Log.i(TAG, "电脑访问服务已停止")
    }

    // ==================== 请求处理 ====================

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 30_000
                val request = Request.parse(BufferedInputStream(s.getInputStream())) ?: return
                route(request, s.getOutputStream(), s)
            } catch (e: SocketException) {
                // 浏览器主动断开：正常现象，不记日志
            } catch (e: Exception) {
                Log.w(TAG, "处理请求异常", e)
                runCatching { writeJson(s.getOutputStream(), 500, """{"error":"internal error"}""") }
            }
        }
    }

    private fun route(request: Request, out: OutputStream, socket: Socket) {
        val path = request.path
        Log.d(TAG, "→ ${request.method} $path")

        when {
            request.method == "GET" && path == "/__identity" -> {
                writeJson(out, 200, """{"service":"$IDENTITY"}""")
            }

            request.method == "POST" && path == "/__shutdown" -> {
                // 只接受回环请求：局域网里任何设备发个 POST 就能关服务的洞要堵上
                if (!socket.inetAddress.isLoopbackAddress) {
                    writeJson(out, 403, """{"error":"loopback only"}""")
                    return
                }
                writeJson(out, 200, """{"ok":true,"bye":true}""")
                thread(isDaemon = true) {
                    Thread.sleep(50)
                    stop()
                }
            }

            request.method == "GET" && path == "/" -> {
                writeResponse(out, 200, "text/html; charset=utf-8", QuickMemoWebPage.HTML.toByteArray())
            }

            request.method == "GET" && path == "/api/notes" -> {
                val notes = runBlocking { dao.getAllQuickMemos() }
                writeJson(out, 200, """{"notes":${notes.joinToString(",", "[", "]") { it.toJson() }}}""")
            }

            request.method == "GET" && path == "/api/events" -> openSse(out, socket)

            path.startsWith("/api/notes/") -> {
                val id = path.removePrefix("/api/notes/").toLongOrNull()
                when {
                    id == null -> writeJson(out, 400, """{"error":"bad id"}""")
                    request.method == "PUT" -> handleUpdate(out, id, request)
                    request.method == "DELETE" -> handleDelete(out, id)
                    else -> writeJson(out, 404, """{"error":"not found"}""")
                }
            }

            else -> writeJson(out, 404, """{"error":"not found"}""")
        }
    }

    private fun handleUpdate(out: OutputStream, id: Long, request: Request) {
        val memo = runBlocking { dao.getQuickMemo(id) }
        if (memo == null || memo.deletedAt != null) {
            writeJson(out, 404, """{"error":"not found"}""")
            return
        }
        val content = extractJsonString(request.body, "content")
        if (content == null) {
            writeJson(out, 400, """{"error":"content (string) required"}""")
            return
        }
        runBlocking {
            dao.updateBody(id, content, System.currentTimeMillis())
        }
        Log.i(TAG, "电脑端编辑闪记 id=$id（${content.length} 字）")
        afterMutation()
        writeJson(out, 200, """{"ok":true}""")
    }

    private fun handleDelete(out: OutputStream, id: Long) {
        val memo = runBlocking { dao.getQuickMemo(id) }
        if (memo == null || memo.deletedAt != null) {
            writeJson(out, 404, """{"error":"not found"}""")
            return
        }
        // 与 App 内划走语义一致：归档（保留内容，可在手机「已归档」恢复）
        runBlocking { dao.markQuickMemoArchived(id, System.currentTimeMillis()) }
        Log.i(TAG, "电脑端删除闪记 id=$id")
        afterMutation()
        writeJson(out, 200, """{"ok":true}""")
    }

    /** 电脑端改动后的收尾：刷新本地签名（避免下轮轮询重复推）+ 广播刷新。 */
    private fun afterMutation() {
        runCatching { lastSignature = changeSignature() }
        broadcast("data: changed\n\n")
    }

    // ==================== SSE ====================

    private fun openSse(out: OutputStream, socket: Socket) {
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream; charset=utf-8\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: keep-alive\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }
        out.write(headers.toByteArray())
        out.write(": connected\n\n".toByteArray())
        out.flush()

        val client = SseClient(socket, out)
        sseClients.add(client)
        Log.d(TAG, "浏览器已连接 SSE（当前 ${sseClients.size} 个）")

        // 心跳：15s 一次注释行，防止中间设备掐掉空闲连接；写失败即视为断开
        try {
            while (!socket.isClosed) {
                Thread.sleep(15_000)
                out.write(": ping\n\n".toByteArray())
                out.flush()
            }
        } catch (e: Exception) {
            // 断开或超时：正常退出
        } finally {
            sseClients.remove(client)
            runCatching { socket.close() }
        }
    }

    private fun broadcast(payload: String) {
        val bytes = payload.toByteArray()
        sseClients.forEach { client ->
            runCatching {
                client.out.write(bytes)
                client.out.flush()
            }.onFailure { sseClients.remove(client) }
        }
    }

    private class SseClient(val socket: Socket, val out: OutputStream)

    // ==================== 变化检测 ====================

    /**
     * 廉价变更签名：一次聚合查询。手机端（主 App 或悬浮窗）任何增删改都会让它变化。
     * SUM 里带上 id 的权重，内容/条数不变但记录被替换时也能体现出来。
     */
    private fun changeSignature(): String {
        val memos = runBlocking { dao.getAllQuickMemos() }
        val count = memos.size
        val maxId = memos.maxOfOrNull { it.id ?: 0L } ?: 0L
        val totalLength = memos.sumOf { it.bodyText.length }
        val updatedSum = memos.sumOf { it.updatedAt }
        return "c=$count,m=$maxId,s=$totalLength,u=$updatedSum"
    }

    // ==================== 端口身份探测 ====================

    /** 探测并关停自家旧实例。返回是否探测到自家服务。 */
    private fun shutdownOwnStaleInstance(): Boolean {
        if (!probeIdentity()) return false
        Log.i(TAG, "端口 $PORT 被自家旧实例占用，发送关停指令")
        runCatching {
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", PORT), 800)
            socket.getOutputStream().apply {
                write(
                    ("POST /__shutdown HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:$PORT\r\n" +
                        "Content-Length: 0\r\n\r\n").toByteArray()
                )
                flush()
            }
            socket.use { it.getInputStream().readBytes() }
        }.onFailure { Log.w(TAG, "关停指令发送失败（继续尝试绑定）", it) }

        // 等旧实例真正让出端口
        repeat(12) {
            if (!probeIdentity()) return true
            Thread.sleep(150)
        }
        return true
    }

    private fun probeIdentity(): Boolean = runCatching {
        val socket = Socket()
        socket.connect(InetSocketAddress("127.0.0.1", PORT), 800)
        socket.soTimeout = 800
        socket.use { s ->
            s.getOutputStream().apply {
                write("GET /__identity HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                flush()
            }
            s.getInputStream().readBytes().toString(Charsets.UTF_8).contains(IDENTITY)
        }
    }.getOrDefault(false)

    // ==================== 响应输出 ====================

    private fun writeJson(out: OutputStream, status: Int, body: String) {
        writeResponse(out, status, "application/json; charset=utf-8", body.toByteArray())
    }

    private fun writeResponse(out: OutputStream, status: Int, contentType: String, body: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 $status ${statusText(status)}\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        else -> "OK"
    }

    /** 极简 JSON 字段提取：只用于取 body 里的单个字符串字段，不做完整解析。 */
    private fun extractJsonString(body: String, key: String): String? {
        val marker = "\"$key\""
        val keyIndex = body.indexOf(marker)
        if (keyIndex < 0) return null
        var i = body.indexOf(':', keyIndex + marker.length)
        if (i < 0) return null
        i++
        while (i < body.length && body[i].isWhitespace()) i++
        if (i >= body.length || body[i] != '"') return null
        i++
        val sb = StringBuilder()
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    when (val esc = body[i + 1]) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            if (i + 5 < body.length) {
                                val hex = body.substring(i + 2, i + 6)
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                                i += 4
                            }
                        }
                        else -> sb.append(esc)
                    }
                    i += 2
                }
                c == '"' -> return sb.toString()
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return null
    }

    /** 闪记转 JSON（不暴露文件系统绝对路径）。 */
    private fun QuickMemoEntity.toJson(): String {
        val isTodo = todoState == QuickMemoTodoState.ACTIVE || todoState == QuickMemoTodoState.COMPLETED
        return buildString {
            append("{")
            append("\"id\":$id,")
            append("\"content\":${bodyText.jsonEscape()},")
            append("\"title\":${title.jsonEscape()},")
            append("\"type\":${type.jsonEscape()},")
            append("\"createdAt\":$createdAt,")
            append("\"updatedAt\":$updatedAt,")
            append("\"isTodo\":$isTodo,")
            append("\"isTodoCompleted\":${todoState == QuickMemoTodoState.COMPLETED},")
            append("\"hasAudio\":${!audioPath.isNullOrEmpty()},")
            append("\"hasImage\":${!imagePath.isNullOrEmpty()},")
            append("\"isVoice\":${type == QuickMemoType.VOICE}")
            append("}")
        }
    }

    private fun String.jsonEscape(): String = buildString {
        append('"')
        this@jsonEscape.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    // ==================== 请求解析 ====================

    private class Request(
        val method: String,
        val path: String,
        val body: String,
    ) {
        companion object {
            /** 解析请求行、头与 body。返回 null = 连接没数据（浏览器预连接后闲置）。 */
            fun parse(input: BufferedInputStream): Request? {
                val headerBytes = ByteArrayOutputStream()
                var last4 = 0
                while (true) {
                    val b = input.read()
                    if (b == -1) return null
                    headerBytes.write(b)
                    last4 = (last4 shl 8) or b
                    if (last4 == 0x0D0A0D0A) break
                    if (headerBytes.size() > 64 * 1024) return null
                }
                val headerText = headerBytes.toString("UTF-8")
                val lines = headerText.split("\r\n")
                val requestLine = lines.firstOrNull()?.split(" ") ?: return null
                if (requestLine.size < 2) return null
                val method = requestLine[0]
                val rawPath = requestLine[1]
                val path = rawPath.substringBefore('?')
                val contentLength = lines
                    .firstOrNull { it.lowercase().startsWith("content-length:") }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                val body = if (contentLength > 0) {
                    val buf = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(buf, read, contentLength - read)
                        if (n <= 0) break
                        read += n
                    }
                    String(buf, 0, read, Charsets.UTF_8)
                } else ""
                return Request(method, path, body)
            }
        }
    }

    enum class StartResult {
        Started,

        /** 端口被其他应用占用。 */
        PortBusyByOther,

        /** 自家旧实例占端口且关停失败。 */
        OwnInstanceStuck,
    }
}
