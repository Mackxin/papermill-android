package com.papermill.app.core.pdf.render

import com.papermill.app.domain.reference.PracticeSheet

/** 练习纸 → 渲染文档：单 section + grid，PaperRenderer 会走「铺满一页」分支。 */
object PracticeRenderer {

    fun build(sheet: PracticeSheet): PaperDocument {
        // 迷宫纸：种子为 0 就现场换一个，保证「再点一次是新迷宫」
        val grid = if (sheet.grid.style == GridStyle.MAZE && sheet.grid.mazeSeed == 0L) {
            sheet.grid.copy(mazeSeed = System.nanoTime())
        } else {
            sheet.grid
        }
        return PaperDocument(
            title = sheet.title,
            subtitle = sheet.subtitle,
            showNameRow = false,
            sections = listOf(PaperSection(title = "", grid = grid)),
            footerText = sheet.footer
                ?: "卷子 · 自绘练习纸 · 可反复打印（版式参考 namishu/printables，MIT）",
            orientation = sheet.orientation,
        )
    }
}
