package com.papermill.app.core.pdf.render

import com.papermill.app.domain.reference.ReferenceSheet

/** 速查表 → 渲染文档。复用试卷的卷头/页眉/页脚，只是正文换成网格表格。 */
object ReferenceRenderer {

    fun build(sheet: ReferenceSheet): PaperDocument = PaperDocument(
        title = sheet.title,
        subtitle = sheet.subtitle,
        showNameRow = false,
        sections = listOf(
            PaperSection(
                title = "要点速查",
                columns = 1,
                table = sheet.table,
            )
        ),
        footerText = "卷子 · 学生必备速查表（可反复打印张贴）",
    )
}
