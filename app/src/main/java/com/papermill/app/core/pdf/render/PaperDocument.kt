package com.papermill.app.core.pdf.render

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

data class PaperSection(
    val title: String,
    val questions: List<PaperQuestion>,
    /** 栏数：口算 2~4 栏，应用题 1 栏 */
    val columns: Int = 1,
    /** 每题下方留几条答题横线（应用题用；口算为 0，答案位写在题干里） */
    val answerLines: Int = 0,
)

data class PaperDocument(
    val title: String,
    val subtitle: String = "",
    val showNameRow: Boolean = true,
    val sections: List<PaperSection>,
    val footerText: String = "卷子 · 自编练习卷",
)

/** 渲染结果：文件名 + PDF 字节 + 页数（页数给 PrintDocumentInfo 用，必须准） */
class RenderedPdf(
    val fileName: String,
    val bytes: ByteArray,
    val pageCount: Int,
)
