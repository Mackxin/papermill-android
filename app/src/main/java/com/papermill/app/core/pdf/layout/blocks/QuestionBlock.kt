package com.papermill.app.core.pdf.layout.blocks

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.figure.FigurePainter
import com.papermill.app.core.pdf.layout.Block
import com.papermill.app.domain.generate.FigureSpec

/**
 * 一道题。
 *
 * 版式：`题号` 占左侧固定槽位（等宽字体，两位数不会把题干顶歪），
 * 题干从槽位右侧开始折行；[answerLines] > 0 时在题干下方画若干条答题横线
 * （应用题 / 竖式用）。
 *
 * 口算这类一行一题的题，答案位直接用全角括号「（　　）」写在题干里 ——
 * 比在行末画一条固定长度的横线更贴近真实练习卷，也省掉一行里的排版协调。
 *
 * v0.16.0 起支持 [figure]：题干下方插一张矢量配图（钟面 / 统计图 / 图形组）。
 * 图跟在题号槽位右侧对齐，和题干左边缘齐平 —— 图顶到最左边会让「3.」像是
 * 悬在图左上角，读起来像被裁掉了题号。
 */
class QuestionBlock(
    private val no: Int,
    private val stem: String,
    private val answerLines: Int = 0,
    private val size: Float = 11f,
    /**
     * 与下一题之间的呼吸。默认 [ITEM_GAP]，答案页排版时会把剩余高度摊到这里
     * 把整页撑满（见 PaperRenderer 的答案页自适应），否则答案只占半张纸。
     */
    private val itemGap: Float = ITEM_GAP,
    /** 看图题的配图；null = 纯文字题。答案页传 null（答案不需要图） */
    private val figure: FigureSpec? = null,
) : Block {

    private var lastWidth: Float = -1f
    private var lastLines: List<String> = emptyList()
    private var numberW: Float = -1f

    /**
     * 题号槽位的实际宽度。
     *
     * 原来固定 20pt：「9.」放得下，「10.」「100.」就顶出槽位和题干粘在一起，
     * 印出来是「10.5047」「13.10×9=」这种。改成按实际字宽量，再多留 3pt 呼吸。
     */
    private fun numberWidth(tp: TextPainter): Float {
        if (numberW < 0f) {
            numberW = maxOf(NUMBER_W, tp.measure("$no.", tp.paint(size, mono = true)) + 3f)
        }
        return numberW
    }

    private fun linesFor(tp: TextPainter, width: Float): List<String> {
        if (width == lastWidth && lastLines.isNotEmpty()) return lastLines
        val p = tp.paint(size)
        lastLines = tp.wrap(stem, p, (width - numberWidth(tp)).coerceAtLeast(20f))
        lastWidth = width
        return lastLines
    }

    /**
     * 这道题**不折行**需要多宽（题号槽位 + 题干一行）。
     *
     * 排版时拿它来定栏数：口算一行只要 80pt，塞进 247pt 的栏里右边全是空白；
     * 选择题带四个选项要 240pt，硬挤进 4 栏就得折成三行。
     * 取 80 分位而不是最大值 —— 一道特别长的题不该把整页压成 1 栏。
     */
    fun singleLineWidth(tp: TextPainter): Float {
        val p = tp.paint(size)
        // 题干里可能有显式换行（科学选择题的「A. 液态　B. 固态　C. 气态」单独一行）。
        // 直接量整串会把两行的宽度**加起来**，这些题就被判成「一栏都放不下」，
        // 白白降栏数、白缩字号。按行拆开取最宽的那一行才对。网页版同一条规则。
        val widest = stem.split('\n').maxOfOrNull { tp.measure(it, p) } ?: 0f
        return numberWidth(tp) + widest
    }

    override fun measure(tp: TextPainter, width: Float): Float {
        val p = tp.paint(size)
        val lineH = tp.lineHeight(p)
        val textH = linesFor(tp, width).size * lineH
        // 答题横线的间距要够手写：原来一行挨一行（约 16pt ≈ 5.6mm），
        // 四位数竖式根本写不进去，所以按 1.55 倍行高铺
        val extra = if (answerLines > 0) answerLines * lineH * ANSWER_LINE_FACTOR else 0f
        // 配图可用宽度要扣掉题号槽位，measure 和 paint 两边必须用同一个值，
        // 否则量出 80pt 高、画出来 92pt，直接压到下一道题上
        val figH = figure?.let {
            FigurePainter.height(it, tp, (width - numberWidth(tp)).coerceAtLeast(60f))
        } ?: 0f
        return textH + figH + (if (figure != null) FIGURE_GAP else 0f) + extra + itemGap
    }

    override fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float) {
        val p = tp.paint(size)
        val numPaint = tp.paint(size, mono = true)
        val lineH = tp.lineHeight(p)
        val lines = linesFor(tp, width)

        b.drawText("$no.", x, y + tp.baselineOffset(p, lineH), numPaint)

        var cursor = y
        lines.forEach { line ->
            b.drawText(line, x + numberWidth(tp), cursor + tp.baselineOffset(p, lineH), p)
            cursor += lineH
        }

        val fig = figure
        if (fig != null) {
            val fw = (width - numberWidth(tp)).coerceAtLeast(60f)
            FigurePainter.paint(b, tp, fig, x + numberWidth(tp), cursor + FIGURE_GAP, fw)
            cursor += FigurePainter.height(fig, tp, fw) + FIGURE_GAP
        }

        if (answerLines > 0) {
            val rule = tp.rulePaint(0.6f, 0xFF999999.toInt())
            val step = lineH * ANSWER_LINE_FACTOR
            repeat(answerLines) { i ->
                val ly = cursor + step * (i + 1) - lineH * 0.35f
                b.drawLine(x + numberWidth(tp), ly, x + width, ly, rule)
            }
        }
    }

    private companion object {
        /** 题号槽位最小宽度：够放「9.」不塌，再窄题号会贴到题干上 */
        const val NUMBER_W = 20f

        /** 题与题之间的呼吸 */
        const val ITEM_GAP = 4f

        /** 答题横线间距 = 行高 × 这个倍数（留够手写高度） */
        const val ANSWER_LINE_FACTOR = 1.55f

        /** 题干与配图之间、图与下一题之间的空隙 */
        const val FIGURE_GAP = 5f
    }
}
