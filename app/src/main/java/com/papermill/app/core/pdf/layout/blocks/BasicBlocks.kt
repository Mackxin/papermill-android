package com.papermill.app.core.pdf.layout.blocks

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.layout.Block

/**
 * 竖直留白。用于给「应用题大题之间」「答案区之前」留空，
 * 或者临时占位（后续里程碑里可做「手动插入空行」）。
 */
class SpacerBlock(private val height: Float) : Block {
    override fun measure(tp: TextPainter, width: Float): Float = height
    override fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float) = Unit
}

/**
 * 通栏说明文字（小字灰色）。用于「共 20 题，满分 40 分」这类副标题，
 * 或题前的做题说明。
 *
 * v0.11.2 起支持折行：大题说明可能是一整句（「用竖式计算下面各题，把得数写在括号里」），
 * 以前按单行量高度、单行画，字长了就横向溢出压到页边。
 */
class NoteBlock(
    private val text: String,
    private val size: Float = 9f,
) : Block {

    override val fullWidth: Boolean = true

    /** 说明文字也不该孤零零挂在页尾 */
    override val keepWithNext: Boolean = true

    private var lastWidth: Float = -1f
    private var lastLines: List<String> = emptyList()

    private fun linesFor(tp: TextPainter, width: Float): List<String> {
        if (width == lastWidth && lastLines.isNotEmpty()) return lastLines
        lastLines = tp.wrap(text, tp.paint(size), width)
        lastWidth = width
        return lastLines
    }

    override fun measure(tp: TextPainter, width: Float): Float {
        val p = tp.paint(size)
        return linesFor(tp, width).size * tp.lineHeight(p) * 1.2f
    }

    override fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float) {
        val p = tp.paint(size, color = 0xFF666666.toInt())
        val lineH = tp.lineHeight(p) * 1.2f
        linesFor(tp, width).forEachIndexed { i, line ->
            b.drawText(line, x, y + i * lineH + tp.baselineOffset(p, lineH), p)
        }
    }
}
