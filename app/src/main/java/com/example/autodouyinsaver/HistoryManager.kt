package com.example.autodouyinsaver

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DownloadHistory(
    val id: Long,
    val title: String,
    val originUrl: String,
    val timestamp: Long,
    val status: String,
    val coverUrl: String = "",
    val durationMs: Long = 0L
)

object HistoryManager {
    private const val PREFS_NAME = "DouyinSaverHistory"
    private const val KEY_HISTORY = "history_list"

    fun addHistory(context: Context, history: DownloadHistory) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_HISTORY, "[]")
        
        try {
            val jsonArray = JSONArray(historyStr)
            val newObj = JSONObject().apply {
                put("id", history.id)
                put("title", history.title)
                put("originUrl", history.originUrl)
                put("timestamp", history.timestamp)
                put("status", history.status)
                put("coverUrl", history.coverUrl)
                put("durationMs", history.durationMs)
            }
            jsonArray.put(newObj)
            
            // 写入本地
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
            syncToExternalFile(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getHistoryList(context: Context): List<DownloadHistory> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_HISTORY, "[]")
        val list = mutableListOf<DownloadHistory>()
        
        try {
            val jsonArray = JSONArray(historyStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DownloadHistory(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        title = obj.optString("title", "未命名"),
                        originUrl = obj.optString("originUrl", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        status = obj.optString("status", "下载中"),
                        coverUrl = obj.optString("coverUrl", ""),
                        durationMs = obj.optLong("durationMs", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 倒序返回，最新的在前面
        return list.sortedByDescending { it.timestamp }
    }

    fun updateStatus(context: Context, id: Long, newStatus: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_HISTORY, "[]")
        try {
            val jsonArray = JSONArray(historyStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optLong("id", -1L) == id) {
                    obj.put("status", newStatus)
                    break
                }
            }
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
            syncToExternalFile(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeHistories(context: Context, ids: List<Long>, deleteFiles: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_HISTORY, "[]")
        
        try {
            val jsonArray = JSONArray(historyStr)
            val newArray = JSONArray()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val currentId = obj.optLong("id", -1L)
                if (!ids.contains(currentId)) {
                    newArray.put(obj)
                }
            }
            
            prefs.edit().putString(KEY_HISTORY, newArray.toString()).apply()
            syncToExternalFile(newArray.toString())
            
            if (deleteFiles) {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                for (id in ids) {
                    downloadManager.remove(id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun renameHistory(context: Context, id: Long, newTitle: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_HISTORY, "[]")
        try {
            val jsonArray = JSONArray(historyStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optLong("id", -1L) == id) {
                    obj.put("title", newTitle)
                    break
                }
            }
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
            syncToExternalFile(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
        syncToExternalFile("[]")
    }

    // --- 物理备份机制 ---
    private fun getExternalBackupFile(): java.io.File {
        val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        if (!publicDir.exists()) publicDir.mkdirs()
        val backupDir = java.io.File(publicDir, "DouyinSaver")
        if (!backupDir.exists()) backupDir.mkdirs()
        return java.io.File(backupDir, "DouyinSaver_History.json")
    }

    private fun syncToExternalFile(jsonStr: String) {
        try {
            val file = getExternalBackupFile()
            file.writeText(jsonStr, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace() // 如果没有读写权限则静默失败，不阻断主流程
        }
    }

    fun autoRecoverIfEmpty(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_HISTORY, "[]")
        if (historyStr == "[]") {
            try {
                val file = getExternalBackupFile()
                if (file.exists()) {
                    val backupStr = file.readText(Charsets.UTF_8)
                    if (backupStr.trim().startsWith("[")) {
                        prefs.edit().putString(KEY_HISTORY, backupStr).apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
