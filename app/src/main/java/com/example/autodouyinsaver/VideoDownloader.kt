package com.example.autodouyinsaver

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log

object VideoDownloader {

    fun downloadVideo(context: Context, videoUrl: String, title: String = "DouyinVideo", coverUrl: String = "", durationMs: Long = 0L, originShareUrl: String = "") {
        try {
            val uri = Uri.parse(videoUrl)
            
            val isImage = videoUrl.contains("image", ignoreCase = true) || videoUrl.contains(".webp", ignoreCase = true) || videoUrl.contains(".jpeg", ignoreCase = true) || videoUrl.contains(".jpg", ignoreCase = true) || videoUrl.contains(".png", ignoreCase = true)
            val extension = if (isImage) ".jpeg" else ".mp4"
            val directory = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
            val desc = if (isImage) "高清图片正在下载" else "无水印视频正在下载"

            val request = DownloadManager.Request(uri).apply {
                setTitle("$title$extension")
                setDescription(desc)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(directory, "DouyinSaver/$title$extension")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            
            // 写入历史
            HistoryManager.addHistory(context, DownloadHistory(
                id = downloadId,
                title = title,
                originUrl = if (originShareUrl.isNotEmpty()) originShareUrl else videoUrl,
                timestamp = System.currentTimeMillis(),
                status = "下载中",
                coverUrl = coverUrl,
                durationMs = durationMs
            ))
            Log.d("VideoDownloader", "Download enqueued for $videoUrl with ID: $downloadId")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
