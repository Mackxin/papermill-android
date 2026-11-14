package com.papermill.app.core.print

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import com.papermill.app.core.pdf.render.RenderedPdf
import com.papermill.app.domain.generate.PageOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 调起系统打印对话框。
 *
 * 局域网打印不需要任何奔图 SDK：奔图 M1 支持 AirPrint / Mopria，
 * 手机装「奔图打印」App 或启用 Mopria Print Service 之后，
 * 系统打印框里就能直接看到这台机器。
 */
object PrintLauncher {

    suspend fun print(activity: Activity, jobName: String, pdf: RenderedPdf) {
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("当前系统没有打印服务")

        // PDF 页面本身就是 842×595pt 的横版时，打印框也必须按横版 A4 匹配，
        // 否则 print service 会把横版内容再缩放/旋转一次，题号被裁掉。
        val media = if (pdf.orientation == PageOrientation.LANDSCAPE) {
            PrintAttributes.MediaSize.ISO_A4.asLandscape()
        } else {
            PrintAttributes.MediaSize.ISO_A4
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(media)
            // PDF 自己已经留了 14mm 边距，这里不要再叠一层
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()

        val adapter = PdfPrintAdapter(jobName, pdf.bytes, pdf.pageCount)

        // print() 会立刻弹出系统 UI 并回调 onLayout，必须在主线程调用
        withContext(Dispatchers.Main) {
            pm.print(jobName, adapter, attributes)
        }
    }

    /**
     * 打印用户自己选的 PDF 文件。
     *
     * 走 [FilePrintAdapter]（流式拷贝），所以三四十 MB 的教材也能打，不会 OOM。
     * 纸张方向按首页的宽高比选：横版内容用竖版纸打印，驱动会再缩一次，字小到看不清。
     */
    suspend fun printFile(
        activity: Activity,
        jobName: String,
        file: java.io.File,
        pageCount: Int,
        landscape: Boolean,
    ) {
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("当前系统没有打印服务")

        val media = if (landscape) {
            PrintAttributes.MediaSize.ISO_A4.asLandscape()
        } else {
            PrintAttributes.MediaSize.ISO_A4
        }
        val attributes = PrintAttributes.Builder()
            .setMediaSize(media)
            // 用户自己的文件可能自带留白，这里不能再叠边距，否则内容被压小
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val adapter = FilePrintAdapter(jobName, file, pageCount)
        withContext(Dispatchers.Main) {
            pm.print(jobName, adapter, attributes)
        }
    }
}

/**
 * 探测系统里有没有可用的打印服务。
 *
 * ============================================================
 * 为什么不用 PrintManager.getPrintServices()
 * ============================================================
 * 那个方法在公开 SDK 里**不存在**（javap 查 android-34 的 android.jar，
 * PrintManager 只有 getPrintJobs / print / isPrintServiceEnabled 三个公开方法）。
 * 用 PackageManager 查「声明了 android.printservice.PrintService 的服务」更可靠。
 *
 * 另外 Android 11 起有包可见性限制，Manifest 里没声明
 * `<queries><intent><action android:name="android.printservice.PrintService"/></intent></queries>`
 * 的话这里永远返回 false —— 会误判成"没有打印服务"。
 *
 * 探测结果只用于**提示**，不能用来阻止用户点打印（探测本身就可能误判）。
 */
object PrintServiceProbe {

    fun hasService(context: Context): Boolean = runCatching {
        context.packageManager
            .queryIntentServices(Intent(ACTION_PRINT_SERVICE), 0)
            .isNotEmpty()
    }.getOrDefault(false)

    /** 跳到系统「打印」设置页，让用户启用 Mopria / 奔图打印服务 */
    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent("android.print.PRINT_SERVICE_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private const val ACTION_PRINT_SERVICE = "android.printservice.PrintService"
}
