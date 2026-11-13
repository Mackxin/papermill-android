package com.papermill.app.domain.reference

import com.papermill.app.core.pdf.render.GridStyle
import com.papermill.app.core.pdf.render.PaperGrid
import com.papermill.app.domain.generate.PageOrientation

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
            "square", "数学方格纸", "20pt 小方格 · 草稿 / 画图 / 竖式",
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
    )
}
