package com.papermill.app.core.pdf.render

import com.papermill.app.domain.reference.PracticeSheet

/** 练习纸 → 渲染文档：单 section + grid，PaperRenderer 会走「铺满一页」分支。 */
object PracticeRenderer {

    fun build(sheet: PracticeSheet): PaperDocument = PaperDocument(
        title = sheet.title,
        subtitle = sheet.subtitle,
        showNameRow = false,
        sections = listOf(PaperSection(title = "", grid = sheet.grid)),
        footerText = "卷子 · 自绘练习纸 · 可反复打印（版式参考 namishu/printables，MIT）",
        orientation = sheet.orientation,
    )
}
