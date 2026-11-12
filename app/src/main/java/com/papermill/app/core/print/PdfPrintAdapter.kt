package com.papermill.app.core.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream

/**
 * 把已经生成好的 PDF 字节交给系统打印框架。
 *
 * ============================================================
 * 两个必须写进代码的坑
 * ============================================================
 * 1. **onWrite 里绝不能再渲染**。系统打印对话框一弹出就调 onLayout，紧接着调 onWrite，
 *    而这里是有超时的。如果此时才开始排版 4 页卷子，低端机会直接超时失败。
 *    所以 [bytes] 必须由调用方提前在 IO 线程算好。
 *
 * 2. **不要把输出流 close 掉**。`destination.fileDescriptor` 是系统持有的 fd，
 *    关掉流会连带把它关掉，结果打印出来是空白页（这个 bug 在有些 ROM 上必现）。
 *    只 write + flush，让框架自己收尾。
 */
class PdfPrintAdapter(
    private val jobName: String,
    private val bytes: ByteArray,
    private val pageCount: Int,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("$jobName.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            // 页数必须准，否则打印预览里的页数和实际不符
            .setPageCount(pageCount)
            .build()
        // 第二个参数 = 内容是否变了（true 表示需要重新 onWrite）
        callback?.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?,
    ) {
        try {
            val fd = destination ?: error("打印目标为空")
            val os = FileOutputStream(fd.fileDescriptor)
            os.write(bytes)
            os.flush()
            // 注意：这里既不 close os，也不 close fd（原因见类注释第 2 条）
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (t: Throwable) {
            callback?.onWriteFailed(t.message ?: "写入打印数据失败")
        }
    }
}
