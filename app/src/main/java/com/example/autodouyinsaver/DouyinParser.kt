package com.example.autodouyinsaver

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class DouyinParseResult(
    val title: String,
    val coverUrl: String,
    val durationMs: Long,
    val urls: List<String>
)

object DouyinParser {

    private const val TAG = "DouyinParser"

    suspend fun parseUrl(shareText: String): DouyinParseResult {
        return withContext(Dispatchers.IO) {
            val fallbackResult = DouyinParseResult("Douyin_${System.currentTimeMillis()}", "", 0L, emptyList())
            try {
                // 1. 提取短链接
                val shortUrl = extractUrl(shareText) ?: return@withContext fallbackResult
                Log.d(TAG, "Extracted short URL: $shortUrl")

                // 2. 将提取到的分享短链拼接到用户提供的免费解析 API 中
                val encodedUrl = URLEncoder.encode(shortUrl, "UTF-8")
                val apiUrl = "https://api.bugpk.com/api/douyin?url=$encodedUrl"
                
                val obj = URL(apiUrl)
                val con = obj.openConnection() as HttpURLConnection
                con.requestMethod = "GET"
                con.setRequestProperty("User-Agent", "Mozilla/5.0")
                con.connect()

                if (con.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(con.inputStream))
                    var inputLine: String?
                    val response = StringBuffer()

                    while (reader.readLine().also { inputLine = it } != null) {
                        response.append(inputLine)
                    }
                    reader.close()
                    con.disconnect()

                    val jsonString = response.toString()
                    Log.d(TAG, "API Response: $jsonString")
                    
                    return@withContext extractVideoUrlsFromJson(jsonString)
                } else {
                    Log.e(TAG, "API Error Code: ${con.responseCode}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            fallbackResult
        }
    }

    private fun extractVideoUrlsFromJson(jsonString: String): DouyinParseResult {
        val resultUrls = mutableListOf<String>()
        var title = "Douyin_${System.currentTimeMillis()}"
        var coverUrl = ""
        var durationMs = 0L

        try {
            val jsonObject = JSONObject(jsonString)
            
            // 尝试按标准解析格式提取
            if (jsonObject.has("data")) {
                val data = jsonObject.getJSONObject("data")
                
                if (data.has("title") && !data.isNull("title")) {
                    val parsedTitle = data.getString("title").trim()
                    if (parsedTitle.isNotEmpty()) {
                        // 移除文件系统中不允许的特殊字符
                        title = parsedTitle.replace(Regex("[\\\\/:*?\"<>|]"), "")
                    }
                }
                
                coverUrl = data.optString("cover", "")
                // 如果API duration为秒，这里可以乘以1000；如果是毫秒，直接取。通常部分API直接给毫秒
                durationMs = data.optLong("duration", 0L)
                
                // 1. 优先处理图文/实况(live)格式，只提取动态视频视频流
                if (data.has("live_photo") && !data.isNull("live_photo")) {
                    val livePhotoArray = data.optJSONArray("live_photo")
                    if (livePhotoArray != null && livePhotoArray.length() > 0) {
                        for (i in 0 until livePhotoArray.length()) {
                            val item = livePhotoArray.optJSONObject(i)
                            // 只提取实况照片的动态视频流，不需要图片
                            val videoUrl = item?.optString("video")
                            if (!videoUrl.isNullOrEmpty() && videoUrl.startsWith("http")) {
                                resultUrls.add(videoUrl)
                            }
                        }
                    }
                }

                if (resultUrls.isNotEmpty()) {
                    return DouyinParseResult(title, coverUrl, durationMs, resultUrls)
                }

                // 3. 常规无水印视频提取
                for (key in arrayOf("play", "url", "video_url", "video", "play_addr")) {
                    if (data.has(key) && !data.isNull(key)) {
                        val value = data.get(key)
                        // 有可能内部依然是 JSONObject 或者直接是 String
                        if (value is String && value.startsWith("http")) {
                            resultUrls.add(value)
                            return DouyinParseResult(title, coverUrl, durationMs, resultUrls)
                        }
                    }
                }
            }
            
            // 如果上述提取失败，进行强力正则提取，找出 JSON 中所有看起来像 http 的 mp4/视频直链
            val pattern = Pattern.compile("\"([^\"]*?https?://[^\"]+)\"")
            val matcher = pattern.matcher(jsonString)
            while (matcher.find()) {
                val match = matcher.group(1)
                // 做一些简单的防误判过滤
                if (match?.contains("avatar") == false && match.contains("video")) {
                    resultUrls.add(match.replace("\\/", "/"))
                    break // 落底策略只取第一个，防止抓太多乱七八糟的视频
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return DouyinParseResult(title, coverUrl, durationMs, resultUrls)
    }

    private fun extractUrl(text: String): String? {
        val matcher = Pattern.compile("https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]").matcher(text)
        if (matcher.find()) {
            return matcher.group()
        }
        return null
    }
}
