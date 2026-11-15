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

    /** 答案页：题与题之间的基础呼吸 */
    private const val ANSWER_GAP = 4f

    /** 答案页栏宽下限：再窄，带解析的答案会被折成豆腐块 */
    private const val MIN_ANSWER_COL_W = 108f

    /** 撑满整页时允许把行距拉到多少：超过这个值宁可多加一栏，也不要把答案拉成稀稀拉拉几行 */
    private const val MAX_ANSWER_GAP = 26f

    /** 正文最多分几栏：再多每栏只剩 80pt，两位数算式也要折行 */
    private const val MAX_BODY_COLUMNS = 6

    /**
     * 选答案栏数的及格线：撑满后至少占满这么多页面，才认这一档。
     *
     * 86% 是「再少就该换更多栏」的分界 —— 低于它就说明这一档排完还剩小半张纸，
     * 不如把栏数减少、把行距摊开。
     */
    private const val ANSWER_MIN_FILL = 0.86f

    /** 正文题间距的默认值（pt）：与 QuestionBlock.ITEM_GAP 一致 */
    private const val BODY_ITEM_GAP = 4f

    /** 正文撑开的目标填充率：低于它就试着把题间距拉开 */
    private const val BODY_FILL_TARGET = 0.93f

    /**
     * 正文撑开的档位。
     *
     * 逐档加大而不是一步算到位：题间距一拉大，折行位置和分栏高度都会变，
     * 只有真的重排一遍才知道会不会多出一页。逐档加、一超页就停在上一档。
     * 上限 14pt —— 再大题目之间就散成一篇散沙，家长会以为是排版出错。
     */
    private val BODY_GAP_STEPS = floatArrayOf(5.5f, 7f, 9f, 11f, 14f)

    /**
     * 按实际题面宽度算这道大题该分几栏。
     *
     * 取 **80 分位**而不是最大值：一道特别长的题（带四个选项的选择题）不该
     * 把整张卷压成 1 栏；反过来，全是短题干的口算也不该被一个长题干拖累。
     * 80% 的题不折行，剩下 20% 折一行 —— 比「整页只有一种栏宽」好得多。
     */
    private fun fitColumns(
        widths: List<Float>,
        contentW: Float,
        gap: Float,
        floor: Int,
        ceiling: Int,
    ): Int {
        if (widths.isEmpty()) return floor
        val sorted = widths.sorted()
        val p80 = sorted[((sorted.size - 1) * 0.8f).toInt()]
        // 栏宽还得留出题号与题干之间的呼吸
        val need = p80.coerceAtLeast(48f) + 8f
        var best = floor.coerceIn(1, ceiling)
        for (c in best..ceiling) {
            val colW = (contentW - gap * (c - 1)) / c
            if (colW >= need) best = c else break
        }
        return best
    }

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
        var bodyColumns = 2

        /** 正文块（[scale] 只作用于表格行高，见下面「单页速查表铺满」） */
        fun buildBody(scale: Float, itemGap: Float = BODY_ITEM_GAP): List<Block> {
            val out = ArrayList<Block>()
            var firstHint = 0
            doc.sections.filter { !it.isAnswer }.forEach { section ->
                // 长题干 / 英文句子 / 带选项的单选题必须占整行：题型用 columns <= 1
                // 声明这一点。注意**不再**按 answerLines > 0 强制整宽 ——
                // 竖式计算要留横线，但横线只在栏内就够，一栏一题会把半张纸浪费掉。
                val forceFullWidth = section.columns <= 1
                // 这道大题自己该排几栏：按实际题面宽度算，不再让整卷共用一个数
                val hint = if (forceFullWidth || section.questions.isEmpty()) 0 else {
                    fitColumns(
                        widths = section.questions.map {
                            QuestionBlock(no = it.no, stem = it.stem).singleLineWidth(tp)
                        },
                        contentW = g.contentW,
                        gap = COLUMN_GAP,
                        floor = maxOf(1, section.columns),
                        ceiling = MAX_BODY_COLUMNS,
                    )
                }
                if (firstHint == 0 && hint > 0) firstHint = hint
                out.add(SectionHeaderBlock(section.title, columnHint = hint))
                // 做题指令只印一次：以前「用竖式计算：」是拼在每道题题干里的，
                // 一页 20 道题就重复 20 遍
                if (section.instruction.isNotBlank()) out.add(NoteBlock(section.instruction))
                val table = section.table
                if (table != null) {
                    // 整张表一个块：跨页由 FlowLayout + TableBlock.split 按剩余高度自己切，
                    // 表头每个实际页面各画一次（按固定行数预切会在同一页里重复表头）
                    out.add(
                        TableBlock(
                            table.headers, table.weights, table.rows,
                            showHeader = true, rowScale = scale,
                        )
                    )
                    out.add(FullWidthBlock(NoteBlock("本表可直接打印张贴，题目均由本 App 整理编排。")))
                } else {
                    section.questions.forEach { q ->
                        val qb = QuestionBlock(
                            no = q.no,
                            stem = q.stem,
                            answerLines = section.answerLines,
                            itemGap = itemGap,
                            // 看图题带图；答案页那一路不传（答案不需要再印一遍图）
                            figure = q.figure,
                        )
                        // 图必须整宽：塞进半栏会缩成指甲盖，而且图和题干抢宽度后
                        // 「看图列式」四个字和图形挤在同一行，学生根本对不上
                        out.add(if (forceFullWidth || q.figure != null) FullWidthBlock(qb) else qb)
                    }
                    bodyColumns = maxOf(bodyColumns, section.columns)
                }
            }
            // 整条流的初始栏数：取第一个非整宽大题的栏数，避免第一段用了错的值
            if (firstHint > 0) bodyColumns = firstHint
            return out
        }

        fun layoutBody(blocks: List<Block>): ArrayList<FlowLayout.Page> {
            val out = ArrayList<FlowLayout.Page>()
            if (blocks.isNotEmpty()) {
                out.addAll(
                    FlowLayout(
                        contentW = g.contentW,
                        contentHFirst = contentHFirst,
                        contentHOther = contentHOther,
                        columns = bodyColumns,
                        columnGap = COLUMN_GAP,
                        startsOnFirstPage = true,
                    ).layout(blocks, tp)
                )
            }
            return out
        }

        var pages = layoutBody(buildBody(1f))

        // ---- 单页速查表铺满整页 ----
        // 九九乘法表、小学语法表这类内容一页装得下，但只占页面 40%~70%，
        // 打印出来下半张全是白纸。按实际占用高度把行高整体拉高，
        // 表头与文字大小不变、只是行距变松，视觉上就像一张挂图。
        val bodySections = doc.sections.filter { !it.isAnswer }
        if (bodySections.size == 1 && bodySections[0].table != null && pages.size == 1) {
            val used = pages[0].items.maxOfOrNull { it.y + it.block.measure(tp, it.w) } ?: 0f
            if (used > 0f && used < contentHFirst * 0.74f) {
                val scale = (contentHFirst * 0.93f / used).coerceIn(1f, 3.2f)
                val taller = layoutBody(buildBody(scale))
                // 拉高后必须还是一页；否则说明本来就在溢出边缘，保持原样重排
                if (taller.size == 1) pages = taller
            }
        }

        // ---- 正文撑开：最后一页别只印半张 ----
        // 栏数按题面宽度定下来之后（口算 3~4 栏），52 道口算只占 18 行，
        // 下半张纸反而比原来更空 —— 多栏只是把宽度用上了，没解决纵向。
        // 这里按答案页同一套思路：把剩余高度摊到题间距上，要求**不增页**。
        // 必须放在答案页之前：答案页一接进来，pages.last() 就是答案页了，
        // 量出来的填充率全是 99%，正文撑开永远不会触发。
        fun bodyFillOf(list: List<FlowLayout.Page>): Float {
            if (list.isEmpty()) return 1f
            val page = list.last()
            val bottom = page.items.maxOfOrNull { it.y + it.block.measure(tp, it.w) } ?: 0f
            val cap = if (list.size == 1) contentHFirst else contentHOther
            return if (cap <= 0f) 1f else (bottom / cap).coerceIn(0f, 1f)
        }
        if (bodyFillOf(pages) < BODY_FILL_TARGET) {
            var best = pages
            for (gap in BODY_GAP_STEPS) {
                val trial = layoutBody(buildBody(1f, gap))
                // 撑开不许把卷子撑多一页 —— 那是把「省半张纸」换成「多一张纸」
                if (trial.size != pages.size) break
                best = trial
                if (bodyFillOf(trial) >= BODY_FILL_TARGET) break
            }
            pages = best
        }

        // ---- 答案页：自适应栏数 + 撑满整页 ----
        // 以前是「答案长就 2 栏、短就 4 栏」，选完就排，排完剩下的半页白白空着
        // （34 道题的答案只占了纸的上半张）。现在按实际答案试排 2..6 栏：
        // 挑「能一页装完、行距又不用拉得离谱」的栏数，再把剩余高度摊到行距上，
        // 让答案均匀铺满整页。
        var firstAnswerPage = -1
        val answerSections = doc.sections.filter { it.isAnswer }
        if (answerSections.any { it.questions.isNotEmpty() }) {
            firstAnswerPage = pages.size
            val answerCount = answerSections.sumOf { it.questions.size }

            /** [gap] 是题与题之间的呼吸：撑满整页就靠加大它，字号不动 */
            fun answerFlow(columns: Int, gap: Float): ArrayList<FlowLayout.Page> {
                val blocks = ArrayList<Block>()
                answerSections.forEach { section ->
                    blocks.add(SectionHeaderBlock(section.title))
                    section.questions.forEach { q ->
                        blocks.add(
                            QuestionBlock(
                                no = q.no,
                                stem = q.stem,
                                answerLines = section.answerLines,
                                itemGap = gap,
                            )
                        )
                    }
                }
                return ArrayList(
                    FlowLayout(
                        contentW = g.contentW,
                        contentHFirst = contentHFirst,
                        contentHOther = contentHOther,
                        columns = columns,
                        columnGap = COLUMN_GAP,
                        startsOnFirstPage = false,
                    ).layout(blocks, tp)
                )
            }

            // 栏宽不能再窄：带解析的语法答案挤成豆腐块比留白更糟
            val maxCols = (g.contentW / MIN_ANSWER_COL_W).toInt().coerceIn(2, 6)

            /**
             * 选定栏数：**从多栏往少栏试，取第一个「撑满后仍够满」的**。
             *
             * 以前是「从少往多试，取第一个能一页装下的」—— 52 条答案在 2 栏里
             * 只占 84%、被行距拉开撑满，看起来是满了，可每条答案只有 3 个字宽，
             * 右半栏全是空白（用户截图指的就是这个）。
             *
             * 现在反过来：先假设最多栏，算它撑满后的填充率；够 [ANSWER_MIN_FILL]
             * 就认这一档 —— 栏越多、列越窄，横向浪费越少。高栏数装不下或撑不满
             * （带解析的长答案）就退回少栏，最差也就是回到 2 栏。
             */
            var bestPages: ArrayList<FlowLayout.Page>? = null
            var bestFill = -1f
            for (c in maxCols downTo 2) {
                val laid = answerFlow(c, ANSWER_GAP)
                if (laid.size != 1) continue // 这一档装不下一页，栏数太多，退一档
                val used = laid[0].items.maxOfOrNull { it.y + it.block.measure(tp, it.w) } ?: 0f
                val rows = ((answerCount + c - 1) / c).coerceAtLeast(1) // 向上取整，末行不满也算一行
                val slack = ((contentHOther * 0.94f - used) / (rows - 1).coerceAtLeast(1))
                    .coerceIn(0f, MAX_ANSWER_GAP)
                val filled = answerFlow(c, ANSWER_GAP + slack)
                if (filled.size != 1) continue
                val fill = ((used + slack * (rows - 1)) / contentHOther).coerceIn(0f, 1f)
                // 从多栏往少栏试：第一个「撑满后仍然够满」的就是答案 —— 栏数最多、
                // 列最窄，横向浪费最少。再往少栏试只会更宽。
                if (fill >= ANSWER_MIN_FILL) {
                    bestPages = filled
                    break
                }
                if (fill > bestFill) {
                    bestFill = fill
                    bestPages = filled
                }
            }
            // 一档都不够满（长答案 / 题太少）：退回填充率最高的那档，别硬拆成窄栏
            pages.addAll(bestPages ?: answerFlow(2, ANSWER_GAP))
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
