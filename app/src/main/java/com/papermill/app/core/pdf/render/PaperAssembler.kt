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

        // 卷头题数必须用「实际生成出来的题数」：某些题型受去重 / 题库容量限制，
        // 请求 37 题可能只出到 23 题。照 spec.subtitle 印会把错误数字印在纸上。
        // 只改写自动生成的「共 N 题」副标题，用户自定义的副标题原样保留。
        val actual = paper.questions.size
        val subtitle = if (spec.subtitle.startsWith("共 ") && spec.subtitle.contains("题")) {
            "共 $actual 题　满分 ${actual * 2} 分"
        } else spec.subtitle

        return PaperDocument(
            title = if (spec.isMistakePaper) "${spec.title}（错题重练）" else spec.title,
            subtitle = subtitle,
            showNameRow = spec.showNameRow,
            sections = sections,
            footerText = "卷子 · 题目由本 App 程序生成，非官方教材配套",
            orientation = spec.orientation,
        )
    }
}
