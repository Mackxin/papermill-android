package com.papermill.app.core.pdf.render

import com.papermill.app.domain.generate.PageOrientation

/**
 * 渲染用的中间表示。
 *
 * 这一层刻意与业务模型（domain 里的 PaperSpec / GeneratedQuestion）解耦：
 * 排版引擎只认「标题 + 若干大题 + 每题一行题干」，不知道什么是知识点、什么是错题。
 * 好处是后续接数据库、接题库、接预览，排版代码一行不用改。
 */
data class PaperQuestion(
    val no: Int,
    val stem: String,
)

/**
 * 网格表格（九九乘法表、拼音音节表、公式表用）。
 * 渲染时会按 [rowsPerBlock] 把行拆成多个 TableBlock，交给 FlowLayout 自动分页。
 */
data class PaperTable(
    val headers: List<String>,
    val weights: List<Float>,
    val rows: List<List<String>>,
    val rowsPerBlock: Int = 18,
)

data class PaperSection(
    val title: String,
    val questions: List<PaperQuestion> = emptyList(),
    /** 栏数：口算 2~4 栏，应用题 1 栏 */
    val columns: Int = 1,
    /** 每题下方留几条答题横线（应用题用；口算为 0，答案位写在题干里） */
    val answerLines: Int = 0,
    /** 非空时本 section 渲染成网格表格，忽略 questions */
    val table: PaperTable? = null,
    /** 非空时整份文档渲染成一页铺满的练习纸，忽略 sections 的其它内容 */
    val grid: PaperGrid? = null,
    /** 答案页标记：统计「最后一页正文用了多少」时不把它算进去 */
    val isAnswer: Boolean = false,
)

data class PaperDocument(
    val title: String,
    val subtitle: String = "",
    val showNameRow: Boolean = true,
    val sections: List<PaperSection>,
    val footerText: String = "卷子 · 自编练习卷",
    /** 竖版 / 横版：决定 PdfDocument 页面宽高，并随结果传给打印框架 */
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
)

/**
 * 渲染结果。
 * [pageFillRatios] 每页「正文内容高度 / 该页可用高度」，用于判断
 * 「最后一页只印了三行、白浪费一张纸」，并据此自动补足题目。
 */
class RenderedPdf(
    val fileName: String,
    val bytes: ByteArray,
    val pageCount: Int,
    val pageFillRatios: List<Float> = emptyList(),
    /** 正文页数（不含答案页）；最后一页正文 = 第 contentPageCount 页 */
    val contentPageCount: Int = pageCount,
    /** 打印时按这个方向选 MediaSize，竖版走 ISO_A4，横版走 ISO_A4.asLandscape() */
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
)
