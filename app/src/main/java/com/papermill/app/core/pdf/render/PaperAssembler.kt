package com.papermill.app.core.pdf.render

import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.GeneratedPaper
import com.papermill.app.domain.generate.PaperSpec

/**
 * 业务卷子 → 渲染中间表示。
 *
 * 排版引擎不认识「知识点 / 题型 / 难度」，这里负责翻译。
 * 答案页单独成 section：四栏、小字、强制另起页。
 */
object PaperAssembler {

    fun build(paper: GeneratedPaper): PaperDocument = build(paper.spec, paper)

    fun build(spec: PaperSpec, paper: GeneratedPaper): PaperDocument {
        val sections = ArrayList<PaperSection>(paper.sections.size + 1)

        paper.sections.forEach { s ->
            sections.add(
                PaperSection(
                    title = s.title,
                    questions = s.questions.map { PaperQuestion(no = it.no, stem = it.stem) },
                    columns = s.columns,
                    answerLines = s.answerLines,
                )
            )
        }

        if (spec.answerMode == AnswerMode.LAST_PAGE && paper.questions.isNotEmpty()) {
            sections.add(
                PaperSection(
                    title = "参考答案（家长核对用）",
                    questions = paper.questions.map { PaperQuestion(no = it.no, stem = it.answer) },
                    columns = 4,
                    isAnswer = true,
                )
            )
        }

        return PaperDocument(
            title = if (spec.isMistakePaper) "${spec.title}（错题重练）" else spec.title,
            subtitle = spec.subtitle,
            showNameRow = spec.showNameRow,
            sections = sections,
            footerText = "卷子 · 题目由本 App 程序生成，非官方教材配套",
        )
    }
}
