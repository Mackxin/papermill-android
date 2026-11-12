package com.papermill.app.core.pdf.layout

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter

/**
 * 排版的基本单元。
 *
 * 约定（很重要，写错就会串页）：
 *  - [measure] 必须是**无副作用的纯计算**，同一宽度下可以被调多次；
 *  - [paint] 的 y 是这个块的**顶部**，不是文字基线；
 *  - 一个块默认不可拆分（一道题 + 它的答题留白必须同页），这正是它比
 *    「一条 y 游标往下走」的报表排版强的地方。
 */
interface Block {
    fun measure(tp: TextPainter, width: Float): Float

    fun paint(b: CanvasBackend, tp: TextPainter, x: Float, y: Float, width: Float)

    /** 是否要横跨整页（大题标题、卷头）：多栏排版时它不参与分栏 */
    val fullWidth: Boolean get() = false
}
