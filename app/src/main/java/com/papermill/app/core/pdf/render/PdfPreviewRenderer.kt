package com.papermill.app.core.pdf.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * 把刚生成的 PDF 字节渲染成位图，给预览页做真正的 WYSIWYG。
 *
 * 用系统 `PdfRenderer`（API 21+），不引任何 PDF 库。
 * 预览走的是「和打印同一个 PDF 字节」，所以预览长什么样，纸上就是什么样 ——
 * 不会出现「App 里好看、打出来错位」这种最难查的问题。
 */
object PdfPreviewRenderer {

    fun render(context: Context, bytes: ByteArray, scale: Float = 1.5f): List<ImageBitmap> {
        val file = File(context.cacheDir, "preview_current.pdf")
        file.writeBytes(bytes)

        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val out = ArrayList<ImageBitmap>(renderer.pageCount)
        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                try {
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out.add(bitmap.asImageBitmap())
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            fd.close()
        }
        return out
    }
}
