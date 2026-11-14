package com.papermill.app.core.pdf.render

import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.PageGeometry
import com.papermill.app.core.pdf.TextPainter
import kotlin.math.ceil
import kotlin.math.min

/**
 * 主题识字卡 / 默写格绘制：拼音在上、田字格在下，一页 A4 按词数自动铺满。
 *
 * ============================================================
 * 版式与版权（v0.5.0）
 * ============================================================
 * 只借鉴「拼音在上、汉字在下」这个通用识字卡版式思路，没有复制任何第三方
 * 词表、图片、代码或提示词文本。词语是日常名词、拼音是读音事实，
 * 这里用的 [PaperGrid.themeCards] 来自 App 内自编并逐条校对拼音的 ThemeBank。
 *
 * 两种纸共用同一套布局：
 *  - showThemeWords = true  → 田字格里印浅灰范字，供描红（识字卡）
 *  - showThemeWords = false → 只给拼音、格子留空，供默写（默写格）
 *
 * 和 [GridPainter] 一样全部用 Canvas 自绘线条，不引图片、不打包字体。
 */
object ThemeCardPainter {

    fun draw(
        b: CanvasBackend,
        tp: TextPainter,
        grid: PaperGrid,
        g: PageGeometry,
        top: Float,
    ) {
        val cards = grid.themeCards
        if (cards.isEmpty()) return

        val left = g.margin
        val width = g.contentW
        val bottom = g.contentBottomSafe
        val availH = bottom - top
        if (availH <= 40f) return

        // 词数决定行数，再把整页高度平均分给每一行 —— 词多就紧凑、词少就写大字，
        // 无论哪个主题都不会出现「半页空白」，符合「打印出完整一页纸」的硬目标。
        val cols = if (g.pageW > g.pageH) 4 else 3
        val rows = ceil(cards.size / cols.toFloat()).toInt().coerceAtLeast(1)
        val cardH = availH / rows
        val gapX = 10f
        val cardW = (width - gapX * (cols - 1)) / cols

        val pinyinH = 17f
        val boxGap = 3f
        // 田字格最大 46pt：再大就不再是「格」，而是一整块方框了
        val boxH = min(46f, (cardH - pinyinH - 18f).coerceAtLeast(22f))

        val frame = tp.rulePaint(0.7f, 0xFF8A8A8A.toInt())
        val inner = tp.rulePaint(0.35f, 0xFFC4C4C4.toInt())
        val rowLine = tp.rulePaint(0.4f, 0xFFE2E2E2.toInt())
        val writeLine = tp.rulePaint(0.4f, 0xFFD8D8D8.toInt())
        val pinyinPaint = tp.paint(9f, color = 0xFF555555.toInt())
        // 范字要足够浅：孩子用铅笔写上去能盖住，又要看得清间架结构
        val modelPaint = tp.paint(boxH * 0.58f, color = 0xFFB8B8B8.toInt())

        cards.forEachIndexed { index, (pinyin, word) ->
            val r = index / cols
            val c = index % cols
            val cx = left + c * (cardW + gapX)
            val cy = top + r * cardH

            val pw = tp.measure(pinyin, pinyinPaint)
            b.drawText(
                pinyin,
                cx + (cardW - pw) / 2f,
                cy + tp.baselineOffset(pinyinPaint, pinyinH),
                pinyinPaint,
            )

            // 一个词几个字就画几个田字格，四字词自动收窄，不会挤成一团
            val n = word.length.coerceAtLeast(1)
            val cell = min(boxH, (cardW - boxGap * (n - 1)) / n)
            val totalW = cell * n + boxGap * (n - 1)
            val sx = cx + (cardW - totalW) / 2f
            val sy = cy + pinyinH + 2f

            for (k in 0 until n) {
                val x = sx + k * (cell + boxGap)
                b.drawRect(x, sy, x + cell, sy + cell, frame)
                b.drawLine(x, sy + cell / 2f, x + cell, sy + cell / 2f, inner)
                b.drawLine(x + cell / 2f, sy, x + cell / 2f, sy + cell, inner)
                if (grid.showThemeWords && k < word.length) {
                    val ch = word[k].toString()
                    val cw = tp.measure(ch, modelPaint)
                    b.drawText(
                        ch,
                        x + (cell - cw) / 2f,
                        sy + tp.baselineOffset(modelPaint, cell),
                        modelPaint,
                    )
                }
            }

            // 田字格下方再留一条淡横线：照着范字独立写一遍，一页纸不浪费
            val lineY = sy + cell + 12f
            if (lineY < cy + cardH - 4f) {
                b.drawLine(cx + 6f, lineY, cx + cardW - 6f, lineY, writeLine)
            }
        }

        // 行间淡分隔线，十几个词卡不糊成一片
        for (r in 1 until rows) {
            val y = top + r * cardH
            b.drawLine(left, y, left + width, y, rowLine)
        }
    }
}
