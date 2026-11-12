package com.papermill.app.core.pdf.render

import java.util.Random

/**
 * M0 的示例卷：一张写死的三年级口算练习。
 *
 * 这里**故意不接数据库、不接出卷引擎** —— M0 的唯一目的是证明
 * 「卷面结构 → A4 PDF 字节 → 系统打印 / 分享」这条链路能跑通。
 * 真正的题目生成器（带自校验、难度档、种子复现）在 M1 才进来。
 *
 * 题目约束已按真实练习卷的口径写：
 *  - 减法大数在前，结果永不为负
 *  - 除法先定商和除数再乘出被除数，必然整除
 *  - 100 以内加减 + 表内乘除，符合三年级上册
 */
object SamplePaper {

    fun build(): PaperDocument {
        val rng = Random(20261112L)
        val questions = ArrayList<PaperQuestion>(20)
        val answers = ArrayList<PaperQuestion>(20)

        repeat(20) { i ->
            val no = i + 1
            val stem: String
            val answer: String
            when (i % 4) {
                0 -> {
                    val a = 12 + rng.nextInt(60)
                    val b = 3 + rng.nextInt(20)
                    stem = "$a + $b =（　　）"
                    answer = "${a + b}"
                }
                1 -> {
                    val x = 40 + rng.nextInt(55)
                    val y = 3 + rng.nextInt(30)
                    val big = maxOf(x, y)
                    val small = minOf(x, y)
                    stem = "$big - $small =（　　）"
                    answer = "${big - small}"
                }
                2 -> {
                    val a = 2 + rng.nextInt(8)
                    val b = 2 + rng.nextInt(8)
                    stem = "$a × $b =（　　）"
                    answer = "${a * b}"
                }
                else -> {
                    val b = 2 + rng.nextInt(8)
                    val q = 2 + rng.nextInt(8)
                    stem = "${b * q} ÷ $b =（　　）"
                    answer = "$q"
                }
            }
            questions.add(PaperQuestion(no, stem))
            answers.add(PaperQuestion(no, answer))
        }

        return PaperDocument(
            title = "三年级上册 · 第二单元 · 口算练习",
            subtitle = "共 20 题　建议用时 5 分钟　满分 40 分",
            sections = listOf(
                PaperSection(
                    title = "一、口算（每题 2 分，共 40 分）",
                    questions = questions,
                    columns = 2,
                ),
                // 答案单独成页（M0 里每个 section 都另起页；M1 再做「接排」）
                PaperSection(
                    title = "二、参考答案（家长核对用）",
                    questions = answers,
                    columns = 4,
                ),
            ),
        )
    }
}
