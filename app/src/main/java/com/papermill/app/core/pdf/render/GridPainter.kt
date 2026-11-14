package com.papermill.app.core.pdf.render

import android.graphics.Paint
import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.PageGeometry
import com.papermill.app.core.pdf.TextPainter
import java.util.Random

/**
 * 练习纸绘制：把一页 A4 的可用区域用格子 / 横线**铺满**。
 *
 * 为什么不复用 FlowLayout：试卷是「有多少题排多少页」，练习纸反过来是
 * 「固定一页纸，按剩余高度算能画多少行」。硬套流式排版反而画不满，
 * 纸上会留一大块白 —— 与用户「最后要打印出完整的一页纸」正好相反。
 *
 * 所有线条都用 Canvas 自绘，不引入图片资源，PDF 体积几乎不增加。
 */
object GridPainter {

    fun draw(
        b: CanvasBackend,
        tp: TextPainter,
        grid: PaperGrid,
        g: PageGeometry,
        top: Float,
    ) {
        val left = g.margin
        val right = g.pageW - g.margin
        val width = right - left
        val bottom = g.contentBottomSafe
        val cell = if (grid.cell > 0f) grid.cell else grid.style.defaultCell

        val frame = tp.rulePaint(0.7f, 0xFF8A8A8A.toInt())
        val fine = tp.rulePaint(0.3f, 0xFFCFCFCF.toInt())
        // 田字格 / 米字格的内部辅助线：虚线、稍深一点，实线浅灰在纸上会「消失」
        val inner = tp.dashPaint()
        val axis = tp.rulePaint(1.1f, 0xFF555555.toInt())

        var y0 = top
        if (grid.hint.isNotEmpty()) {
            val p = tp.paint(9f, color = 0xFF666666.toInt())
            b.drawText(grid.hint, left, top + 12f, p)
            y0 = top + 22f
        }
        if (bottom <= y0) return

        when (grid.style) {
            GridStyle.TIANZI, GridStyle.MIZI -> {
                val cols = (width / cell).toInt().coerceAtLeast(1)
                val size = width / cols
                val rows = ((bottom - y0) / size).toInt().coerceAtLeast(1)
                for (row in 0 until rows) {
                    val t = y0 + row * size
                    for (col in 0 until cols) {
                        val l = left + col * size
                        b.drawRect(l, t, l + size, t + size, frame)
                        b.drawLine(l, t + size / 2f, l + size, t + size / 2f, inner)
                        b.drawLine(l + size / 2f, t, l + size / 2f, t + size, inner)
                        if (grid.style == GridStyle.MIZI) {
                            b.drawLine(l, t, l + size, t + size, inner)
                            b.drawLine(l + size, t, l, t + size, inner)
                        }
                    }
                }
            }

            GridStyle.FOUR_LINE -> {
                val h = cell
                val gap = 16f
                val groups = ((bottom - y0 + gap) / (h + gap)).toInt().coerceAtLeast(1)
                for (i in 0 until groups) {
                    val t = y0 + i * (h + gap)
                    for (k in 0..3) {
                        val y = t + k * h / 3f
                        b.drawLine(left, y, right, y, if (k == 0 || k == 3) frame else fine)
                    }
                    b.drawLine(left, t, left, t + h, fine)
                    b.drawLine(right, t, right, t + h, fine)
                }
            }

            GridStyle.SQUARE, GridStyle.COORDINATE -> {
                val cols = (width / cell).toInt().coerceAtLeast(1)
                val size = width / cols
                val rows = ((bottom - y0) / size).toInt().coerceAtLeast(1)
                val gridRight = left + cols * size
                val gridBottom = y0 + rows * size
                for (c in 0..cols) {
                    val x = left + c * size
                    b.drawLine(x, y0, x, gridBottom, fine)
                }
                for (r in 0..rows) {
                    val y = y0 + r * size
                    b.drawLine(left, y, gridRight, y, fine)
                }
                b.drawRect(left, y0, gridRight, gridBottom, frame)

                if (grid.style == GridStyle.COORDINATE) {
                    val cx = left + cols * size / 2f
                    val cy = y0 + rows * size / 2f
                    b.drawLine(left, cy, gridRight, cy, axis)
                    b.drawLine(cx, y0, cx, gridBottom, axis)
                    val p = tp.paint(7f, color = 0xFF777777.toInt())
                    for (k in -5..5) {
                        if (k == 0) continue
                        val x = cx + k * size
                        if (x > left && x < gridRight) {
                            b.drawText("$k", x - 3f, cy + 11f, p)
                        }
                        val y = cy - k * size
                        if (y > y0 && y < gridBottom) {
                            b.drawText("$k", cx + 4f, y + 3f, p)
                        }
                    }
                }
            }

            GridStyle.LINED -> {
                val rows = ((bottom - y0) / cell).toInt().coerceAtLeast(1)
                for (i in 0..rows) {
                    val y = y0 + i * cell
                    b.drawLine(left, y, right, y, if (i == 0 || i == rows) frame else fine)
                }
                b.drawLine(left, y0, left, y0 + rows * cell, frame)
            }

            GridStyle.DOT_GRID -> {
                val dot = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF9A9A9A.toInt()
                    style = Paint.Style.FILL
                }
                val cols = (width / cell).toInt().coerceAtLeast(1)
                val size = width / cols
                val rows = ((bottom - y0) / size).toInt().coerceAtLeast(1)
                for (r in 0..rows) {
                    for (c in 0..cols) {
                        val x = left + c * size
                        val y = y0 + r * size
                        b.drawRect(x - 0.8f, y - 0.8f, x + 0.8f, y + 0.8f, dot)
                    }
                }
            }

            // v0.5.0：主题识字卡走自己的绘制器，hint 已在上面统一扣掉高度
            GridStyle.THEME_CARD -> ThemeCardPainter.draw(b, tp, grid, g, y0)

            // v0.8.0：迷宫纸
            GridStyle.MAZE -> drawMaze(b, tp, left, right, y0, bottom, cell, grid.mazeSeed)
        }
    }

    /**
     * 迷宫纸：递归回溯（深度优先 + 回溯）生成，再按「没打通就画墙」画出来。
     *
     * 算法本身是公开的经典生成法，这里自己实现：
     *  - 每个格子记一个 4 位掩码，位 N/E/S/W 表示「这一面已经打通」；
     *  - 从左上角出发，随机挑一个没访问过的邻居打通并走过去；
     *  - 走不动就回退，直到所有格子都访问过 —— 这样得到的是**完美迷宫**
     *    （任意两点之间恰好一条路，一定有解、没有环）；
     *  - 入口在左上格外侧开口、出口在右下格外侧开口。
     *
     * [seed] 为 0 时用当前时间，保证「再点一次是新迷宫」。
     */
    private fun drawMaze(
        b: CanvasBackend,
        tp: TextPainter,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        cell: Float,
        seed: Long,
    ) {
        val width = right - left
        if (width <= 0f || bottom <= top) return
        val cols = (width / cell).toInt().coerceIn(4, 40)
        val size = width / cols
        val rows = ((bottom - top) / size).toInt().coerceIn(4, 60)
        val rng = Random(if (seed != 0L) seed else System.nanoTime())

        val count = cols * rows
        val open = IntArray(count) // 位掩码：1=N 2=E 4=S 8=W
        val visited = BooleanArray(count)
        val stack = ArrayDeque<Int>()

        visited[0] = true
        stack.addLast(0)
        while (stack.isNotEmpty()) {
            val cur = stack.last()
            val cx = cur % cols
            val cy = cur / cols
            val candidates = ArrayList<Int>(4)
            if (cy > 0 && !visited[cur - cols]) candidates.add(0)
            if (cx < cols - 1 && !visited[cur + 1]) candidates.add(1)
            if (cy < rows - 1 && !visited[cur + cols]) candidates.add(2)
            if (cx > 0 && !visited[cur - 1]) candidates.add(3)
            if (candidates.isEmpty()) {
                stack.removeLast()
                continue
            }
            when (candidates[rng.nextInt(candidates.size)]) {
                0 -> {
                    open[cur] = open[cur] or 1
                    open[cur - cols] = open[cur - cols] or 4
                    visited[cur - cols] = true
                    stack.addLast(cur - cols)
                }
                1 -> {
                    open[cur] = open[cur] or 2
                    open[cur + 1] = open[cur + 1] or 8
                    visited[cur + 1] = true
                    stack.addLast(cur + 1)
                }
                2 -> {
                    open[cur] = open[cur] or 4
                    open[cur + cols] = open[cur + cols] or 1
                    visited[cur + cols] = true
                    stack.addLast(cur + cols)
                }
                else -> {
                    open[cur] = open[cur] or 8
                    open[cur - 1] = open[cur - 1] or 2
                    visited[cur - 1] = true
                    stack.addLast(cur - 1)
                }
            }
        }

        val wall = tp.rulePaint(0.7f, 0xFF7A7A7A.toInt())
        val frame = tp.rulePaint(1.1f, 0xFF5A5A5A.toInt())

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                val x = left + c * size
                val y = top + r * size
                val mask = open[idx]
                // 外墙用粗线；入口（左上格外侧）与出口（右下格外侧）留口
                if (mask and 1 == 0) {
                    val isEntrance = r == 0 && c == 0
                    if (!isEntrance) b.drawLine(x, y, x + size, y, if (r == 0) frame else wall)
                }
                if (mask and 4 == 0) {
                    val isExit = r == rows - 1 && c == cols - 1
                    if (!isExit) {
                        b.drawLine(x, y + size, x + size, y + size, if (r == rows - 1) frame else wall)
                    }
                }
                if (mask and 8 == 0) {
                    b.drawLine(x, y, x, y + size, if (c == 0) frame else wall)
                }
                if (mask and 2 == 0) {
                    b.drawLine(x + size, y, x + size, y + size, if (c == cols - 1) frame else wall)
                }
            }
        }

        // 入口 / 出口不再印文字标注：hint 已经写明「左上进、右下出」，
        // 而标注画在迷宫框外侧会和上面那行提示叠在一起（第一版就叠了）。
    }
}
