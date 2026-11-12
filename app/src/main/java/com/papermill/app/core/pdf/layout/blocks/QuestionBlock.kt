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
) : Block {

    private var lastWidth: Float = -1f
    private var lastLines: List<String> = emptyList()

    private fun linesFor(tp: TextPainter, width: Float): List<String> {
        if (width == lastWidth && lastLines.isNotEmpty()) return lastLines
        val p = tp.paint(size)
        lastLines = tp.wrap(stem, p, (width - NUMBER_W).coerceAtLeast(20f))
        lastWidth = width
        return lastLines
    }

    override fun measure(tp: TextPainter, width: Float): Float {
        val p = tp.paint(size)
        val lineH = tp.lineHeight(p)
        val textH = linesFor(tp, width).size * lineH
        val extra = if (answerLines > 0) answerLines * lineH else 0f
        return textH + extra + ITEM_GAP
    }

    override fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float) {
        val p = tp.paint(size)
        val numPaint = tp.paint(size, mono = true)
        val lineH = tp.lineHeight(p)
        val lines = linesFor(tp, width)

        b.drawText("$no.", x, y + tp.baselineOffset(p, lineH), numPaint)

        var cursor = y
        lines.forEach { line ->
            b.drawText(line, x + NUMBER_W, cursor + tp.baselineOffset(p, lineH), p)
            cursor += lineH
        }

        if (answerLines > 0) {
            val rule = tp.rulePaint(0.6f, 0xFF999999.toInt())
            repeat(answerLines) { i ->
                val ly = cursor + lineH * (i + 1) - lineH * 0.35f
                b.drawLine(x + NUMBER_W, ly, x + width, ly, rule)
            }
        }
    }

    private companion object {
        /** 题号槽位宽度：够放「100.」仍有余量 */
        const val NUMBER_W = 20f

        /** 题与题之间的呼吸 */
        const val ITEM_GAP = 4f
    }
}
