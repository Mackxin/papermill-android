package com.papermill.app.core.pdf.layout.blocks

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.layout.Block
import com.papermill.app.core.pdf.layout.SplittableBlock

/**
 * 网格表格块（九九乘法表、拼音音节表、公式表、语法表）。
 *
 * 每张速查表在渲染前会被拆成若干行的小表格块，交给 FlowLayout 分页 ——
 * 这样表格能跨页且表头重复，不会出现「一个 200 行的大块塞在一页里溢出」。
 *
 * ============================================================
 * 行高必须跟着内容走（v0.7.4 修的坑）
 * ============================================================
 * 旧版行高写死 `textSize * 2.2f`，也不折行，直接把整段文字当一行画出去。
 * 语法表这种单元格里塞着「规则 + 一整句例句」的表，后果是：
 * 文字横向溢到隔壁列、纵向几行叠在一起，打印出来糊成一团。
 *
 * 现在每个单元格先按列宽 [TextPainter.wrap] 折行（自带避头尾），
 * 行高取该行最高的那个单元格：行数 × 行距 + 上下留白。
 * measure 与 paint 共用同一份折行结果，否则高度和实际绘制对不上，文字会压到下一行。
 */
class TableBlock(
    private val headers: List<String>,
    private val weights: List<Float>,
    private val rows: List<List<String>>,
    private val showHeader: Boolean = true,
    private val textSize: Float = 10f,
    /** 行高倍数：单页速查表用它把行距拉松，铺满整页（字不动，只是留白变多） */
    private val rowScale: Float = 1f,
) : SplittableBlock {

    override val fullWidth: Boolean = true

    /** 单元格左右内边距 */
    private val padX = 3.5f

    /** 单元格上下内边距 */
    private val padY = 3.5f

    /** 行距系数：表格比正文紧一点，但要保证相邻行不粘 */
    private val lineFactor = 1.42f

    /** 再短的一行也要有这么多高，不然表头和单字行会显得扁 */
    private val minRowH = textSize * 2.0f

    /** 分页时同一块会被反复 measure，折行结果缓存住，别每次都重算 */
    private var cachedWidth = -1f
    private var cachedRows: List<RowLayout> = emptyList()

    private class RowLayout(
        /** 每列折好的行；cells[列][行] */
        val cells: List<List<String>>,
        val height: Float,
    )

    /** 列宽：weights 归一化到总宽；权重条目少于表头时按均分兜底 */
    private fun colWidths(width: Float): List<Float> {
        val total = weights.sum().takeIf { it > 0f } ?: 1f
        val out = weights.map { it / total * width }
        return if (out.size >= headers.size) {
            out
        } else {
            out + List(headers.size - out.size) { width / headers.size }
        }
    }

    private fun layout(tp: TextPainter, width: Float): List<RowLayout> {
        if (cachedWidth == width) return cachedRows

        val normal = tp.paint(textSize)
        val bold = tp.paint(textSize, bold = true)
        val lineH = textSize * lineFactor
        val cols = colWidths(width)

        val all = ArrayList<List<String>>(rows.size + 1)
        if (showHeader) all.add(headers)
        all.addAll(rows)

        cachedRows = all.mapIndexed { index, cells ->
            val paint = if (showHeader && index == 0) bold else normal
            val lines = cols.mapIndexed { c, cw ->
                tp.wrap(
                    cells.getOrNull(c) ?: "",
                    paint,
                    (cw - padX * 2f).coerceAtLeast(textSize * 2f),
                )
            }
            val maxLines = lines.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
            val rawH = (maxLines * lineH + padY * 2f).coerceAtLeast(minRowH)
            RowLayout(lines, rawH * rowScale)
        }
        cachedWidth = width
        return cachedRows
    }

    override fun measure(tp: TextPainter, width: Float): Float =
        layout(tp, width).sumOf { it.height.toDouble() }.toFloat() + BOTTOM_GAP

    /**
     * 按剩余高度把行切开，页尾能塞几行就塞几行。
     *
     * 关键细节：
     *  - 续页也要带表头，所以可用的高度要先扣掉一行表头的高度；
     *  - 至少给后半截留一行，否则会切出空块、FlowLayout 里死循环；
     *  - 一行都放不下就返回 null，让调用方翻页后整块重排。
     */
    override fun split(tp: TextPainter, width: Float, maxHeight: Float): Pair<Block, Block>? {
        if (rows.size <= 1) return null

        val laid = layout(tp, width)
        val firstRowIndex = if (showHeader) 1 else 0
        // 前半截在原位置，表头本来就在里面；后半截另起一页，还要再画一次表头
        var used = if (showHeader) laid.first().height else 0f
        var take = 0
        // 上界留一行给后半截：切出空尾巴等于没切
        while (take < rows.size - 1) {
            val rowH = laid.getOrNull(firstRowIndex + take)?.height ?: break
            if (used + rowH + BOTTOM_GAP > maxHeight) break
            used += rowH
            take++
        }
        if (take <= 0) return null

        return TableBlock(headers, weights, rows.take(take), showHeader, textSize, rowScale) to
            TableBlock(headers, weights, rows.drop(take), showHeader, textSize, rowScale)
    }

    override fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float) {
        val normal = tp.paint(textSize)
        val bold = tp.paint(textSize, bold = true)
        val frame = tp.rulePaint(0.7f, 0xFF888888.toInt())
        val fine = tp.rulePaint(0.4f, 0xFFBBBBBB.toInt())
        val cols = colWidths(width)
        val lineH = textSize * lineFactor
        val laid = layout(tp, width)

        var cursor = y
        laid.forEachIndexed { rowIndex, row ->
            val isHeader = showHeader && rowIndex == 0
            val top = cursor
            val bottom = cursor + row.height

            // 横线
            b.drawLine(x, top, x + width, top, if (isHeader) frame else fine)

            var left = x
            cols.forEachIndexed { c, cw ->
                val right = left + cw
                // 竖线
                b.drawLine(left, top, left, bottom, if (c == 0) frame else fine)

                val lines = row.cells.getOrNull(c) ?: emptyList()
                if (lines.any { it.isNotEmpty() }) {
                    val paint = if (isHeader) bold else normal
                    // 整块文字在行内垂直居中：行高可能被同行的长单元格撑高
                    var ty = top + (row.height - lines.size * lineH) / 2f
                    lines.forEach { line ->
                        if (line.isNotEmpty()) {
                            val tx = if (isHeader) {
                                left + (cw - tp.measure(line, paint)) / 2f
                            } else {
                                left + padX
                            }
                            b.drawText(line, tx, ty + tp.baselineOffset(paint, lineH), paint)
                        }
                        ty += lineH
                    }
                }
                left = right
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
