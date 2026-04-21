package com.example.autodouyinsaver

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoDownloadAccessibilityService : AccessibilityService() {

    private val TAG = "AutoDownloadService"
    private var isAutomating = false

    companion object {
        // 持有当前运行的服务实例，FloatingWindowService 可以直接调用
        @Volatile
        var instance: AutoDownloadAccessibilityService? = null
            private set

        fun triggerAutomation() {
            val svc = instance
            if (svc != null) {
                svc.startAutomation()
            } else {
                Log.w("AutoDownloadService", "无障碍服务未运行，无法触发自动化")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 通过 companion object.triggerAutomation() 触发，不依赖事件
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        Log.d(TAG, "Accessibility Service Destroyed")
    }

    private fun startAutomation() {
        if (isAutomating) return
        isAutomating = true

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Step 1: 点击分享按钮（UI 操作在主线程）
                val sharedClicked = findAndClickNodeByDesc("分享") || findAndClickNodeByText("分享")
                if (!sharedClicked) {
                    Toast.makeText(this@AutoDownloadAccessibilityService, "未找到分享按钮", Toast.LENGTH_SHORT).show()
                    isAutomating = false
                    return@launch
                }

                // Step 2: 等待底部分享面板弹出
                delay(1200)

                // Step 3: 点击"复制链接"
                val copyClicked = findAndClickNodeByText("复制链接") || findAndClickNodeByText("分享链接")
                if (!copyClicked) {
                    Toast.makeText(this@AutoDownloadAccessibilityService, "未找到复制链接按钮", Toast.LENGTH_SHORT).show()
                    isAutomating = false
                    return@launch
                }

                Toast.makeText(this@AutoDownloadAccessibilityService, "已复制链接，正在暗中解析...", Toast.LENGTH_SHORT).show()

                // Step 4: 等待系统动画与剪贴板写入完成
                delay(1500)

                // Step 5: Android 14+ / Android 16 后台读取剪贴板受限，使用透明 Activity 代理读取
                try {
                    val intent = android.content.Intent(this@AutoDownloadAccessibilityService, ClipboardReaderActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "启动 ClipboardReaderActivity 失败: ${e.message}")
                    isAutomating = false
                }

            } catch (e: Exception) {
                Log.e(TAG, "自动化流程异常: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@AutoDownloadAccessibilityService, "解析出错: ${e.message}", Toast.LENGTH_LONG).show()
                isAutomating = false
            }
        }
    }

    fun processShareText(shareText: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "剪贴板内容: $shareText")

                if (shareText.isEmpty()) {
                    Toast.makeText(this@AutoDownloadAccessibilityService, "剪贴板为空，请重试", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Step 6: 切换到 IO 线程发起网络解析（耗时操作不阻塞主线程）
                val parseResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    DouyinParser.parseUrl(shareText)
                }

                // Step 7: 回主线程下载派发
                val title = parseResult.title
                val playUrls = parseResult.urls
                val coverUrl = parseResult.coverUrl
                val durationMs = parseResult.durationMs

                if (playUrls.isNotEmpty()) {
                    Toast.makeText(this@AutoDownloadAccessibilityService, "解析成功！静默下载 ${playUrls.size} 个视频...", Toast.LENGTH_SHORT).show()
                    playUrls.forEachIndexed { index, url ->
                        val suffix = if (playUrls.size > 1) "_${index + 1}" else ""
                        VideoDownloader.downloadVideo(
                            this@AutoDownloadAccessibilityService,
                            url,
                            "$title$suffix",
                            coverUrl,
                            durationMs
                        )
                    }
                } else {
                    Toast.makeText(this@AutoDownloadAccessibilityService, "解析失败，该视频可能已被限制", Toast.LENGTH_LONG).show()
                    HistoryManager.addHistory(
                        this@AutoDownloadAccessibilityService,
                        DownloadHistory(
                            id = -1L,
                            title = title,
                            originUrl = shareText,
                            timestamp = System.currentTimeMillis(),
                            status = "解析失败",
                            coverUrl = coverUrl,
                            durationMs = durationMs
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理剪贴板数据异常: ${e.message}")
                Toast.makeText(this@AutoDownloadAccessibilityService, "解析出错: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isAutomating = false
            }
        }
    }

    private fun findAndClickNodeByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        
        val visibleNodes = nodes.filter { it.isVisibleToUser }
        for (node in visibleNodes.reversed()) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                parent = parent.parent
            }
        }
        return false
    }

    private fun findAndClickNodeByDesc(desc: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByContentDescription(rootNode, desc, list)
        
        for (node in list.reversed()) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                parent = parent.parent
            }
        }
        return false
    }

    private fun findNodesByContentDescription(
        node: AccessibilityNodeInfo?, 
        desc: String, 
        resultList: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        if (node.isVisibleToUser && node.contentDescription?.toString()?.contains(desc) == true) {
            resultList.add(node)
        }
        for (i in 0 until node.childCount) {
            findNodesByContentDescription(node.getChild(i), desc, resultList)
        }
    }

    private suspend fun showToast(msg: String) {
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            Toast.makeText(this@AutoDownloadAccessibilityService, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
