package com.papermill.app.core.pdf.layout.blocks

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.layout.Block

/**
 * 一道题。
 *
 * 版式：`题号` 占左侧固定槽位（等宽字体，两位数不会把题干顶歪），
 * 题干从槽位右侧开始折行；[answerLines] > 0 时在题干下方画若干条答题横线
 * （应用题 / 竖式用）。
 *
 * 口算这类一行一题的题，答案位直接用全角括号「（　　）」写在题干里 ——
 * 比在行末画一条固定长度的横线更贴近真实练习卷，也省掉一行里的排版协调。
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

    override fun measure(tp: TextPainter, width: Float): Float {
        val p = tp.paint(size)
        val lineH = tp.lineHeight(p)
        val textH = linesFor(tp, width).size * lineH
        // 答题横线的间距要够手写：原来一行挨一行（约 16pt ≈ 5.6mm），
        // 四位数竖式根本写不进去，所以按 1.55 倍行高铺
        val extra = if (answerLines > 0) answerLines * lineH * ANSWER_LINE_FACTOR else 0f
        return textH + extra + itemGap
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
    }
}
