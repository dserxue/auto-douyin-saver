package com.example.autodouyinsaver

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log

object VideoDownloader {

    // 模拟抖音 Android 客户端的 UA，避免 CDN 限速/拒绝
    private const val DOUYIN_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val DOUYIN_REFERER = "https://www.douyin.com/"

    fun downloadVideo(
        context: Context,
        videoUrl: String,
        title: String = "DouyinVideo",
        coverUrl: String = "",
        durationMs: Long = 0L,
        originShareUrl: String = ""
    ) {
        // 对文件名做安全处理，去除系统不支持的字符
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)

        val isImage = videoUrl.contains("image", ignoreCase = true)
            || videoUrl.contains(".webp", ignoreCase = true)
            || videoUrl.contains(".jpeg", ignoreCase = true)
            || videoUrl.contains(".jpg", ignoreCase = true)
            || videoUrl.contains(".png", ignoreCase = true)
        val extension = if (isImage) ".jpeg" else ".mp4"
        val directory = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        val desc = if (isImage) "高清图片正在下载" else "无水印视频正在下载"

        try {
            val uri = Uri.parse(videoUrl)

            val request = DownloadManager.Request(uri).apply {
                setTitle("$safeTitle$extension")
                setDescription(desc)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(directory, "DouyinSaver/$safeTitle$extension")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                // ✅ 关键：加上 Referer 和 UA，避免抖音 CDN 鉴权失败/限速
                addRequestHeader("Referer", DOUYIN_REFERER)
                addRequestHeader("User-Agent", DOUYIN_UA)
                addRequestHeader("Accept", "*/*")
                addRequestHeader("Accept-Language", "zh-CN,zh;q=0.9")
                addRequestHeader("Connection", "keep-alive")
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            // 写入历史
            HistoryManager.addHistory(
                context, DownloadHistory(
                    id = downloadId,
                    title = safeTitle,
                    originUrl = if (originShareUrl.isNotEmpty()) originShareUrl else videoUrl,
                    timestamp = System.currentTimeMillis(),
                    status = "下载中",
                    coverUrl = coverUrl,
                    durationMs = durationMs
                )
            )
            Log.d("VideoDownloader", "Download enqueued: $videoUrl  ID=$downloadId")
        } catch (e: Exception) {
            Log.e("VideoDownloader", "下载入队失败: ${e.message}")
            e.printStackTrace()
        }
    }
}
