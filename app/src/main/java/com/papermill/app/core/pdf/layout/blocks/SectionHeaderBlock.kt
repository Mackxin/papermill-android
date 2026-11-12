package com.papermill.app.core.pdf.layout.blocks

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.layout.Block

/**
 * 大题标题，如「一、口算（每题 2 分，共 20 分）」。
 *
 * 它 `fullWidth = true`：多栏排版时横跨整页，否则会挤在第一栏顶部
 * （看起来像一道题的标题，而不是整大题的标题）。
 */
class SectionHeaderBlock(
    private val title: String,
    private val size: Float = 11.5f,
) : Block {

    override val fullWidth: Boolean = true

    override fun measure(tp: TextPainter, width: Float): Float {
        val p = tp.paint(size, bold = true)
        return tp.lineHeight(p) + TOP_PAD + BOTTOM_PAD
    }

    override fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float) {
        val p = tp.paint(size, bold = true)
        val lineH = tp.lineHeight(p)
        b.drawText(title, x, y + TOP_PAD + tp.baselineOffset(p, lineH), p)
    }

    private companion object {
        const val TOP_PAD = 10f
        const val BOTTOM_PAD = 6f
    }
}
