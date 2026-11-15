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
    /**
     * 目录里的分组名。
     *
     * v0.15.0 从 9 张涨到 22 张，如果还是一张平铺的网格，找「数轴纸」得滑两屏、
     * 而且「田字格」和「课程表」排在一起毫无内在联系。分组之后每组 3~6 张，
     * 一屏之内就能看完一类，也顺带说清了「这张纸是干什么用的」。
     */
    val group: String = GROUP_BASIC,
) {
    companion object {
        /** 早期 9 张沿用下来的默认分组 */
        const val GROUP_BASIC = "基础格纸"
        const val GROUP_WRITING = "练字 · 书写格"
        const val GROUP_LINED = "横线 · 抄写纸"
        const val GROUP_MATH = "数学 · 演算纸"
        const val GROUP_NOTE = "笔记 · 表格"
        const val GROUP_EXTRA = "兴趣 · 其他"
    }
}

object PracticeData {

    /**
     * v0.15.0 把原来 9 张平铺的清单按「这张纸干什么用」分成了 5 组。
     *
     * 分组的顺序就是打印频率的顺序：练字格最常用，乐谱 / 迷宫归到最后一组。
     * 组内顺序也照顾了「同一类里从小到大」—— 横线纸按行距从宽到窄排。
     */
    val sheets: List<PracticeSheet> = listOf(
        // ---------------- 练字 · 书写格 ----------------
        PracticeSheet(
            "tianzi", "田字格练字纸", "低年级生字 · 一页约 9×11 格",
            PaperGrid(GridStyle.TIANZI, hint = "书写提示：注意笔顺与间架结构，一字一格"),
            group = PracticeSheet.GROUP_WRITING,
        ),
        PracticeSheet(
            "mizi", "米字格练字纸", "带对角辅助线 · 练笔画位置",
            PaperGrid(GridStyle.MIZI, hint = "书写提示：横平竖直，撇捺沿对角线舒展"),
            group = PracticeSheet.GROUP_WRITING,
        ),
        PracticeSheet(
            "pinyin_tianzi", "拼音田字格", "上拼音下田字 · 一年级抄写主力",
            PaperGrid(
                GridStyle.PINYIN_TIANZI,
                hint = "上面写拼音，下面写汉字：一格一字，拼音写在三线中间",
            ),
            group = PracticeSheet.GROUP_WRITING,
        ),
        PracticeSheet(
            "hui_gong", "回宫格练字纸", "外框 + 内框 · 练起笔与收笔",
            PaperGrid(
                GridStyle.HUI_GONG,
                hint = "字的起笔与收笔落在内框边上，内部十字帮你定中宫位置",
            ),
            group = PracticeSheet.GROUP_WRITING,
        ),
        PracticeSheet(
            "jiu_gong", "九宫格练字纸", "3×3 细分 · 练比例与重心",
            PaperGrid(
                GridStyle.JIU_GONG,
                hint = "看清每一笔落在九个小格的哪一格：上紧下松、左收右放，字才站得住",
            ),
            group = PracticeSheet.GROUP_WRITING,
        ),
        PracticeSheet(
            "tianzi_landscape", "田字格（A4 横版）", "横向排版 · 一页能写更多字",
            PaperGrid(GridStyle.TIANZI, hint = "横版 A4 · 打印时请选「横向」"),
            orientation = PageOrientation.LANDSCAPE,
            group = PracticeSheet.GROUP_WRITING,
        ),

        // ---------------- 横线 · 抄写纸 ----------------
        PracticeSheet(
            "lined", "横条书写纸", "行距约 10mm · 抄写、听写、日记",
            PaperGrid(GridStyle.LINED),
            group = PracticeSheet.GROUP_LINED,
        ),
        PracticeSheet(
            // 一年级写字大、手还不稳，10mm 的行距对他们太挤，12mm 刚好
            "lined_wide", "宽行横线纸", "行距约 12mm · 低年级大字抄写",
            PaperGrid(GridStyle.LINED, cell = 34f, hint = "一行写一行空，字不用挤在一起"),
            group = PracticeSheet.GROUP_LINED,
        ),
        PracticeSheet(
            // 高年级笔记 / 摘抄：行距窄一点，一页能多记三成
            "lined_narrow", "窄行横线纸", "行距约 7mm · 高年级笔记、摘抄",
            PaperGrid(GridStyle.LINED, cell = 20f),
            group = PracticeSheet.GROUP_LINED,
        ),
        PracticeSheet(
            "fourline", "拼音 / 英文四线格", "四线三格 · 抄写与默写",
            PaperGrid(GridStyle.FOUR_LINE, hint = "中格写满，上不顶线、下不踩线"),
            group = PracticeSheet.GROUP_LINED,
        ),
        PracticeSheet(
            "dictation", "听写纸", "带题号 · 一行一词，遮住再默",
            PaperGrid(
                GridStyle.DICTATION,
                hint = "家长念一个、孩子写一个；写完把上面的折过去再默一遍",
            ),
            group = PracticeSheet.GROUP_LINED,
        ),

        // ---------------- 数学 · 演算纸 ----------------
        PracticeSheet(
            "square", "数学方格纸", "小方格 · 草稿 / 画图 / 竖式",
            PaperGrid(GridStyle.SQUARE),
            group = PracticeSheet.GROUP_MATH,
        ),
        PracticeSheet(
            "vertical_calc", "竖式演算格", "一格一题 · 格内三条线对齐数位",
            PaperGrid(
                GridStyle.VERTICAL_CALC,
                hint = "一题一格：个位对齐个位，进位写在小线上方",
            ),
            group = PracticeSheet.GROUP_MATH,
        ),
        PracticeSheet(
            "number_line", "数轴练习纸", "多条空白数轴 · 标 0 起点",
            PaperGrid(
                GridStyle.NUMBER_LINE,
                hint = "先标刻度再画点：向右是变大，向左是变小",
            ),
            group = PracticeSheet.GROUP_MATH,
        ),
        PracticeSheet(
            "coord", "坐标纸", "带坐标轴 · 初中函数作图",
            PaperGrid(GridStyle.COORDINATE),
            group = PracticeSheet.GROUP_MATH,
        ),

        // ---------------- 笔记 · 表格 ----------------
        PracticeSheet(
            "blank", "空白纸", "什么都不印 · 画图、草稿、手抄报",
            PaperGrid(GridStyle.BLANK, hint = "整页纯白，只有卷头和页脚"),
            group = PracticeSheet.GROUP_NOTE,
        ),
        PracticeSheet(
            "cornell", "康奈尔笔记纸", "线索 / 笔记 / 总结 三分区",
            PaperGrid(
                GridStyle.CORNELL,
                hint = "左边写关键词和问题，右边记内容，下课后在底部写三行总结",
            ),
            group = PracticeSheet.GROUP_NOTE,
        ),
        PracticeSheet(
            "timetable", "课程表", "节次 × 周一至周五 · 底部备注",
            PaperGrid(GridStyle.TIMETABLE),
            group = PracticeSheet.GROUP_NOTE,
        ),
        PracticeSheet(
            "mistake", "错题整理纸", "一题一区块 · 题干 / 错因 / 正解",
            PaperGrid(
                GridStyle.MISTAKE,
                hint = "抄题干 → 写清错在哪一步 → 再写正确解法，最后自己讲一遍",
            ),
            group = PracticeSheet.GROUP_NOTE,
        ),

        // ---------------- 兴趣 · 其他 ----------------
        PracticeSheet(
            "dot", "点阵控笔纸", "点阵定位 · 练笔画起收",
            PaperGrid(GridStyle.DOT_GRID),
            group = PracticeSheet.GROUP_EXTRA,
        ),
        PracticeSheet(
            "music", "五线谱纸", "空白谱表 · 抄谱、视唱、默写",
            PaperGrid(
                GridStyle.MUSIC,
                cell = 48f,
                hint = "谱号与拍号写在每行开头，符头压在线上或线间",
            ),
            group = PracticeSheet.GROUP_EXTRA,
        ),
        PracticeSheet(
            "maze", "迷宫纸", "现场生成 · 每点一次都是新迷宫",
            PaperGrid(
                GridStyle.MAZE,
                cell = 26f,
                hint = "从左上角入口走进去，到右下角出口走出来；可用铅笔，走错就擦掉重来",
            ),
            group = PracticeSheet.GROUP_EXTRA,
        ),
    )

    /** 按 [PracticeSheet.group] 切好、且保持声明顺序的分组；空组自动不出现 */
    val sheetGroups: List<Pair<String, List<PracticeSheet>>> =
        sheets.groupBy { it.group }.map { (k, v) -> k to v }

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
