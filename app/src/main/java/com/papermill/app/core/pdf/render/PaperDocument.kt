package com.papermill.app.core.pdf.render

import com.papermill.app.domain.generate.FigureSpec
import com.papermill.app.domain.generate.FooterSize
import com.papermill.app.domain.generate.LineGap
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
    /** 看图题的配图（只描述「几个圆、时针指到几」，怎么画在 pdf 层） */
    val figure: FigureSpec? = null,
)

/**
 * 网格表格（九九乘法表、拼音音节表、公式表用）。
 *
 * 整张表作为一个 [com.papermill.app.core.pdf.layout.blocks.TableBlock] 交给
 * FlowLayout。**不再按固定行数预切**：v0.7.4 起表格能在行边界处按剩余高度
 * 自行劈开，预切只会让同一页里出现好几个重复表头。
 */
data class PaperTable(
    val headers: List<String>,
    val weights: List<Float>,
    val rows: List<List<String>>,
)

data class PaperSection(
    val title: String,
    val questions: List<PaperQuestion> = emptyList(),
    /** 栏数：口算 2~4 栏，应用题 1 栏 */
    val columns: Int = 1,
    /** 每题下方留几条答题横线（应用题用；口算为 0，答案位写在题干里） */
    val answerLines: Int = 0,
    /** 大题说明：印在标题下面、只印一次（做题指令不要去每道题里重复） */
    val instruction: String = "",
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
    /** 副标题是否与标题排在同一行（v0.17.0：省下整整一行的卷头高度） */
    val subtitleInline: Boolean = false,
    val showNameRow: Boolean = true,
    val sections: List<PaperSection>,
    /** 页脚文字；null = 用渲染层默认那句，空串 = 整条页脚不印 */
    val footerText: String? = DEFAULT_FOOTER,
    /** 页脚右侧的生成时间（已格式化好的字符串，空串 = 不印） */
    val footerStamp: String = "",
    /** 页脚右侧再印「第 N / M 页」：每页都印，第一页也有（页眉只有第 2 页起才有） */
    val showPageNo: Boolean = true,
    /** 页脚高度档位：这个高度直接从正文可用高度里扣 */
    val footerSize: FooterSize = FooterSize.NORMAL,
    /** 题间行距档位：紧凑能多塞题，宽松更好写字（见 [LineGap]） */
    val lineGap: LineGap = LineGap.NORMAL,
    /** 行距选「自定义」时用的具体 pt 值 */
    val lineGapCustom: Float = 8f,
    /** 竖版 / 横版：决定 PdfDocument 页面宽高，并随结果传给打印框架 */
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
) {
    /**
     * 题间距下界（撑开算法的起点）。
     *
     * 自定义档的 base 与 max 取同一个值：用户说「我就要 8pt」，
     * 就不该被二分撑开悄悄拉到 20pt —— 那正是「行与行之间太宽」的来源。
     */
    val gapBase: Float
        get() = if (lineGap == LineGap.CUSTOM) lineGapCustom else lineGap.base

    /** 题间距上界（撑开算法的终点）。自定义档不再撑开 */
    val gapMax: Float
        get() = if (lineGap == LineGap.CUSTOM) lineGapCustom else lineGap.max

    /**
     * 有没有东西要印在页脚。
     *
     * 三样都空时连分隔线一起省掉，正文能多吃一整条页脚的高度。
     * 页码默认是开的，所以「整条不印」这个诉求靠 footerText = "" + footerStamp = ""
     * + showPageNo = false 三者同时成立来表达（见 AppState.footerTextForSpec）。
     */
    val hasFooter: Boolean
        get() = (footerText?.isNotEmpty() == true) || footerStamp.isNotEmpty() || showPageNo

    /** 真正要印的文字：null 走默认文案 */
    val footerResolved: String
        get() = footerText ?: DEFAULT_FOOTER

    companion object {
        const val DEFAULT_FOOTER = "卷子 · 题目由本 App 程序生成，非官方教材配套"
    }
}

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
