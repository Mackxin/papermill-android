package com.papermill.app.core.pdf.render

/**
 * 练习纸版式。
 *
 * ============================================================
 * 来源与版权（v0.4.0）
 * ============================================================
 * 「横条书写纸 / 方格纸 / 英文四线三格」这三类**版式思路**参考了
 * github.com/namishu/printables（代码、原创文档、模板设计 MIT）。
 * 该仓库样例 PDF 与其内置字体都没有也不会打进本 App：
 * 这里全部用 Android Canvas 自绘线条，字体走系统回退，
 * 只保留「有哪些版式、每种版式适合练什么」这一层通用设计。
 * 田字格 / 米字格 / 点阵 / 坐标纸属于通行的基础版式，自行编排。
 * 完整 MIT 声明随包放在 assets/licenses/NAMISHU_PRINTABLES_LICENSE.txt。
 */
enum class GridStyle(val label: String, val defaultCell: Float) {
    /** 方格 + 十字虚线，低年级练汉字 */
    TIANZI("田字格", 56f),
    /** 方格 + 十字 + 对角线，练间架结构 */
    MIZI("米字格", 56f),
    /** 四条横线一组，拼音 / 英文书写 */
    FOUR_LINE("四线三格", 36f),
    /** 小方格，数学草稿与画图 */
    SQUARE("方格纸", 20f),
    /** 等距横线，抄写与听写 */
    LINED("横条纸", 28f),
    /** 点阵，控笔练习 */
    DOT_GRID("点阵纸", 20f),
    /** 带坐标轴的方格，初中函数作图 */
    COORDINATE("坐标纸", 20f),
    /**
     * v0.5.0 主题识字卡：拼音在上、田字格在下，一页按词数自动铺满。
     * 只借鉴「拼音在上、汉字在下」这一通用识字卡版式，词表与拼音全部自编重录。
     */
    THEME_CARD("主题识字卡", 72f),
}

data class PaperGrid(
    val style: GridStyle,
    /** 格边长 / 行高（pt）；<=0 时用样式默认值 */
    val cell: Float = 0f,
    /** 每页左侧的书写提示（打印在标题下方） */
    val hint: String = "",
    /** 主题识字卡内容：拼音 to 词语；非空时按 [THEME_CARD][GridStyle.THEME_CARD] 版式绘制 */
    val themeCards: List<Pair<String, String>> = emptyList(),
    /** true = 田字格里印浅灰范字供描红；false = 只给拼音，格子留空默写 */
    val showThemeWords: Boolean = true,
)
