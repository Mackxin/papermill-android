package com.papermill.app.core.pdf.figure

import android.graphics.Paint
import android.graphics.Path
import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.domain.generate.FigureKind
import com.papermill.app.domain.generate.FigureSpec

/**
 * 看图题的配图绘制器：把 [FigureSpec] 这份纯数据画成矢量图形。
 *
 * ============================================================
 * 为什么是矢量而不是图片
 * ============================================================
 * 一张 300dpi 的钟面 PNG 约 60~120KB，一份卷子十道看图题就多出 1MB；
 * 而整页文字才几 KB。用 Canvas 现画：一条圆弧、两根指针、十二个刻度，
 * PDF 里只增加几百字节，放大到 A3 打印也不会糊。
 *
 * 更要紧的是**比例可以跟着栏宽走** —— 图片是固定像素的，塞进半栏要么被
 * 压扁要么留白；矢量图按宽度算半径，1 栏和 2 栏都好看。
 *
 * ============================================================
 * 高度怎么来
 * ============================================================
 * [height] 和 [paint] 必须**用同一套公式**算尺寸，否则排版量出一个高度、
 * 画的时候却溢出几 pt，压在下一道题上。所以两边都调用 [layoutOf]，
 * 拿到同一个 [Layout] 再各自使用 —— 复制粘贴两份尺寸计算迟早会走偏。
 */
object FigurePainter {

    /** 图形外接盒的边长（pt）。14pt ≈ 5mm，一年级课本上的图形大约就这么大 */
    private const val SHAPE_SIZE = 14f

    /** 每行图形的行高：图形本身 + 上下呼吸 */
    private const val ROW_H = 26f

    /**
     * 尺寸算好之后的中间结果，[height] 与 [paint] 共用。
     *
     * @param w 实际占用的宽度（可能小于给定宽度，比如钟面只占一角）
     * @param h 实际占用的高度
     */
    private data class Layout(val w: Float, val h: Float, val extra: Float = 0f)

    fun height(spec: FigureSpec, tp: TextPainter, width: Float): Float = layoutOf(spec, width).h

    private fun layoutOf(spec: FigureSpec, width: Float): Layout = when (spec.kind) {
        // 每组一行；一行最多 10 个，够数就不用再折行
        FigureKind.COUNTING -> Layout(width, spec.groups.size * ROW_H + 4f)

        // 十格阵：宽的一侧最多 210pt（再宽就像一张表而不是教具），高永远是两格
        FigureKind.TEN_FRAME -> {
            val w = minOf(width * 0.62f, 210f)
            Layout(w, w / 5f * 2f + 6f)
        }

        // 钟面给一个不太大的固定半径：太大像挂钟，太小看不见刻度
        FigureKind.CLOCK -> {
            val d = minOf(width * 0.26f, 112f)
            Layout(d, d + 10f)
        }

        // 统计图：绘图区 + 底部文字标签
        FigureKind.BAR_CHART -> Layout(minOf(width * 0.66f, 280f), BAR_H + 16f)

        // 两组：一行物品 + 中间分隔线
        FigureKind.TWO_GROUPS -> Layout(width, 44f)
    }

    fun paint(b: CanvasBackend, tp: TextPainter, spec: FigureSpec, x: Float, y: Float, width: Float) {
        val box = layoutOf(spec, width)
        when (spec.kind) {
            FigureKind.COUNTING -> paintCounting(b, tp, spec, x, y)
            FigureKind.TEN_FRAME -> paintTenFrame(b, tp, spec, x, y, box.w)
            FigureKind.CLOCK -> paintClock(b, tp, spec, x, y, box.w)
            FigureKind.BAR_CHART -> paintBarChart(b, tp, spec, x, y, box.w)
            FigureKind.TWO_GROUPS -> paintTwoGroups(b, tp, spec, x, y, box.w)
        }
    }

    // ---------------------------------------------------------------- 各种图

    /**
     * 看图数一数：每组一行。
     *
     * 一行的排法照教材：**满 5 个就先停一下再继续**（5 个一组是人类一眼能数清的
     * 上限，这就是「五个五个地数」的由来）。10 个挤成一排均匀铺开，学生反而
     * 得一个一个点着数，一眼看不出「比 5 多几个」。
     */
    private fun paintCounting(b: CanvasBackend, tp: TextPainter, spec: FigureSpec, x: Float, y: Float) {
        val gap = SHAPE_SIZE * 1.6f
        // 五五之间的空隙必须**明显大于**普通间距。第一版取 1.5 倍图形宽度（21pt），
        // 比普通间距 27pt 还小，渲染出来完全看不出「5 个一停」——
        // 分组等于没分，一年级还是得一个个点着数。
        val fiveGap = gap * 1.8f
        spec.groups.forEachIndexed { row, count ->
            val kind = spec.shapes.getOrElse(row) { 0 }
            val cy = y + row * ROW_H + SHAPE_SIZE / 2f + 3f
            // 左对齐开始画：几行数量不同时左边缘对齐，才看得出「哪行多」
            var cx = x + SHAPE_SIZE / 2f
            repeat(count.coerceIn(0, 20)) { i ->
                drawShape(b, tp, kind, cx, cy, SHAPE_SIZE / 2f)
                cx += if (i == 4) fiveGap else gap
            }
        }
    }

    /**
     * 十格阵：2 行 × 5 格，前 [n] 格画实心圆点。
     *
     * 这是「凑十法」的核心教具：格子固定 10 个，填了几个、还差几个空位，
     * 一眼就能看出来 —— 这正是 8 + 5 要「先凑成 10」的直觉来源。
     */
    private fun paintTenFrame(b: CanvasBackend, tp: TextPainter, spec: FigureSpec, x: Float, y: Float, w: Float) {
        val n = (spec.groups.firstOrNull() ?: 0).coerceIn(0, 10)
        val cell = w / 5f
        val frame = tp.rulePaint(0.8f, 0xFF6E6E6E.toInt())
        val dot = fillPaint(0xFF4A4A4A.toInt())
        // 外框 + 中缝
        b.drawRect(x, y, x + w, y + cell * 2f, frame)
        b.drawLine(x, y + cell, x + w, y + cell, frame)
        for (c in 1 until 5) {
            b.drawLine(x + c * cell, y, x + c * cell, y + cell * 2f, frame)
        }
        // 圆点：从左到右、先上排后下排（和数数的方向一致）
        for (i in 0 until n) {
            val r = i / 5
            val c = i % 5
            b.drawCircle(x + c * cell + cell / 2f, y + r * cell + cell / 2f, cell * 0.30f, dot)
        }
    }

    /**
     * 钟面：圆 + 1~12 的数字 + 12 个刻度 + 时针分针。
     *
     * **必须印数字**。第一版只画刻度不画数字，看着像一块手表盘 ——
     * 二年级认钟面靠的就是「短针指着几就是几时」，没有数字这道题根本没法做。
     *
     * 指针按**真实角度**算（时针要跟着分钟走一点点，8:30 的时针指在 8 和 9 正中间），
     * 不这么画的话「8 时 30 分」的时针会正对 8，学生照着读成 8 时整也说得通，
     * 这道题就白出了。
     */
    private fun paintClock(b: CanvasBackend, tp: TextPainter, spec: FigureSpec, x: Float, y: Float, d: Float) {
        val hour = (spec.groups.getOrElse(0) { 9 }) % 12
        val minute = spec.groups.getOrElse(1) { 0 }
        val r = d / 2f
        val cx = x + r + 1f
        val cy = y + r + 3f

        val rim = tp.rulePaint(1.1f, 0xFF555555.toInt())
        val tick = tp.rulePaint(0.7f, 0xFF888888.toInt())
        val hourHand = tp.rulePaint(2.6f, 0xFF333333.toInt())
        val minHand = tp.rulePaint(1.4f, 0xFF333333.toInt())
        // 数字半径 0.70r、字号 0.13r：再往外（0.74r）时「10」「11」「12」这三个
        // 两位数会互相顶到一起，还会压到外侧的刻度线 —— 一格 30° 的弦长只有 20pt，
        // 两个两位数并排就要 22pt 以上
        val num = tp.paint((r * 0.13f).coerceIn(5.5f, 8f), color = 0xFF333333.toInt())

        b.drawCircle(cx, cy, r, rim)
        // 刻度画在数字外侧一圈：3/6/9/12 点方向长一点
        for (k in 0 until 12) {
            val rad = Math.toRadians((k * 30f - 90f).toDouble())
            val long = k % 3 == 0
            val inner = r * if (long) 0.90f else 0.94f
            b.drawLine(
                cx + (inner * Math.cos(rad)).toFloat(),
                cy + (inner * Math.sin(rad)).toFloat(),
                cx + (r * 0.99f * Math.cos(rad)).toFloat(),
                cy + (r * 0.99f * Math.sin(rad)).toFloat(),
                tick,
            )
        }
        // 1~12：12 在正上方，顺时针每 30° 一个
        for (h in 1..12) {
            val rad = Math.toRadians((h * 30f - 90f).toDouble())
            val tx = cx + (r * 0.70f * Math.cos(rad)).toFloat()
            val ty = cy + (r * 0.70f * Math.sin(rad)).toFloat()
            val text = "$h"
            b.drawText(text, tx - tp.measure(text, num) / 2f, ty + tp.measure(text, num) * 0.34f, num)
        }
        // 时针：把分钟折算成小时的小数部分，指针才会停在两格之间
        val hRad = Math.toRadians(((hour + minute / 60f) * 30f - 90f).toDouble())
        b.drawLine(
            cx, cy,
            cx + (r * 0.50f * Math.cos(hRad)).toFloat(),
            cy + (r * 0.50f * Math.sin(hRad)).toFloat(),
            hourHand,
        )
        val mRad = Math.toRadians((minute * 6f - 90f).toDouble())
        b.drawLine(
            cx, cy,
            cx + (r * 0.80f * Math.cos(mRad)).toFloat(),
            cy + (r * 0.80f * Math.sin(mRad)).toFloat(),
            minHand,
        )
        b.drawCircle(cx, cy, 2.2f, fillPaint(0xFF333333.toInt()))
    }

    /** 统计图绘图区高度（不含底部标签） */
    private const val BAR_H = 96f

    /**
     * 条形统计图：纵轴 + 4 条柱子 + 刻度值。
     *
     * 柱高按「最大值 = 绘图区高度」等比缩放，所以纵轴刻度是真值（不是百分比），
     * 学生能直接读出「苹果比香蕉多几个」。
     */
    private fun paintBarChart(b: CanvasBackend, tp: TextPainter, spec: FigureSpec, x: Float, y: Float, w: Float) {
        if (spec.values.isEmpty()) return
        val axis = tp.rulePaint(0.9f, 0xFF6E6E6E.toInt())
        val grid = tp.rulePaint(0.3f, 0xFFC8C8C8.toInt())
        val bar = fillPaint(0xFFB9C6D4.toInt())
        val barLine = tp.rulePaint(0.6f, 0xFF6E6E6E.toInt())
        val labelPaint = tp.paint(8f, color = 0xFF555555.toInt())
        val scalePaint = tp.paint(7f, color = 0xFF8A8A8A.toInt())

        val plotLeft = x + 22f
        val plotRight = x + w
        val plotTop = y + 4f
        val plotBottom = y + BAR_H
        val maxV = spec.values.max().coerceAtLeast(1)
        // 纵轴取「整齐的刻度」：最大值向上取到 4 的倍数，刻度正好 4 段
        val step = ((maxV + 3) / 4).coerceAtLeast(1)
        val top = step * 4

        // 横向网格 + 刻度值
        for (i in 0..4) {
            val gy = plotBottom - (plotBottom - plotTop) * i / 4f
            b.drawLine(plotLeft, gy, plotRight, gy, if (i == 0) axis else grid)
            if (i > 0) {
                val text = "${step * i}"
                b.drawText(text, plotLeft - tp.measure(text, scalePaint) - 3f, gy + 2.5f, scalePaint)
            }
        }
        b.drawLine(plotLeft, plotTop, plotLeft, plotBottom, axis)

        val n = minOf(spec.values.size, 6)
        val slot = (plotRight - plotLeft) / n
        val barW = minOf(slot * 0.52f, 34f)
        for (i in 0 until n) {
            val v = spec.values.getOrElse(i) { 0 }
            val cx = plotLeft + slot * i + slot / 2f
            val bh = (plotBottom - plotTop) * v / top.toFloat()
            if (v > 0) {
                b.drawRect(cx - barW / 2f, plotBottom - bh, cx + barW / 2f, plotBottom, bar)
                b.drawRect(cx - barW / 2f, plotBottom - bh, cx + barW / 2f, plotBottom, barLine)
            }
            // 柱顶标数值：图上一眼能读到数，不用回头数格子
            val vs = "$v"
            b.drawText(vs, cx - tp.measure(vs, scalePaint) / 2f, plotBottom - bh - 3f, scalePaint)
            val name = spec.labels.getOrElse(i) { "" }
            b.drawText(name, cx - tp.measure(name, labelPaint) / 2f, plotBottom + 12f, labelPaint)
        }
        if (spec.unit.isNotEmpty()) {
            // 单位浮在绘图区右上方；贴在 plotTop 那条线上会和最上面一个刻度值撞
            val u = "（${spec.unit}）"
            b.drawText(u, plotRight - tp.measure(u, scalePaint), plotTop - 3f, scalePaint)
        }
    }

    /**
     * 看图列式：左边一组、分隔线、右边一组。
     *
     * 中间那条竖虚线是关键 —— 没有它两组物品糊成一片，学生分不清
     * 「原来 5 个、又来了 3 个」，加法式子就列不出来。
     */
    private fun paintTwoGroups(b: CanvasBackend, tp: TextPainter, spec: FigureSpec, x: Float, y: Float, w: Float) {
        val left = spec.groups.getOrElse(0) { 0 }.coerceIn(0, 10)
        val right = spec.groups.getOrElse(1) { 0 }.coerceIn(0, 10)
        val kind = spec.shapes.firstOrNull() ?: 0
        val cy = y + 20f
        val gap = SHAPE_SIZE * 1.7f
        val divider = tp.dashPaint(0.6f, 0xFFAAAAAA.toInt(), 3f, 3f)
        val midX = x + w / 2f

        // 两组各自在**自己那半张**里居中。原来右组从分隔线右边直接开画，
        // 左边 6 个、右边 2 个时右半张几乎全空，看着像「还没画完」
        fun drawGroup(count: Int, from: Float, to: Float) {
            if (count <= 0) return
            val span = (count - 1) * gap + SHAPE_SIZE
            var cx = from + ((to - from) - span) / 2f + SHAPE_SIZE / 2f
            repeat(count) {
                drawShape(b, tp, kind, cx, cy, SHAPE_SIZE / 2f)
                cx += gap
            }
        }
        drawGroup(left, x, midX - 8f)
        b.drawLine(midX, y + 6f, midX, y + 34f, divider)
        drawGroup(right, midX + 8f, x + w)
    }

    // ---------------------------------------------------------------- 小工具

    private fun fillPaint(color: Int): Paint = Paint().apply {
        isAntiAlias = true
        this.color = color
        style = Paint.Style.FILL
    }

    /** 按编号画一种图形：0 圆 1 三角 2 方块 3 五角星 */
    private fun drawShape(b: CanvasBackend, tp: TextPainter, kind: Int, cx: Float, cy: Float, r: Float) {
        val stroke = tp.rulePaint(1.0f, 0xFF555555.toInt())
        when (kind.mod(4)) {
            0 -> b.drawCircle(cx, cy, r, stroke)
            1 -> {
                val p = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r * 0.95f, cy + r * 0.72f)
                    lineTo(cx - r * 0.95f, cy + r * 0.72f)
                    close()
                }
                b.drawPath(p, stroke)
            }
            2 -> b.drawRect(cx - r * 0.88f, cy - r * 0.88f, cx + r * 0.88f, cy + r * 0.88f, stroke)
            else -> b.drawPath(starPath(cx, cy, r), stroke)
        }
    }

    /** 五角星：外半径 r、内半径 0.42r，10 个顶点逐个连起来 */
    private fun starPath(cx: Float, cy: Float, r: Float): Path {
        val p = Path()
        val inner = r * 0.42f
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else inner
            val deg = (-90 + i * 36).toDouble()
            val rad = Math.toRadians(deg)
            val px = cx + (radius * Math.cos(rad)).toFloat()
            val py = cy + (radius * Math.sin(rad)).toFloat()
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
        return p
    }
}
