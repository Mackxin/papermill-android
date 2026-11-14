package com.papermill.app.core.pdf

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache

/**
 * 文字测量与折行。
 *
 * ============================================================
 * 为什么要有一个类专门管 Paint
 * ============================================================
 * 一份 4 页的卷子约有 200 个文字块，每块在 measure 阶段要测量 1~2 次，
 * 如果每次 new Paint() + 每次重新 measureText，在低端机上会明显掉帧（预览页尤其）。
 * 这里做两件事：
 *  1. Paint 按 (字号, 粗体, 等宽) 缓存复用 —— 同一个 Paint 对象喂给 PDF 和预览两侧，
 *     measureText 结果才完全一致（预览和打印不一致是最难查的 bug）。
 *  2. measureText 结果进 LruCache —— 数字题大量重复（"1." "（　　）"），命中率很高。
 *
 * ============================================================
 * 字体：只用 sans-serif / monospace，不打包字体文件
 * ============================================================
 * 中文由系统字体回退（Noto Sans CJK）负责。打包思源黑体要多 5~10MB，
 * 而 PDF 里嵌入字体还会让文件从 60KB 涨到 3MB，得不偿失。
 */
class TextPainter {

    private val paints = HashMap<String, Paint>()
    private val measureCache = LruCache<String, Float>(4096)

    /**
     * 取一支 Paint。颜色也进 key —— 否则「灰字小注」会把共享的黑色 Paint 改成灰色，
     * 后续正文跟着一起变灰（这类 bug 在 PDF 里极难一眼看出）。
     */
    fun paint(
        size: Float,
        bold: Boolean = false,
        mono: Boolean = false,
        color: Int = Color.BLACK,
    ): Paint {
        val key = "$size|$bold|$mono|$color"
        paints[key]?.let { return it }
        return Paint().apply {
            isAntiAlias = true
            this.color = color
            textSize = size
            typeface = Typeface.create(
                if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
            paints[key] = this
        }
    }

    /** 细线 / 分隔线 / 下划线用的描边 Paint。每次新建：颜色和线宽组合太多，缓存不划算。 */
    fun rulePaint(width: Float = 0.6f, color: Int = 0xFF444444.toInt()): Paint = Paint().apply {
        isAntiAlias = true
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    /**
     * 虚线画笔：田字格 / 米字格的十字与斜线辅助线用它。
     *
     * 实线 0.4pt 浅灰在 PDF 里几乎等于没有（1.5 倍预览时只有 0.6px，
     * 抗锯齿一糊就全白了），学生纸上的辅助线本来就该是虚线 —— 既不抢笔画，
     * 又能让人一眼看出这是「田」不是空方格。
     */
    fun dashPaint(
        width: Float = 0.55f,
        color: Int = 0xFF9C9C9C.toInt(),
        dash: Float = 2.4f,
        gap: Float = 2.4f,
    ): Paint = Paint().apply {
        isAntiAlias = true
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        pathEffect = DashPathEffect(floatArrayOf(dash, gap), 0f)
    }

    fun measure(text: String, paint: Paint): Float {
        val key = "${paint.textSize}|${paint.typeface}|$text"
        measureCache.get(key)?.let { return it }
        val w = paint.measureText(text)
        measureCache.put(key, w)
        return w
    }

    /** 单行行高：正文用 1.5 倍行距，这是中文练习卷的通用呼吸感 */
    fun lineHeight(paint: Paint, factor: Float = 1.5f): Float = paint.textSize * factor

    /**
     * 一行文字在 [lineHeight] 高的槽位里垂直居中时，基线该落在哪。
     * 返回的是相对槽位顶部的偏移量 —— 调用方加 y 即可。
     */
    fun baselineOffset(paint: Paint, lineHeight: Float): Float =
        (lineHeight - (paint.descent() - paint.ascent())) / 2f - paint.ascent()

    /**
     * 按宽度折行，逐字推进。
     *
     * 三个排版必须处理的细节：
     *  1. **避头尾**：`，。、；：？！）》」` 不能出现在行首。发现断点会落在禁则字符上时，
     *     多吞一个字（宁可稍微溢出几个 pt，也不要出现"一行开头是个句号"）。
     *  2. **英文不劈词**：逐字推进天生会把 `worst` 断成 `w` + `orst`，
     *     语法表、音标表的例词全是英文，断在词中间比溢出还难看。
     *     断点两侧都是字母/数字时往前退到词边界；单个词比整行还长才允许硬断。
     *  3. 显式换行符 `\n` 强制断行（题干里偶有多行）。
     */
    fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        if (maxWidth <= 0f) return listOf(text)

        val out = ArrayList<String>()
        text.split('\n').forEach { para ->
            if (para.isEmpty()) {
                out.add("")
                return@forEach
            }
            var i = 0
            while (i < para.length) {
                var len = 1
                while (i + len <= para.length &&
                    measure(para.substring(i, i + len), paint) <= maxWidth
                ) {
                    len++
                }
                len = (len - 1).coerceAtLeast(1)

                // 英文不劈词：断点前一个字符和断点后一个字符同属一个词 → 往前退
                while (len > 1 && i + len < para.length &&
                    isWordChar(para[i + len - 1]) && isWordChar(para[i + len])
                ) {
                    len--
                }

                // 避头：行首不能是这些标点 → 把下一个字符也拉进本行
                while (i + len < para.length && NO_LINE_START.indexOf(para[i + len]) >= 0) {
                    len++
                }
                len = len.coerceAtMost(para.length - i)

                out.add(para.substring(i, i + len))
                i += len
            }
        }
        return if (out.isEmpty()) listOf("") else out
    }

    /** 组成英文单词的字符：字母、数字、词内撇号（don't）、词内连字符算可断点 */
    private fun isWordChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '\''

    private companion object {
        /** 不能出现在行首的字符 */
        const val NO_LINE_START = "，。、；：？！）〕］｝》」』】”’%,.;:?!)]}"
    }
}
