package com.papermill.app.core.pdf.render

import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.GeneratedPaper
import com.papermill.app.domain.generate.PaperSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    // 配图跟着题目走：看图题不画图就只剩一句「看图列式」，等于没出
                    questions = s.questions.map {
                        PaperQuestion(no = it.no, stem = it.stem, figure = it.figure)
                    },
                    columns = s.columns,
                    answerLines = s.answerLines,
                    instruction = s.instruction,
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
            subtitleInline = spec.headerOneLine,
            showNameRow = spec.showNameRow,
            sections = sections,
            footerText = spec.footerText,
            // 生成时间印在页脚右侧：家长要能看出这是哪天的卷子，
            // 又不该为它多占一行 —— 页脚本来就要印东西。
            // 页脚整条关掉（footerText == ""）时时间也一起不印：
            // 「不印页脚」的意图是「纸尾干干净净」，只在右下角留个时间戳不叫不印。
            footerStamp = if (spec.showCreatedAt && spec.footerText?.isEmpty() != true) {
                "生成 " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(paper.createdAt))
            } else "",
            lineGap = spec.lineGap,
            orientation = spec.orientation,
        )
    }
}
