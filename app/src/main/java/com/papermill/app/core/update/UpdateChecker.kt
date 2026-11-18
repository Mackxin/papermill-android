package com.papermill.app.core.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
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

    /**
     * 把新版 APK 下到 `cacheDir/updates/`，返回文件。失败抛异常，由调用方转成提示语。
     *
     * 两个坑：
     *  · Release 的资产地址会 302 到 objects.githubusercontent.com，跨域跳转
     *    HttpURLConnection 自己不一定跟，所以手动跟。
     *  · 放进 cacheDir 而不是外部存储 —— cacheDir 已经在 FileProvider 的声明范围内
     *    （`<cache-path path="."/>`），不用再改 xml；系统空间紧张时它也能被自动回收。
     */
    suspend fun download(
        ctx: Context,
        url: String,
        version: String,
        onProgress: (done: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        // 文件名带版本号：缓存里可能躺着上一版下载的包，混用会「点 v0.27 装成 v0.26」
        val out = File(dir, "papermill-${version.filter { it.isDigit() || it == '.' }}.apk")
        if (out.exists()) out.delete()

        val conn = openFollowingRedirects(url)
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) error("服务端返回 $code")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastTick = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        // 每 256KB 回报一次：太勤会让 Compose 每帧都重组，太懒进度条不动
                        if (done - lastTick >= 256 * 1024) {
                            lastTick = done
                            onProgress(done, total)
                        }
                    }
                    onProgress(done, total)
                }
            }
        } finally {
            conn.disconnect()
        }
        out
    }

    private fun openFollowingRedirects(url: String): HttpURLConnection {
        var target = url
        repeat(5) {
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "PaperMill-Android")
            conn.setRequestProperty("Accept", "application/octet-stream")
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) error("跳转没有 Location")
                target = URL(URL(target), loc).toString()
                return@repeat
            }
            return conn
        }
        error("重定向次数过多")
    }
}
