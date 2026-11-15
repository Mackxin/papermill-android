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
        // 中间层级：回宫格的内框、竖式格的格内辅助线用它。
        // 只有 frame / fine 两档时，内框和外框一样重、辅助线和格线一样重，
        // 整页看上去就是「一堆等重的线」，分不出哪条是格子边界。
        val mid = tp.rulePaint(0.45f, 0xFFB4B4B4.toInt())
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

            // ---------------- v0.15.0 通用纸张模板 ----------------
            GridStyle.BLANK -> Unit // 纯白纸：页眉页脚照常，正文什么都不画

            GridStyle.CORNELL -> drawCornell(b, tp, left, right, y0, bottom, cell, frame, fine)

            GridStyle.MUSIC -> drawMusic(b, left, right, y0, bottom, cell, frame)

            GridStyle.PINYIN_TIANZI ->
                drawPinyinTianzi(b, left, width, y0, bottom, cell, frame, fine, inner)

            GridStyle.VERTICAL_CALC ->
                drawVerticalCalc(b, left, right, y0, bottom, cell, frame, mid)

            GridStyle.NUMBER_LINE ->
                drawNumberLines(b, tp, left, right, y0, bottom, cell, axis, fine)

            GridStyle.DICTATION ->
                drawDictation(b, tp, left, right, y0, bottom, cell, fine)

            GridStyle.TIMETABLE ->
                drawTimetable(b, tp, left, right, y0, bottom, frame, fine)

            GridStyle.HUI_GONG, GridStyle.JIU_GONG ->
                drawCalligraphy(b, left, width, y0, bottom, cell, frame, fine, mid, inner, grid.style)

            GridStyle.MISTAKE ->
                drawMistakeSheet(b, tp, left, right, y0, bottom, frame, fine, mid)
        }
    }

    // ============================================================
    // v0.15.0 通用纸张模板
    // ============================================================
    // 全部 Canvas 自绘：这些是通行的基础版式（康奈尔、五线谱、拼音田字格、
    // 竖式格、数轴、听写纸…），只保留「版式长什么样、适合练什么」这一层，
    // 不含任何第三方模板站的图片、PDF 或字体。

    /** 小标签：右对齐到给定 x，字号 8pt 灰字。用于「线索 / 笔记 / 总结」这类分区名 */
    private fun label(
        b: CanvasBackend,
        tp: TextPainter,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
    ) {
        b.drawText(text, x, y, paint)
    }

    /**
     * 康奈尔笔记：左侧线索栏（约 28% 宽）+ 右侧笔记区 + 底部总结区（约 26% 高）。
     *
     * 为什么这三个区要按比例而不是固定值：A4 横版时可用宽高变化很大，
     * 写死 150pt 的总结区在横版上会占掉半页。
     *
     * v0.15.0 修了三个第一版画错的地方：
     *  1. 分区竖线一路画到了纸底，把总结区也切成了两半 —— 标准康奈尔里
     *     总结区是**通栏**的，竖线只该画到分界横线为止；
     *  2. 总结区一片空白，可它正是要写字的地方，补上通栏横线；
     *  3. 线索栏的横线自成一套等距，和右边笔记线对不齐，看着像两张纸
     *     叠在一起；改成「每两条笔记线画一条线索线」，严格对齐。
     */
    private fun drawCornell(
        b: CanvasBackend,
        tp: TextPainter,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        cell: Float,
        frame: Paint,
        fine: Paint,
    ) {
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return
        val cueW = width * 0.28f
        val split = top + height * 0.74f

        // 笔记区：从分区标签下面开始铺，行高取 cell（默认 26pt ≈ 9mm）
        val noteTop = top + 20f
        val noteRows = ((split - noteTop) / cell).toInt().coerceAtLeast(1)
        val step = (split - noteTop) / noteRows
        for (i in 0..noteRows) {
            val y = noteTop + i * step
            b.drawLine(left + cueW, y, right, y, fine)
        }
        // 线索栏和笔记区共用同一套 y：隔一行画一条，宽度刚好是一半
        var i = 0
        while (i <= noteRows) {
            val y = noteTop + i * step
            b.drawLine(left, y, left + cueW, y, fine)
            i += 2
        }

        // 总结区：通栏横线，一路铺到底
        var y = split + 20f + 26f
        while (y <= bottom) {
            b.drawLine(left, y, right, y, fine)
            y += 28f
        }

        // 竖线只到分界线，分界线本身用重一档的 frame
        b.drawLine(left + cueW, top, left + cueW, split, frame)
        b.drawLine(left, split, right, split, frame)

        val p = tp.paint(8f, color = 0xFF9A9A9A.toInt())
        label(b, tp, "线索 / 关键词", left + 5f, top + 13f, p)
        label(b, tp, "笔记", left + cueW + 5f, top + 13f, p)
        label(b, tp, "总结", left + 5f, split + 13f, p)
    }

    /** 五线谱：一组五条线（谱线间距 = cell/6），组间留白。默写 / 视唱抄谱用 */
    private fun drawMusic(
        b: CanvasBackend,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        cell: Float,
        frame: Paint,
    ) {
        val lineGap = cell / 6f
        val staffH = lineGap * 4
        val groupGap = cell * 0.55f
        var y = top + lineGap * 1.5f
        while (y + staffH <= bottom) {
            for (k in 0..4) {
                val ly = y + k * lineGap
                b.drawLine(left, ly, right, ly, frame)
            }
            y += staffH + groupGap + lineGap
        }
    }

    /** 拼音田字格：上方拼音四线格（高度约为格宽 45%）+ 下方田字格 */
    private fun drawPinyinTianzi(
        b: CanvasBackend,
        left: Float,
        width: Float,
        top: Float,
        bottom: Float,
        cell: Float,
        frame: Paint,
        fine: Paint,
        inner: Paint,
    ) {
        val cols = (width / cell).toInt().coerceAtLeast(1)
        val size = width / cols
        val pyH = size * 0.45f
        val blockH = pyH + size
        val rows = ((bottom - top) / blockH).toInt().coerceAtLeast(1)
        for (r in 0 until rows) {
            val t = top + r * blockH
            for (c in 0 until cols) {
                val l = left + c * size
                // 拼音：四条线围成三格，中间两条是细线
                for (k in 0..3) {
                    val y = t + pyH * k / 3f
                    b.drawLine(l, y, l + size, y, if (k == 0 || k == 3) frame else fine)
                }
                b.drawLine(l, t, l, t + pyH, fine)
                b.drawLine(l + size, t, l + size, t + pyH, fine)
                // 田字格
                val ty = t + pyH
                b.drawRect(l, ty, l + size, ty + size, frame)
                b.drawLine(l, ty + size / 2f, l + size, ty + size / 2f, inner)
                b.drawLine(l + size / 2f, ty, l + size / 2f, ty + size, inner)
            }
        }
    }

    /**
     * 竖式演算格：分列的大格，格内三条浅横线帮着对齐数位。
     *
     * 为什么要有格内横线：小学竖式的最大错误不是算错，是**数位没对齐**。
     * 空白的格子起不到引导作用，三条淡线能让人自然把个位落在一起。
     *
     * 格线分两档画：格子边界用 [frame]、格内三条辅助线用 [mid]。
     * 第一版全用同一支笔，结果 171×120 的格子里七条线等重，
     * 一眼看不出「一个格子写到哪儿结束」。
     */
    private fun drawVerticalCalc(
        b: CanvasBackend,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        cell: Float,
        frame: Paint,
        mid: Paint,
    ) {
        val width = right - left
        val cols = (width / (cell * 1.3f)).toInt().coerceIn(1, 4)
        val boxW = width / cols
        val rows = ((bottom - top) / cell).toInt().coerceAtLeast(1)
        // 行高把余数摊掉：cell=120 时 660pt 只能排 5 行、余 60pt，
        // 不留这半行空白，直接把 5 行均分成 132pt —— 整页正好铺满。
        val boxH = (bottom - top) / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val l = left + c * boxW
                val t = top + r * boxH
                b.drawRect(l, t, l + boxW, t + boxH, frame)
                for (k in 1..3) {
                    val y = t + boxH * k / 4f
                    b.drawLine(l + 8f, y, l + boxW - 8f, y, mid)
                }
            }
        }
        b.drawRect(left, top, left + cols * boxW, top + rows * boxH, frame)
    }

    /** 数轴练习纸：多条带箭头与等距刻度的空白数轴，标「0」起点 */
    private fun drawNumberLines(
        b: CanvasBackend,
        tp: TextPainter,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        cell: Float,
        axis: Paint,
        fine: Paint,
    ) {
        val rowH = cell
        val rows = ((bottom - top - 6f) / rowH).toInt().coerceAtLeast(1)
        val p = tp.paint(7.5f, color = 0xFF8A8A8A.toInt())
        for (r in 0 until rows) {
            val y = top + r * rowH + rowH * 0.62f
            b.drawLine(left, y, right - 9f, y, axis)
            // 箭头：两条短线，避免为此引 Path
            b.drawLine(right - 9f, y, right - 17f, y - 4f, axis)
            b.drawLine(right - 9f, y, right - 17f, y + 4f, axis)
            b.drawText("0", left - 2f, y - 5f, p)
            var x = left
            while (x < right - 26f) {
                b.drawLine(x, y - 3.5f, x, y + 3.5f, fine)
                x += 16f
            }
        }
    }

    /** 听写纸：编号 + 一条横线，一行一词 */
    private fun drawDictation(
        b: CanvasBackend,
        tp: TextPainter,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        cell: Float,
        fine: Paint,
    ) {
        val rows = ((bottom - top) / cell).toInt().coerceAtLeast(1)
        val p = tp.paint(9f, color = 0xFF8A8A8A.toInt())
        for (i in 0 until rows) {
            val base = top + i * cell + cell * 0.72f
            b.drawText("${i + 1}.", left, base, p)
            b.drawLine(left + 22f, base + 3f, right, base + 3f, fine)
        }
    }

    /** 课程表：表头（节次 / 周一…周五）+ 七节课 */
    private fun drawTimetable(
        b: CanvasBackend,
        tp: TextPainter,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        frame: Paint,
        fine: Paint,
    ) {
        val days = listOf("节次", "周一", "周二", "周三", "周四", "周五")
        val bodyRows = 7
        val totalRows = bodyRows + 1
        val colW = (right - left) / days.size
        val rowH = minOf((bottom - top) / totalRows, 46f)
        val p = tp.paint(10f, color = 0xFF333333.toInt())
        val small = tp.paint(9f, color = 0xFF666666.toInt())

        for (r in 0..totalRows) {
            val y = top + r * rowH
            // 表头下沿（r == 1）也加粗：7 节课和表头之间要有一条明确的界
            val heavy = r == 0 || r == 1 || r == totalRows
            b.drawLine(left, y, left + days.size * colW, y, if (heavy) frame else fine)
        }
        for (c in 0..days.size) {
            val x = left + c * colW
            b.drawLine(x, top, x, top + totalRows * rowH, if (c == 0 || c == days.size) frame else fine)
        }
        // 表头
        days.forEachIndexed { c, name ->
            val w = tp.measure(name, if (c == 0) small else p)
            b.drawText(name, left + c * colW + (colW - w) / 2f, top + rowH * 0.62f, if (c == 0) small else p)
        }
        // 节次
        for (r in 1..bodyRows) {
            val name = "第${r}节"
            val w = tp.measure(name, small)
            b.drawText(name, left + (colW - w) / 2f, top + (r + 0.62f) * rowH, small)
        }

        // 表格本身只有 8 行 × 46pt ≈ 368pt，一页还剩两百多 pt 空着。
        // 空着就是白费半张纸，补一块「本周作业 / 备注」横线区 —— 这恰好是
        // 家长真会往上写的那部分（作业项、要带的东西、临时调课）。
        val tableBottom = top + totalRows * rowH
        if (bottom - tableBottom > 60f) {
            b.drawText("本周作业 / 备注", left, tableBottom + 20f, p)
            var y = tableBottom + 30f
            while (y <= bottom) {
                b.drawLine(left, y, right, y, fine)
                y += 26f
            }
        }
    }

    /**
     * 回宫格 / 九宫格：练字的两种辅助格。
     *
     * 九宫格第一版还画了两条对角线，渲染出来整页是一格一个「✕」，
     * 看着像数学习题纸而不是写字格 —— 传统九宫格本来就只有 3×3 九个小格，
     * 对角线是米字格的东西，两者不该混。删掉对角线后线条少一半，
     * 每一笔该占哪一格反而看得更清。
     */
    private fun drawCalligraphy(
        b: CanvasBackend,
        left: Float,
        width: Float,
        top: Float,
        bottom: Float,
        cell: Float,
        frame: Paint,
        fine: Paint,
        mid: Paint,
        inner: Paint,
        style: GridStyle,
    ) {
        val cols = (width / cell).toInt().coerceAtLeast(1)
        val size = width / cols
        val rows = ((bottom - top) / size).toInt().coerceAtLeast(1)
        for (r in 0 until rows) {
            val t = top + r * size
            for (c in 0 until cols) {
                val l = left + c * size
                b.drawRect(l, t, l + size, t + size, frame)
                if (style == GridStyle.HUI_GONG) {
                    // 回宫格：内缩一个方框 + 内部十字，笔画起收落在内框边上。
                    // 内框用 mid（比外框轻一档），否则两个框一样粗，像画了双层格子。
                    // 内缩 22%：内框占整格的 56%，留出的四边正好让长横、长捺伸出去
                    val inset = size * 0.22f
                    b.drawRect(l + inset, t + inset, l + size - inset, t + size - inset, mid)
                    b.drawLine(l + size / 2f, t + inset, l + size / 2f, t + size - inset, inner)
                    b.drawLine(l + inset, t + size / 2f, l + size - inset, t + size / 2f, inner)
                } else {
                    // 九宫格：只有 3×3 的细分线，相邻格的线连成一片细网格，
                    // 每隔三道一条 frame —— 这正是传统九宫格纸的样子
                    for (k in 1..2) {
                        val x = l + size * k / 3f
                        val y = t + size * k / 3f
                        b.drawLine(x, t, x, t + size, fine)
                        b.drawLine(l, y, l + size, y, fine)
                    }
                }
            }
        }
    }

    /**
     * 错题整理纸：一条错题一个区块 —— 题干两行、错因一行、正解两行。
     *
     * 区块高度按「能放几个就均分」算，不是写死 132pt 再除以它：
     * 第一版写死 132pt，635pt 的可用高度除下来是 4.8 个 —— 取了 5 个，
     * 结果第 5 个区块的下半截直接画到了页脚外面（渲染图里能看到它被裁断）。
     * 现在先取整块数，再把剩余高度均分回去，既不越界也不在纸尾留一条空白。
     */
    private fun drawMistakeSheet(
        b: CanvasBackend,
        tp: TextPainter,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        frame: Paint,
        fine: Paint,
        mid: Paint,
    ) {
        val rows = ((bottom - top) / 132f).toInt().coerceAtLeast(1)
        val blockH = (bottom - top) / rows
        val p = tp.paint(8f, color = 0xFF9A9A9A.toInt())
        val title = tp.paint(9f, color = 0xFF666666.toInt())
        for (r in 0 until rows) {
            val t = top + r * blockH
            b.drawRect(left, t, right, t + blockH, mid)
            b.drawText("第 ${r + 1} 题", left + 6f, t + 13f, title)

            // 三段的纵向位置全部按区块高度的比例算 —— 均分后区块可能从 132 变成 158，
            // 写死的绝对偏移会把「正解」的最后一行挤到框线上。
            val stemY = t + blockH * 0.22f
            val causeY = t + blockH * 0.56f
            val fixY = t + blockH * 0.72f

            b.drawText("题干", left + 6f, stemY, p)
            b.drawLine(left + 34f, stemY + 4f, right - 8f, stemY + 4f, fine)
            b.drawLine(left + 34f, t + blockH * 0.44f, right - 8f, t + blockH * 0.44f, fine)

            b.drawText("错因", left + 6f, causeY, p)
            b.drawLine(left + 34f, causeY + 4f, right - 8f, causeY + 4f, fine)

            b.drawText("正解", left + 6f, fixY, p)
            b.drawLine(left + 34f, fixY + 4f, right - 8f, fixY + 4f, fine)
            b.drawLine(left + 34f, t + blockH * 0.93f, right - 8f, t + blockH * 0.93f, fine)
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
