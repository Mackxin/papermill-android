package com.papermill.app.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * App 内检查更新：读公开 GitHub 仓库的最新 Release。
 *
 * 不引任何网络库（HttpURLConnection 足够），不加后台轮询 —— 只在用户点「检查更新」时请求一次。
 * 如果网络不通（国内访问 GitHub API 可能失败），直接返回 null，UI 给「检查失败」提示，
 * 绝不影响离线出卷打印。
 */
object UpdateChecker {

    const val REPO = "Mackxin/papermill-android"
    const val REPO_URL = "https://github.com/$REPO"

    data class Release(
        val version: String,
        val name: String,
        val notes: String,
        val apkUrl: String?,
        val pageUrl: String,
    )

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "PaperMill-Android")
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val o = JSONObject(body)

                var apkUrl: String? = null
                val assets = o.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url")
                            break
                        }
                    }
                }

                Release(
                    version = o.optString("tag_name").removePrefix("v"),
                    name = o.optString("name"),
                    notes = o.optString("body").take(400),
                    apkUrl = apkUrl,
                    pageUrl = o.optString("html_url", REPO_URL + "/releases"),
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** 点分数字版本比较：1.2.0 > 1.1.9；非数字段按 0 处理 */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(s: String) = s.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
