package com.papermill.app.core.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * PDF 落地与分享。
 *
 * 分享这一条链路是「远程打印」的唯一可行路径：
 * 导出 PDF → 分享到微信 → 在微信里用「奔图云打印」小程序上传打印。
 * 奔图云打印没有公开 API，App 无法直连 —— 所以这里只做"把文件交出去"，
 * 不在 UI 上承诺任何"一键云打印"。
 */
object PdfExporter {

    fun papersDir(context: Context): File {
        // 外部私有目录优先（用户能用文件管理器找到）；不可用时退内部存储
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "papers").apply { mkdirs() }
    }

    fun write(context: Context, fileName: String, bytes: ByteArray): File {
        val file = File(papersDir(context), fileName)
        file.writeBytes(bytes)
        return file
    }
}

object PdfSharer {

    fun share(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "发送试卷 PDF")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * 多张图片一起分享（每页一张 PNG）。
     *
     * 为什么要 `ACTION_SEND_MULTIPLE` 而不是逐张发：微信只接受一次多图，
     * 逐张发会连续弹 3 次选择器，用户点到最后一张时前面两张早忘了发到哪儿。
     * `EXTRA_STREAM` 的类型也必须是 `ArrayList<Uri>`，传单个 Uri 会被部分应用忽略。
     */
    fun shareImages(context: Context, files: List<File>, title: String) {
        if (files.isEmpty()) return
        val uris = ArrayList(
            files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        )
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "发送试卷图片")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
