package com.papermill.app.core.pdf.layout.blocks

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.layout.Block

/**
 * 网格表格块（九九乘法表、拼音音节表、公式表）。
 *
 * 每张速查表在渲染前会被拆成若干行的小表格块，交给 FlowLayout 分页 ——
 * 这样表格能跨页且表头重复，不会出现「一个 200 行的大块塞在一页里溢出」。
 */
class TableBlock(
    private val headers: List<String>,
    private val weights: List<Float>,
    private val rows: List<List<String>>,
    private val showHeader: Boolean = true,
    private val textSize: Float = 10f,
) : Block {

    override val fullWidth: Boolean = true

    private val rowH: Float get() = textSize * 2.2f

    override fun measure(tp: TextPainter, width: Float): Float {
        val lineCount = rows.size + if (showHeader) 1 else 0
        return lineCount * rowH + BOTTOM_GAP
    }

    override fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float) {
        val normal = tp.paint(textSize)
        val bold = tp.paint(textSize, bold = true)
        val frame = tp.rulePaint(0.7f, 0xFF888888.toInt())
        val fine = tp.rulePaint(0.4f, 0xFFBBBBBB.toInt())

        val totalWeight = weights.sum().takeIf { it > 0f } ?: 1f
        // 每列边界的 x 坐标
        val edges = ArrayList<Float>(headers.size + 1)
        edges.add(x)
        var acc = 0f
        weights.forEach { w ->
            acc += w / totalWeight * width
            edges.add(x + acc)
        }
        while (edges.size < headers.size + 1) edges.add(x + width)

        var cursor = y
        val allRows = ArrayList<List<String>>()
        if (showHeader) allRows.add(headers)
        allRows.addAll(rows)

        allRows.forEachIndexed { rowIndex, cells ->
            val isHeader = showHeader && rowIndex == 0
            val top = cursor
            val bottom = cursor + rowH

            // 横线
            b.drawLine(x, top, x + width, top, if (isHeader) frame else fine)

            for (c in 0 until headers.size) {
                val left = edges[c]
                val right = edges.getOrElse(c + 1) { x + width }
                // 竖线
                b.drawLine(left, top, left, bottom, if (c == 0) frame else fine)

                val text = cells.getOrNull(c) ?: ""
                if (text.isEmpty()) continue
                val paint = if (isHeader) bold else normal
                val tw = tp.measure(text, paint)
                val tx = left + ((right - left) - tw) / 2f
                val baseline = top + tp.baselineOffset(paint, rowH)
                b.drawText(text, tx, baseline, paint)
            }
            b.drawLine(x + width, top, x + width, bottom, frame)
            cursor = bottom
        }
        b.drawLine(x, cursor, x + width, cursor, frame)
    }

    private companion object {
        const val BOTTOM_GAP = 10f
    }
}
