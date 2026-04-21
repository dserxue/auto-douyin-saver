package com.example.autodouyinsaver

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log

object VideoDownloader {

    fun downloadVideo(context: Context, videoUrl: String, title: String = "DouyinVideo", coverUrl: String = "", durationMs: Long = 0L) {
        try {
            val uri = Uri.parse(videoUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle("$title.mp4")
                setDescription("抖音无水印视频正在下载")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "DouyinSaver/$title.mp4")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            
            // 写入历史
            HistoryManager.addHistory(context, DownloadHistory(
                id = downloadId,
                title = title,
                originUrl = videoUrl,
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
