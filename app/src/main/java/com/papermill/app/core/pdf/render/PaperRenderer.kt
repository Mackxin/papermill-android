package com.papermill.app.core.pdf.render

import android.graphics.pdf.PdfDocument
import com.papermill.app.core.pdf.PageGeometry
import com.papermill.app.core.pdf.PdfBackend
import com.papermill.app.core.pdf.TextPainter
import com.papermill.app.core.pdf.layout.Block
import com.papermill.app.core.pdf.layout.FlowLayout
import com.papermill.app.core.pdf.layout.blocks.QuestionBlock
import com.papermill.app.core.pdf.layout.blocks.SectionHeaderBlock
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
        val tp = TextPainter()

        // 首页被卷头占掉，后续页只有窄页眉 —— 两页可用高度不同
        val contentHFirst = PageGeometry.CONTENT_BOTTOM_SAFE - (PageGeometry.MARGIN + coverHeight(doc))
        val contentHOther = PageGeometry.CONTENT_BOTTOM_SAFE - (PageGeometry.MARGIN + RUNNING_H)

        val pages = ArrayList<FlowLayout.Page>()
        doc.sections.forEach { section ->
            val blocks = ArrayList<Block>(section.questions.size + 1)
            blocks.add(SectionHeaderBlock(section.title))
            section.questions.forEach { q ->
                blocks.add(QuestionBlock(no = q.no, stem = q.stem, answerLines = section.answerLines))
            }
            pages.addAll(
                FlowLayout(
                    contentW = PageGeometry.CONTENT_W,
                    contentHFirst = contentHFirst,
                    contentHOther = contentHOther,
                    columns = section.columns,
                    columnGap = COLUMN_GAP,
                    // 只有整卷第 1 页才有卷头；第 2 个大题另起页时按「后续页」的高度排
                    startsOnFirstPage = pages.isEmpty(),
                ).layout(blocks, tp)
            )
        }

        val total = pages.size
        val out = ByteArrayOutputStream()
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo
                    .Builder(PageGeometry.PAGE_W, PageGeometry.PAGE_H, index + 1)
                    .create()
                val pdfPage = pdf.startPage(info)
                val backend = PdfBackend(pdfPage.canvas)

                val contentTop = if (index == 0) {
                    drawCover(backend, tp, doc)
                } else {
                    drawRunningHeader(backend, tp, doc, index + 1, total)
                }

                page.items.forEach { placed ->
                    placed.block.paint(
                        backend,
                        tp,
                        PageGeometry.MARGIN + placed.x,
                        contentTop + placed.y,
                        placed.w,
                    )
                }

                drawFooter(backend, tp, doc)
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
    private fun drawCover(b: com.papermill.app.core.pdf.CanvasBackend, tp: TextPainter, doc: PaperDocument): Float {
        var y = PageGeometry.MARGIN

        val titlePaint = tp.paint(TITLE_SIZE, bold = true)
        val titleW = tp.measure(doc.title, titlePaint)
        b.drawText(
            doc.title,
            (PageGeometry.PAGE_W - titleW) / 2f,
            y + tp.baselineOffset(titlePaint, TITLE_H),
            titlePaint,
        )
        y += TITLE_H

        if (doc.subtitle.isNotEmpty()) {
            val subPaint = tp.paint(SUB_SIZE, color = 0xFF555555.toInt())
            val subW = tp.measure(doc.subtitle, subPaint)
            b.drawText(
                doc.subtitle,
                (PageGeometry.PAGE_W - subW) / 2f,
                y + tp.baselineOffset(subPaint, SUB_H),
                subPaint,
            )
            y += SUB_H
        }

        if (doc.showNameRow) {
            val labelPaint = tp.paint(10.5f)
            val baseline = y + tp.baselineOffset(labelPaint, NAME_ROW_H)
            val rule = tp.rulePaint(0.8f, 0xFF333333.toInt())
            var x = PageGeometry.MARGIN

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
            PageGeometry.MARGIN, y + 2f,
            PageGeometry.PAGE_W - PageGeometry.MARGIN, y + 2f,
            tp.rulePaint(1f, 0xFF222222.toInt()),
        )
        b.drawLine(
            PageGeometry.MARGIN, y + 5f,
            PageGeometry.PAGE_W - PageGeometry.MARGIN, y + 5f,
            tp.rulePaint(0.5f, 0xFFAAAAAA.toInt()),
        )
        y += COVER_RULE_H
        return y
    }

    // ------------------------------------------------------------------ 页眉页脚

    private fun drawRunningHeader(
        b: com.papermill.app.core.pdf.CanvasBackend,
        tp: TextPainter,
        doc: PaperDocument,
        pageNo: Int,
        total: Int,
    ): Float {
        val p = tp.paint(8.5f, color = 0xFF555555.toInt())
        val baseline = PageGeometry.MARGIN + tp.baselineOffset(p, RUNNING_H)

        b.drawText(doc.title, PageGeometry.MARGIN, baseline, p)

        val right = "第 $pageNo 页 / 共 $total 页"
        val rightW = tp.measure(right, p)
        b.drawText(right, PageGeometry.PAGE_W - PageGeometry.MARGIN - rightW, baseline, p)

        b.drawLine(
            PageGeometry.MARGIN, PageGeometry.MARGIN + RUNNING_H - 5f,
            PageGeometry.PAGE_W - PageGeometry.MARGIN, PageGeometry.MARGIN + RUNNING_H - 5f,
            tp.rulePaint(0.5f, 0xFFBBBBBB.toInt()),
        )
        return PageGeometry.MARGIN + RUNNING_H
    }

    private fun drawFooter(
        b: com.papermill.app.core.pdf.CanvasBackend,
        tp: TextPainter,
        doc: PaperDocument,
    ) {
        val bottom = PageGeometry.PAGE_H - PageGeometry.MARGIN
        b.drawLine(
            PageGeometry.MARGIN, bottom - 12f,
            PageGeometry.PAGE_W - PageGeometry.MARGIN, bottom - 12f,
            tp.rulePaint(0.5f, 0xFFDDDDDD.toInt()),
        )
        val p = tp.paint(7.5f, color = 0xFF999999.toInt())
        val w = tp.measure(doc.footerText, p)
        b.drawText(doc.footerText, (PageGeometry.PAGE_W - w) / 2f, bottom - 4f, p)
    }

    // ------------------------------------------------------------------ 文件名

    private fun buildFileName(title: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").take(24)
        return "卷子_${safe}_$stamp.pdf"
    }
}
