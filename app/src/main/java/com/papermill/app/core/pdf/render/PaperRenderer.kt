package com.papermill.app.core.pdf.render

import android.graphics.pdf.PdfDocument
import com.papermill.app.core.pdf.CanvasBackend
import com.papermill.app.core.pdf.PageGeometry
import com.papermill.app.core.pdf.PdfBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.layout.Block
import com.papermill.app.core.pdf.layout.FlowLayout
import com.papermill.app.core.pdf.layout.blocks.FullWidthBlock
import com.papermill.app.core.pdf.layout.blocks.NoteBlock
import com.papermill.app.core.pdf.layout.blocks.QuestionBlock
import com.papermill.app.core.pdf.layout.blocks.SectionHeaderBlock
import com.papermill.app.core.pdf.layout.blocks.TableBlock
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 试卷渲染器：两阶段渲染。
 *
 * 阶段一：每个大题交给 [FlowLayout] 算出「哪一页、哪一栏、哪个 y」；
 * 阶段二：知道总页数之后，逐页 startPage → 画卷头/页眉 → 画每个块 → 画页脚 → finishPage。
 *
 * 为什么必须先知道总页数：页眉右侧要写「第 2 页 / 共 4 页」。
 * 单阶段（边排边画）永远写不出「共 y 页」。
 *
 * 不用第三方 PDF 库的理由（沿用同机已验证的判断）：
 * iText / PdfBox 动辄 5~10MB，还要处理 AGPL 商用授权；
 * 而 `android.graphics.pdf.PdfDocument` 是框架内置，零依赖、中文靠系统字体回退。
 *
 * v0.4.0：所有页面尺寸都从 [PageGeometry] 实例取，不再引用全局常量。
 * 竖版 595×842 / 横版 842×595，卷头、页眉、页脚、分页全部跟着自动适配。
 */
object PaperRenderer {

    private const val COLUMN_GAP = 20f

    private const val TITLE_SIZE = 17f
    private const val TITLE_H = 26f
    private const val SUB_SIZE = 9f
    private const val SUB_H = 17f
    private const val NAME_ROW_H = 26f
    private const val COVER_RULE_H = 10f
    private const val RUNNING_H = 24f

    fun render(doc: PaperDocument): RenderedPdf {
        // 练习纸走独立分支：固定一页，按剩余高度把格子铺满
        doc.sections.firstOrNull()?.grid?.let { return renderPracticeGrid(doc, it) }

        val tp = TextPainter()
        val g = PageGeometry.of(doc.orientation)

        // 首页被卷头占掉，后续页只有窄页眉 —— 两页可用高度不同
        val contentHFirst = g.contentBottomSafe - (g.margin + coverHeight(doc))
        val contentHOther = g.contentBottomSafe - (g.margin + RUNNING_H)

        // ---- 1) 整卷合并成两条流：正文一条、答案一条 ----
        // 旧版对每个大题各调一次 FlowLayout，导致「换个大题就换一页」：
        // 6 种题型的一年级卷被印成 7 页。现在所有大题的块进同一个流，
        // 标题 / 需要书写过程的题用整宽块占满一行，段与段在同一页里上下相接。
        val bodyBlocks = ArrayList<Block>()
        val answerBlocks = ArrayList<Block>()
        var bodyColumns = 2

        doc.sections.forEach { section ->
            val target = if (section.isAnswer) answerBlocks else bodyBlocks
            target.add(SectionHeaderBlock(section.title))

            val table = section.table
            if (table != null) {
                // 表格按固定行数切块，FlowLayout 就能自然分页且每块都带表头
                table.rows.chunked(table.rowsPerBlock.coerceAtLeast(1)).forEach { rows ->
                    target.add(TableBlock(table.headers, table.weights, rows, showHeader = true))
                }
                target.add(FullWidthBlock(NoteBlock("本表可直接打印张贴，题目均由本 App 整理编排。")))
            } else {
                section.questions.forEach { q ->
                    val qb = QuestionBlock(no = q.no, stem = q.stem, answerLines = section.answerLines)
                    target.add(if (section.answerLines > 0) FullWidthBlock(qb) else qb)
                }
                if (!section.isAnswer) bodyColumns = maxOf(bodyColumns, section.columns)
            }
        }

        val pages = ArrayList<FlowLayout.Page>()
        if (bodyBlocks.isNotEmpty()) {
            pages.addAll(
                FlowLayout(
                    contentW = g.contentW,
                    contentHFirst = contentHFirst,
                    contentHOther = contentHOther,
                    columns = bodyColumns,
                    columnGap = COLUMN_GAP,
                    startsOnFirstPage = true,
                ).layout(bodyBlocks, tp)
            )
        }

        // 答案页单独成流：本来就该另起一页，四栏小字更省纸
        var firstAnswerPage = -1
        if (answerBlocks.isNotEmpty()) {
            firstAnswerPage = pages.size
            pages.addAll(
                FlowLayout(
                    contentW = g.contentW,
                    contentHFirst = contentHFirst,
                    contentHOther = contentHOther,
                    columns = 4,
                    columnGap = COLUMN_GAP,
                    startsOnFirstPage = false,
                ).layout(answerBlocks, tp)
            )
        }

        val total = pages.size

        // 每页填充率：用来判断「最后一页只印了几行、白浪费一张纸」
        val fillRatios = pages.mapIndexed { index, page ->
            var bottom = 0f
            page.items.forEach { placed ->
                val h = placed.block.measure(tp, placed.w)
                bottom = maxOf(bottom, placed.y + h)
            }
            val cap = if (index == 0) contentHFirst else contentHOther
            if (cap <= 0f) 0f else (bottom / cap).coerceIn(0f, 1f)
        }
        val out = ByteArrayOutputStream()
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo
                    .Builder(g.pageW, g.pageH, index + 1)
                    .create()
                val pdfPage = pdf.startPage(info)
                val backend = PdfBackend(pdfPage.canvas)

                val contentTop = if (index == 0) {
                    drawCover(backend, tp, doc, g)
                } else {
                    drawRunningHeader(backend, tp, doc, g, index + 1, total)
                }

                page.items.forEach { placed ->
                    placed.block.paint(
                        backend,
                        tp,
                        g.margin + placed.x,
                        contentTop + placed.y,
                        placed.w,
                    )
                }

                drawFooter(backend, tp, doc, g)
                pdf.finishPage(pdfPage)
            }
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }

        return RenderedPdf(
            fileName = buildFileName(doc.title),
            bytes = out.toByteArray(),
            pageCount = total,
            pageFillRatios = fillRatios,
            contentPageCount = firstAnswerPage.takeIf { it > 0 } ?: total,
            orientation = doc.orientation,
        )
    }

    /** 练习纸：一页 A4，卷头 + 铺满的格子 + 页脚，天然 100% 利用纸张 */
    private fun renderPracticeGrid(doc: PaperDocument, grid: PaperGrid): RenderedPdf {
        val tp = TextPainter()
        val g = PageGeometry.of(doc.orientation)
        val out = ByteArrayOutputStream()
        val pdf = PdfDocument()
        try {
            val info = PdfDocument.PageInfo.Builder(g.pageW, g.pageH, 1).create()
            val page = pdf.startPage(info)
            val b = PdfBackend(page.canvas)
            val top = drawCover(b, tp, doc, g)
            GridPainter.draw(b, tp, grid, g, top)
            drawFooter(b, tp, doc, g)
            pdf.finishPage(page)
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
        return RenderedPdf(
            fileName = buildFileName(doc.title),
            bytes = out.toByteArray(),
            pageCount = 1,
            pageFillRatios = listOf(1f),
            contentPageCount = 1,
            orientation = doc.orientation,
        )
    }

    // ------------------------------------------------------------------ 卷头

    private fun coverHeight(doc: PaperDocument): Float {
        var h = TITLE_H
        if (doc.subtitle.isNotEmpty()) h += SUB_H
        if (doc.showNameRow) h += NAME_ROW_H
        return h + COVER_RULE_H
    }

    /** 画卷头，返回正文起始 y（含页边距） */
    private fun drawCover(b: CanvasBackend, tp: TextPainter, doc: PaperDocument, g: PageGeometry): Float {
        var y = g.margin

        val titlePaint = tp.paint(TITLE_SIZE, bold = true)
        val titleW = tp.measure(doc.title, titlePaint)
        b.drawText(
            doc.title,
            (g.pageW - titleW) / 2f,
            y + tp.baselineOffset(titlePaint, TITLE_H),
            titlePaint,
        )
        y += TITLE_H

        if (doc.subtitle.isNotEmpty()) {
            val subPaint = tp.paint(SUB_SIZE, color = 0xFF555555.toInt())
            val subW = tp.measure(doc.subtitle, subPaint)
            b.drawText(
                doc.subtitle,
                (g.pageW - subW) / 2f,
                y + tp.baselineOffset(subPaint, SUB_H),
                subPaint,
            )
            y += SUB_H
        }

        if (doc.showNameRow) {
            val labelPaint = tp.paint(10.5f)
            val baseline = y + tp.baselineOffset(labelPaint, NAME_ROW_H)
            val rule = tp.rulePaint(0.8f, 0xFF333333.toInt())
            var x = g.margin

            fun field(text: String, lineW: Float) {
                b.drawText(text, x, baseline, labelPaint)
                x += tp.measure(text, labelPaint) + 3f
                b.drawLine(x, baseline + 2f, x + lineW, baseline + 2f, rule)
                x += lineW + 16f
            }

            field("姓名", 62f)
            field("班级", 52f)
            field("日期", 56f)

            b.drawText("得分", x, baseline, labelPaint)
            x += tp.measure("得分", labelPaint) + 6f
            repeat(3) {
                b.drawRect(x, baseline - 10f, x + 11f, baseline + 2f, rule)
                x += 13f
            }
            y += NAME_ROW_H
        }

        // 双线：粗线 + 细线，是国内试卷的通用「卷头分界」
        b.drawLine(
            g.margin, y + 2f,
            g.pageW - g.margin, y + 2f,
            tp.rulePaint(1f, 0xFF222222.toInt()),
        )
        b.drawLine(
            g.margin, y + 5f,
            g.pageW - g.margin, y + 5f,
            tp.rulePaint(0.5f, 0xFFAAAAAA.toInt()),
        )
        y += COVER_RULE_H
        return y
    }

    // ------------------------------------------------------------------ 页眉页脚

    private fun drawRunningHeader(
        b: CanvasBackend,
        tp: TextPainter,
        doc: PaperDocument,
        g: PageGeometry,
        pageNo: Int,
        total: Int,
    ): Float {
        val p = tp.paint(8.5f, color = 0xFF555555.toInt())
        val baseline = g.margin + tp.baselineOffset(p, RUNNING_H)

        b.drawText(doc.title, g.margin, baseline, p)

        val right = "第 $pageNo 页 / 共 $total 页"
        val rightW = tp.measure(right, p)
        b.drawText(right, g.pageW - g.margin - rightW, baseline, p)

        b.drawLine(
            g.margin, g.margin + RUNNING_H - 5f,
            g.pageW - g.margin, g.margin + RUNNING_H - 5f,
            tp.rulePaint(0.5f, 0xFFBBBBBB.toInt()),
        )
        return g.margin + RUNNING_H
    }

    private fun drawFooter(
        b: CanvasBackend,
        tp: TextPainter,
        doc: PaperDocument,
        g: PageGeometry,
    ) {
        val bottom = g.pageH - g.margin
        b.drawLine(
            g.margin, bottom - 12f,
            g.pageW - g.margin, bottom - 12f,
            tp.rulePaint(0.5f, 0xFFDDDDDD.toInt()),
        )
        val p = tp.paint(7.5f, color = 0xFF999999.toInt())
        val w = tp.measure(doc.footerText, p)
        b.drawText(doc.footerText, (g.pageW - w) / 2f, bottom - 4f, p)
    }

    // ------------------------------------------------------------------ 文件名

    private fun buildFileName(title: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").take(24)
        return "卷子_${safe}_$stamp.pdf"
    }
}
