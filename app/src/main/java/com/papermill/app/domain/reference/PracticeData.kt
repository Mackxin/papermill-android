package com.papermill.app.domain.reference

import com.papermill.app.core.pdf.render.GridStyle
import com.papermill.app.core.pdf.render.PaperGrid
import com.papermill.app.domain.generate.PageOrientation
import com.papermill.app.domain.generate.ThemeBank

/**
 * 练习纸目录：点一张就生成一页铺满的 A4 PDF。
 *
 * 竖版适合练字 / 抄写，横版适合口算草稿和长时间书写，
 * 最后一张特意做成 A4 横向，方便直接验证横版打印链路。
 */
data class PracticeSheet(
    val id: String,
    val title: String,
    val subtitle: String,
    val grid: PaperGrid,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    /** 页脚说明；主题识字纸要单独标注「自编词库、拼音校对」的来源边界 */
    val footer: String? = null,
)

object PracticeData {

    val sheets: List<PracticeSheet> = listOf(
        PracticeSheet(
            "tianzi", "田字格练字纸", "低年级生字 · 一页约 9×11 格",
            PaperGrid(GridStyle.TIANZI, hint = "书写提示：注意笔顺与间架结构，一字一格"),
        ),
        PracticeSheet(
            "mizi", "米字格练字纸", "带对角辅助线 · 练笔画位置",
            PaperGrid(GridStyle.MIZI, hint = "书写提示：横平竖直，撇捺沿对角线舒展"),
        ),
        PracticeSheet(
            "fourline", "拼音 / 英文四线格", "四线三格 · 抄写与默写",
            PaperGrid(GridStyle.FOUR_LINE, hint = "中格写满，上不顶线、下不踩线"),
        ),
        PracticeSheet(
            "square", "数学方格纸", "小方格 · 草稿 / 画图 / 竖式",
            PaperGrid(GridStyle.SQUARE),
        ),
        PracticeSheet(
            "lined", "横条书写纸", "等距横线 · 抄写、听写、日记",
            PaperGrid(GridStyle.LINED),
        ),
        PracticeSheet(
            "dot", "点阵控笔纸", "点阵定位 · 练笔画起收",
            PaperGrid(GridStyle.DOT_GRID),
        ),
        PracticeSheet(
            "coord", "坐标纸", "带坐标轴 · 初中函数作图",
            PaperGrid(GridStyle.COORDINATE),
        ),
        PracticeSheet(
            "tianzi_landscape", "田字格（A4 横版）", "横向排版 · 一页能写更多字",
            PaperGrid(GridStyle.TIANZI, hint = "横版 A4 · 打印时请选「横向」"),
            orientation = PageOrientation.LANDSCAPE,
        ),
        PracticeSheet(
            "maze", "迷宫纸", "现场生成 · 每点一次都是新迷宫",
            PaperGrid(
                GridStyle.MAZE,
                cell = 26f,
                hint = "从左上角入口走进去，到右下角出口走出来；可用铅笔，走错就擦掉重来",
            ),
        ),
    )

    /**
     * v0.5.0 主题识字纸：6 个生活主题 × 2 种练法 = 12 张，全部一页铺满。
     *
     * - **识字卡**：拼音 + 田字格里的浅灰范字，供描红；
     * - **默写格**：只给拼音、田字格留空，供听写默写。
     *
     * 词表来自 App 内自编的 ThemeBank（6 大场景、人物 / 物品 / 设施分类），
     * 拼音逐条重录校对，没有复制任何第三方仓库的词表或图片。
     */
    val themeSheets: List<PracticeSheet> = ThemeBank.themes.flatMap { theme ->
        val cards = ThemeBank.cardsOf(theme.id)
        listOf(
            PracticeSheet(
                id = "theme_${theme.id}_read",
                title = "${theme.name} · 识字卡",
                subtitle = "${cards.size} 个词 · 拼音 + 范字描红",
                grid = PaperGrid(
                    style = GridStyle.THEME_CARD,
                    hint = "看拼音读一读，沿浅灰范字描一遍，再在横线上独立写一遍（人物 · 物品 · 设施）",
                    themeCards = cards,
                    showThemeWords = true,
                ),
                footer = "卷子 · 主题识字卡 · 词表自编、拼音逐条校对，可反复打印",
            ),
            PracticeSheet(
                id = "theme_${theme.id}_write",
                title = "${theme.name} · 默写格",
                subtitle = "${cards.size} 个词 · 只看拼音写汉字",
                grid = PaperGrid(
                    style = GridStyle.THEME_CARD,
                    hint = "只看拼音，在田字格里写出对应的词语，再在横线上组词或造句",
                    themeCards = cards,
                    showThemeWords = false,
                ),
                footer = "卷子 · 主题默写格 · 词表自编、拼音逐条校对，可反复打印",
            ),
        )
    }
}
