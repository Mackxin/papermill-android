package com.papermill.app.core.pdf.layout.blocks

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.layout.Block

/**
 * 大题标题，如「一、口算（每题 2 分，共 20 分）」。
 *
 * 它 `fullWidth = true`：多栏排版时横跨整页，否则会挤在第一栏顶部
 * （看起来像一道题的标题，而不是整大题的标题）。
 *
 * v0.22.0：[note] 是「跟标题排在同一行」的那句话（低年级科学：请家长读题）。
 * 以前它是一条独立说明块，占掉整整一行高度 —— 这类一句话提示本来就只该是标题的尾巴。
 * 一行放不下（窄纸 / 大字号）时才退化成第二行，并且 measure 里同步多算一行，
 * 否则会压到下一题上。
 */
class SectionHeaderBlock(
    private val title: String,
    private val size: Float = 11.5f,
    /** 这道大题下面按几栏排；0 = 沿用当前栏数 */
    override val columnHint: Int = 0,
    /** 挂在标题同一行的短提示；空串 = 没有 */
    private val note: String = "",
) : Block {

    override val fullWidth: Boolean = true

    /** 标题不能单独留在页尾（后面一道题都放不下时，整体挪到下一页） */
    override val keepWithNext: Boolean = true

    private val noteSize: Float get() = size * 0.78f

    /** 这一行塞不塞得下「标题 + 提示」；塞不下就走第二行 */
    private fun fitsInline(tp: TextPainter, width: Float): Boolean {
        if (note.isBlank()) return false
        val titleW = tp.measure(title, tp.paint(size, bold = true))
        val noteW = tp.measure(note, tp.paint(noteSize, color = NOTE_COLOR))
        return titleW + NOTE_GAP + noteW <= width
    }

    override fun measure(tp: TextPainter, width: Float): Float {
        val p = tp.paint(size, bold = true)
        // 只有真的会换到第二行时才多算一行 —— 否则这条提示等于白吃一行纸
        val extra = if (note.isNotBlank() && !fitsInline(tp, width)) noteSize * 1.4f else 0f
        return tp.lineHeight(p) + TOP_PAD + BOTTOM_PAD + extra
    }

    override fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float) {
        val p = tp.paint(size, bold = true)
        val lineH = tp.lineHeight(p)
        b.drawText(title, x, y + TOP_PAD + tp.baselineOffset(p, lineH), p)
        if (note.isBlank()) return

        val np = tp.paint(noteSize, color = NOTE_COLOR)
        if (fitsInline(tp, width)) {
            val nx = x + tp.measure(title, p) + NOTE_GAP
            val noteH = noteSize * 1.2f
            val top = y + TOP_PAD + (lineH - noteH) / 2f
            b.drawText(note, nx, top + tp.baselineOffset(np, noteH), np)
        } else {
            val noteH = noteSize * 1.4f
            val top = y + TOP_PAD + lineH
            b.drawText(note, x, top + tp.baselineOffset(np, noteH), np)
        }
    }

    private companion object {
        const val TOP_PAD = 10f
        const val BOTTOM_PAD = 6f
        const val NOTE_GAP = 10f

        /** 比正文浅一档的灰：是提示，不该跟标题抢注意力 */
        const val NOTE_COLOR = 0xFF777777.toInt()
    }
}
