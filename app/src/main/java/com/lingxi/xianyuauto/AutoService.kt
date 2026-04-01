package com.lingxi.xianyuauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * 灵犀助手 - 闲鱼无障碍自动化服务
 * 
 * 核心功能：
 * 1. 监听闲鱼界面变化
 * 2. 通过HTTP接收Termux指令
 * 3. 执行点击/输入/滑动等操作
 * 4. 返回UI状态给Termux
 */
class AutoService : AccessibilityService() {

    companion object {
        const val TAG = "LingXiAuto"
        const val PORT = 8848
        var instance: AutoService? = null
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "无障碍服务已连接")
        startHttpServer()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听闲鱼界面变化（可选，用于自动响应）
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        serverJob?.cancel()
        serverSocket?.close()
        instance = null
        Log.i(TAG, "服务已销毁")
    }

    // ==================== HTTP服务器 ====================

    private fun startHttpServer() {
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, "HTTP服务器启动: localhost:$PORT")
                
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    launch {
                        handleRequest(client)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "服务器错误: ${e.message}")
            }
        }
    }

    private suspend fun handleRequest(client: java.net.Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val output = client.getOutputStream()

            // 读取请求行
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // 读取headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val colonIndex = line!!.indexOf(':')
                if (colonIndex > 0) {
                    headers[line!!.substring(0, colonIndex).trim().lowercase()] = 
                        line!!.substring(colonIndex + 1).trim()
                }
            }

            // 读取body（POST请求）
            var body = ""
            if (method == "POST" && headers.containsKey("content-length")) {
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength > 0) {
                    val buffer = CharArray(contentLength)
                    reader.read(buffer, 0, contentLength)
                    body = String(buffer)
                }
            }

            // 路由处理
            val response = when {
                path == "/status" -> handleStatus()
                path == "/tree" -> handleGetUITree()
                path.startsWith("/click") -> handleClick(path)
                path.startsWith("/longclick") -> handleLongClick(path)
                path == "/input" && method == "POST" -> handleInput(body)
                path == "/scroll" && method == "POST" -> handleScroll(body)
                path == "/back" -> handleBack()
                path == "/home" -> handleHome()
                path == "/screenshot" -> handleScreenshot()
                path == "/find" && method == "POST" -> handleFind(body)
                path == "/gesture" && method == "POST" -> handleGesture(body)
                else -> jsonResponse(404, mapOf("error" to "Not Found: $path"))
            }

            writeResponse(output, response)
        } catch (e: Exception) {
            Log.e(TAG, "处理请求错误: ${e.message}")
            try {
                writeResponse(client.getOutputStream(), 
                    jsonResponse(500, mapOf("error" to e.message)))
            } catch (_: Exception) {}
        } finally {
            client.close()
        }
    }

    private fun writeResponse(output: OutputStream, response: String) {
        val bytes = response.toByteArray()
        output.write("HTTP/1.1 200 OK\r\n".toByteArray())
        output.write("Content-Type: application/json\r\n".toByteArray())
        output.write("Content-Length: ${bytes.size}\r\n".toByteArray())
        output.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
        output.write("\r\n".toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun jsonResponse(code: Int = 200, data: Map<String, Any?>): String {
        val json = JSONObject(data)
        return json.toString()
    }

    // ==================== 命令处理器 ====================

    private fun handleStatus(): String {
        return jsonResponse(200, mapOf(
            "status" to "running",
            "service" to "connected",
            "root" to rootInActiveWindow?.packageName?.toString() ?: "unknown"
        ))
    }

    private fun handleGetUITree(): String {
        val root = rootInActiveWindow ?: return jsonResponse(500, mapOf("error" to "No root"))
        val tree = nodeToJson(root)
        return jsonResponse(200, mapOf("tree" to tree))
    }

    private fun nodeToJson(node: AccessibilityNodeInfo): JSONObject {
        val json = JSONObject()
        json.put("class", node.className?.toString() ?: "")
        json.put("text", node.text?.toString() ?: "")
        json.put("desc", node.contentDescription?.toString() ?: "")
        json.put("clickable", node.isClickable)
        json.put("editable", node.isEditable)
        json.put("scrollable", node.isScrollable)
        
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        json.put("bounds", mapOf(
            "left" to bounds.left, "top" to bounds.top,
            "right" to bounds.right, "bottom" to bounds.bottom
        ))

        val children = JSONArray()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            children.put(nodeToJson(child))
            child.recycle()
        }
        json.put("children", children)
        
        return json
    }

    /**
     * 点击指定文本或坐标
     * GET /click?text=北京  或  GET /click?x=100&y=200
     */
    private fun handleClick(path: String): String {
        val params = parseQueryParams(path)
        
        return if (params.containsKey("text")) {
            val text = params["text"]!!
            val root = rootInActiveWindow ?: return errorResponse("No root")
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNullOrEmpty()) {
                errorResponse("未找到文本: $text")
            } else {
                val node = nodes.first()
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                nodes.forEach { it.recycle() }
                jsonResponse(200, mapOf("success" to success, "text" to text))
            }
        } else if (params.containsKey("x") && params.containsKey("y")) {
            val x = params["x"]!!.toFloat()
            val y = params["y"]!!.toFloat()
            val success = clickAt(x, y)
            jsonResponse(200, mapOf("success" to success, "x" to x, "y" to y))
        } else {
            errorResponse("需要text或x,y参数")
        }
    }

    /**
     * 长按
     */
    private fun handleLongClick(path: String): String {
        val params = parseQueryParams(path)
        val x = params["x"]?.toFloat() ?: return errorResponse("需要x参数")
        val y = params["y"]?.toFloat() ?: return errorResponse("需要y参数")
        
        val success = longClickAt(x, y)
        return jsonResponse(200, mapOf("success" to success))
    }

    /**
     * 输入文字到焦点输入框
     * POST /input {"text": "你好"}
     */
    private fun handleInput(body: String): String {
        val json = JSONObject(body)
        val text = json.getString("text")
        
        val root = rootInActiveWindow ?: return errorResponse("No root")
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        
        if (focused != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
            return jsonResponse(200, mapOf("success" to success, "method" to "setText"))
        }
        
        // 备用方案：通过剪贴板粘贴
        return jsonResponse(500, mapOf("error" to "未找到焦点输入框"))
    }

    /**
     * 滑动
     * POST /scroll {"direction": "down", "distance": 500}
     */
    private fun handleScroll(body: String): String {
        val json = JSONObject(body)
        val direction = json.optString("direction", "down")
        val distance = json.optInt("distance", 500).toFloat()
        
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f
        
        val (startX, startY, endX, endY) = when (direction) {
            "down" -> arrayOf(centerX, centerY + distance/2, centerX, centerY - distance/2)
            "up" -> arrayOf(centerX, centerY - distance/2, centerX, centerY + distance/2)
            "left" -> arrayOf(centerX + distance/2, centerY, centerX - distance/2, centerY)
            "right" -> arrayOf(centerX - distance/2, centerY, centerX + distance/2, centerY)
            else -> return errorResponse("无效方向: $direction")
        }
        
        val success = swipe(startX, startY, endX, endY, 300)
        return jsonResponse(200, mapOf("success" to success, "direction" to direction))
    }

    private fun handleBack(): String {
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        return jsonResponse(200, mapOf("success" to success))
    }

    private fun handleHome(): String {
        val success = performGlobalAction(GLOBAL_ACTION_HOME)
        return jsonResponse(200, mapOf("success" to success))
    }

    private fun handleScreenshot(): String {
        // AccessibilityService不直接支持截图，需要MediaProjection
        return jsonResponse(501, mapOf("error" to "截图需使用ADB screencap"))
    }

    /**
     * 查找元素
     * POST /find {"text": "发布", "clickable": true}
     */
    private fun handleFind(body: String): String {
        val json = JSONObject(body)
        val root = rootInActiveWindow ?: return errorResponse("No root")
        
        val results = JSONArray()
        
        fun searchNode(node: AccessibilityNodeInfo) {
            val textMatch = if (json.has("text")) {
                val searchText = json.getString("text")
                (node.text?.toString()?.contains(searchText) == true ||
                 node.contentDescription?.toString()?.contains(searchText) == true)
            } else true
            
            val clickableMatch = if (json.has("clickable")) {
                node.isClickable == json.getBoolean("clickable")
            } else true
            
            if (textMatch && clickableMatch) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                results.put(JSONObject().apply {
                    put("text", node.text?.toString() ?: "")
                    put("desc", node.contentDescription?.toString() ?: "")
                    put("clickable", node.isClickable)
                    put("center_x", (bounds.left + bounds.right) / 2)
                    put("center_y", (bounds.top + bounds.bottom) / 2)
                })
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                searchNode(child)
                child.recycle()
            }
        }
        
        searchNode(root)
        return jsonResponse(200, mapOf("count" to results.length(), "results" to results))
    }

    /**
     * 手势操作
     * POST /gesture {"type": "click|swipe|long_click", "x1": 100, "y1": 200, ...}
     */
    private fun handleGesture(body: String): String {
        val json = JSONObject(body)
        val type = json.getString("type")
        
        val success = when (type) {
            "click" -> clickAt(json.getFloat("x1"), json.getFloat("y1"))
            "long_click" -> longClickAt(json.getFloat("x1"), json.getFloat("y1"))
            "swipe" -> swipe(
                json.getFloat("x1"), json.getFloat("y1"),
                json.getFloat("x2"), json.getFloat("y2"),
                json.optInt("duration", 300).toLong()
            )
            else -> false
        }
        
        return jsonResponse(200, mapOf("success" to success, "type" to type))
    }

    // ==================== 手势执行 ====================

    private fun clickAt(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return dispatchGestureSync(gesture)
    }

    private fun longClickAt(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
            .build()
        
        return dispatchGestureSync(gesture)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGestureSync(gesture)
    }

    private fun dispatchGestureSync(gesture: GestureDescription): Boolean {
        var result = false
        val latch = java.util.concurrent.CountDownLatch(1)
        
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                result = true
                latch.countDown()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                latch.countDown()
            }
        }, null)
        
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        return result
    }

    // ==================== 工具方法 ====================

    private fun parseQueryParams(path: String): Map<String, String> {
        val queryIndex = path.indexOf('?')
        if (queryIndex < 0) return emptyMap()
        
        return path.substring(queryIndex + 1)
            .split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
                else null
            }
            .toMap()
    }

    private fun errorResponse(message: String): String {
        return jsonResponse(500, mapOf("error" to message))
    }
}
