package com.papermill.app.core.print

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import com.papermill.app.core.pdf.render.RenderedPdf
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

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
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
}

/**
 * 探测系统里有没有可用的打印服务。
 *
 * 注意：Android 11 起有包可见性限制，Manifest 里没声明
 * `<queries><intent><action android:name="android.printservice.PrintService"/></intent></queries>`
 * 的话，[PrintManager.getPrintServices] 永远返回空 —— 会误判成"没有打印服务"。
 *
 * 探测结果只用于**提示**，不能用来阻止用户点打印（探测本身就可能误判）。
 */
object PrintServiceProbe {

    fun hasService(context: Context): Boolean {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        return runCatching { pm.printServices.isNotEmpty() }.getOrDefault(false)
    }

    /** 跳到系统「打印」设置页，让用户启用 Mopria / 奔图打印服务 */
    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent("android.print.PRINT_SERVICE_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
