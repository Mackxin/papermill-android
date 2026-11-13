package com.papermill.app.core.pdf.layout.blocks

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.layout.Block

/**
 * 把一个块强制改成「占整行」。
 *
 * 用途：同一份卷里既有 2 栏口算，又有需要书写过程的应用题。
 * 应用题不能被塞进半栏宽里，把它包成整宽块后，就能和口算处在
 * **同一个连续排版流**里，而不会像旧版那样「换个大题就换一页」。
 */
class FullWidthBlock(private val delegate: Block) : Block {
    override val fullWidth: Boolean = true

    override fun measure(tp: TextPainter, width: Float): Float = delegate.measure(tp, width)

    override fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float) {
        delegate.paint(b, tp, x, y, width)
    }
}
