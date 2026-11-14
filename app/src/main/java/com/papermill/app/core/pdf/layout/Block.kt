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

/**
 * 可以在行边界处切成两半的块。
 *
 * 表格用固定行数切块（`rowsPerBlock`）时，页尾剩余空间经常只够放 1~2 行，
 * 整块却被推到下一页 —— 打印出来就是大片空白。真正按剩余高度切，
 * 才能把这半页填满。
 *
 * 只对「行与行之间可以断开」的块实现（表格）；一道题加它的答题留白
 * 不能从中间劈开，所以 [QuestionBlock] 不实现本接口。
 */
interface SplittableBlock : Block {
    /**
     * 在 [maxHeight] 高度内尽量多塞一部分。
     *
     * 返回 (前半, 后半)；连一行都放不下、或本来就装得下不需要切时返回 null，
     * 交给调用方按原样处理。
     */
    fun split(tp: TextPainter, width: Float, maxHeight: Float): Pair<Block, Block>?
}
