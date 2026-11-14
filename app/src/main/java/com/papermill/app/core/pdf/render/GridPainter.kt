package com.papermill.app.core.pdf.render

import android.graphics.Paint
import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.PageGeometry
import com.papermill.app.core.pdf.TextPainter

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
        // 田字格 / 米字格的内部辅助线要能被看清，又不能抢过学生的字
        val inner = tp.rulePaint(0.4f, 0xFFADADAD.toInt())
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
        }
    }
}
