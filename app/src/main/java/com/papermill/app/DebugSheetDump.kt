package com.papermill.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import com.papermill.app.core.pdf.render.PaperRenderer
import com.papermill.app.core.pdf.render.PdfPreviewRenderer
import com.papermill.app.core.pdf.render.PracticeRenderer
import com.papermill.app.core.pdf.render.ReferenceRenderer
import com.papermill.app.domain.generate.GenContext
import com.papermill.app.domain.generate.GeneratorRegistry
import com.papermill.app.domain.generate.QuestionType
import com.papermill.app.domain.reference.PracticeData
import com.papermill.app.domain.reference.ReferenceData
import java.io.File
import java.io.FileOutputStream

/**
 * 内置纸张的「批量体检」工具，**只在 debug 包生效**。
 *
 * ============================================================
 * 为什么需要它
 * ============================================================
 * App 里有 16 张速查表 + 8 张练习纸 + 24 张主题识字纸，靠手点 UI 一张张
 * 看根本看不过来；而排版崩坏（文字溢出格子、页尾大片空白、表格断开）
 * 只有真的把 PDF 渲出来才能发现。
 *
 * 这里直接在设备上跑一遍渲染管线，把每一页存成 PNG，并顺手用像素扫描
 * 算出「最后一处墨迹到内容区底部还剩多少 pt」—— 那就是页尾空白。
 * 空白超过 200pt 基本可以认定分页策略有问题。
 *
 * 触发方式（不影响正式功能）：
 * ```
 * adb shell am start -n com.papermill.app/.MainActivity --ez dumpSheets true
 * ```
 * 产物在 `/sdcard/Android/data/com.papermill.app/files/sheets/`。
 */
object DebugSheetDump {

    private const val TAG = "SheetDump"

    /** 渲染倍率：1pt = 1.5px，A4 竖版约 892×1263，看清小字够用 */
    private const val SCALE = 1.5f

    /** 页脚高度（pt），与 PageGeometry.footerH 保持一致 */
    private const val FOOTER_H = 26f
    private const val MARGIN = 40f

    /** 页脚那条分隔线画在内容区底部上方约 38pt，扫描时要一并避开，否则每页空白都算成 38 */
    private const val FOOTER_RULE_GAP = 46f

    fun dump(ctx: Context) {
        Thread {
            val dir = File(ctx.getExternalFilesDir(null), "sheets")
            dir.deleteRecursively()
            dir.mkdirs()

            val jobs = ArrayList<Triple<String, String, () -> ByteArray>>()
            ReferenceData.sheets.forEach { s ->
                jobs.add(Triple("ref", s.title) { PaperRenderer.render(ReferenceRenderer.build(s)).bytes })
            }
            (PracticeData.sheets + PracticeData.themeSheets).forEach { s ->
                jobs.add(Triple("practice", s.title) { PaperRenderer.render(PracticeRenderer.build(s)).bytes })
            }

            val report = StringBuilder()
            report.append("名称\t页数\t各页页尾空白(pt)\n")

            jobs.forEachIndexed { index, job ->
                val (kind, title, render) = job
                val name = "%02d-%s-%s".format(index, kind, title.replace(Regex("[^\\w\\u4e00-\\u9fa5]"), "_"))
                try {
                    val bytes = render()
                    val pages = PdfPreviewRenderer.render(ctx, bytes, SCALE)
                    val blanks = ArrayList<String>()
                    pages.forEachIndexed { pageIndex, image ->
                        val bmp = image.asAndroidBitmap()
                        save(dir, "$name-p${pageIndex + 1}.png", bmp)
                        blanks.add("%.0f".format(blankAtBottom(bmp)))
                    }
                    val line = "$title\t${pages.size}\t${blanks.joinToString(" / ")}"
                    report.append(line).append('\n')
                    Log.i(TAG, line)
                } catch (t: Throwable) {
                    val line = "$title\t渲染失败：${t.message}"
                    report.append(line).append('\n')
                    Log.e(TAG, line, t)
                }
            }

            File(dir, "report.txt").writeText(report.toString())
            Log.i(TAG, "done -> ${dir.absolutePath}")
        }.start()
    }

    /**
     * 题型抽样：把指定题型各生成若干题，直接打到 logcat。
     *
     * 用途是**核对题干与答案是否配套** —— 选择题选项洗牌后答案字母容易对不上，
     * 光看代码看不出来，只有把题目真造出来逐条念一遍才可靠。
     */
    fun dumpQuestions() {
        Thread {
            val ctxList = listOf(
                QuestionType.IDIOM_FILL to 4,
                QuestionType.IDIOM_MEANING to 4,
                QuestionType.POEM_FILL to 4,
                QuestionType.POEM_MATCH to 4,
                QuestionType.ORAL_MISSING to 4,
                QuestionType.ORAL_REMAINDER to 4,
            )
            ctxList.forEach { (type, n) ->
                val gen = GeneratorRegistry.of(type, 4) ?: return@forEach
                val ctx = GenContext(
                    grade = 4,
                    count = n,
                    difficulty = 2,
                    rng = java.util.Random(42),
                    blocked = HashSet(),
                )
                val list = runCatching { gen.generate(ctx) }.getOrElse { emptyList() }
                Log.i(TAG, "===== ${type.label}（${list.size} 题）=====")
                list.forEachIndexed { i, q ->
                    Log.i(TAG, "Q${i + 1} ${q.stem.replace("\n", " ｜ ")}")
                    Log.i(TAG, "   答：${q.answer}")
                }
            }
        }.start()
    }

    private fun save(dir: File, name: String, bmp: Bitmap) {
        FileOutputStream(File(dir, name)).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * 页尾空白：内容区最下沿往上，第一处「有墨」的位置离底部多远（pt）。
     *
     * 扫描时避开页脚那一行小字，否则每页都会被页脚撑成 0。
     */
    private fun blankAtBottom(bmp: Bitmap): Float {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // 内容区最下沿：页高 - 页边距 - 页脚 - 页脚分隔线的余量
        val bottomPx = ((h / SCALE) - MARGIN - FOOTER_H - FOOTER_RULE_GAP) * SCALE
        val from = bottomPx.toInt().coerceIn(0, h - 1)

        for (y in from downTo 0) {
            val rowStart = y * w
            var x = 0
            while (x < w) {
                val c = pixels[rowStart + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r < 240 || g < 240 || b < 240) {
                    return (from - y) / SCALE
                }
                x += 2
            }
        }
        return from / SCALE
    }
}
