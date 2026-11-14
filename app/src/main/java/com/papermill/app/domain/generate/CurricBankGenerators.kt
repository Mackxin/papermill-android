package com.papermill.app.domain.generate

import com.papermill.app.domain.content.CurricQuestion
import com.papermill.app.domain.content.CurriculumBank
import java.util.Random

/**
 * v0.9.0 课标题库题型：从 [CurriculumBank]（cn-math-taxonomy，CC BY-NC 4.0）里抽真题。
 *
 * 与 [BankQuestionGenerator]（自编题库）的区别：
 *  - 自编题库是「按知识点手写的几十道题」，量小、跨科、选项要自己编；
 *  - 课标题库是**上游持续扩容的真实知识点配套题**（当前 7897 道），
 *    所以这里不需要造干扰项 —— 选项本来就齐，只要洗牌并同步换算答案字母。
 *
 * 两个硬约束：
 *  1. **题目不能重复**。同一张卷里抽到两道一样的题等于白印一页，
 *     所以用题干 hash 进 `ctx.blocked`（它同时管着同卷去重与历史卷去重）。
 *  2. **答案必须跟着选项一起动**。洗牌后重新算正确字母，
 *     绝不能出现「选项换了、答案还是原来的 A」这种印错的卷子。
 */

private val LETTERS = arrayOf("A", "B", "C", "D", "E", "F")

private fun <T> shuffledBy(src: List<T>, rng: Random): MutableList<T> {
    val a = ArrayList(src)
    for (i in a.size - 1 downTo 1) {
        val j = rng.nextInt(i + 1)
        val t = a[i]
        a[i] = a[j]
        a[j] = t
    }
    return a
}

/** 题干结尾是不是已经有「写答案的地方」了 */
private fun hasWriteSlot(p: String): Boolean =
    p.contains('＿') || p.contains("__") || p.contains("（　") || p.contains("（ ）") ||
        p.contains("( )") || p.contains("(  )")

/**
 * 抽题主流程（两个题型共用）。
 *
 * 洗牌后整池走一遍，走完还差再洗一遍重来 —— 但第二轮几乎必然被 `blocked` 挡光，
 * 所以上限 4 轮，凑不够就如实少给几道。绝不为了凑数把同一道题印两遍。
 */
private fun drawCurric(ctx: GenContext, choice: Boolean): List<GeneratedQuestion> {
    val pool = CurriculumBank.pool(
        subjects = ctx.subjects,
        grade = ctx.grade,
        difficulty = ctx.difficulty,
        choice = choice,
        topicIds = ctx.topicIds,
    )
    if (pool.isEmpty()) return emptyList()

    val type = if (choice) QuestionType.CURRIC_CHOICE else QuestionType.CURRIC_BLANK
    val out = ArrayList<GeneratedQuestion>(ctx.count)
    var round = 0
    while (out.size < ctx.count && round < 4) {
        for (q in shuffledBy(pool, ctx.rng)) {
            if (out.size >= ctx.count) break
            val rendered = if (choice) renderChoice(q, ctx.rng) else renderBlank(q)
            if (rendered == null) continue
            // 用题干（而非含洗牌后选项的整段）去重，否则同一道题换个选项顺序就能重复进来
            if (!ctx.blocked.add(stableHash(q.prompt))) continue
            out.add(
                GeneratedQuestion(
                    no = 0,
                    type = type,
                    stem = rendered.first,
                    answer = rendered.second,
                    topic = topicLabel(q),
                )
            )
        }
        round++
    }
    return out
}

private fun topicLabel(q: CurricQuestion): String {
    val name = CurriculumBank.topicName(q.subject, q.topicId)
    return if (name.isEmpty()) "${q.subject.label}·知识点" else "${q.subject.label}·$name"
}

/** 填空题：题干照抄，末尾补一个写答案的括号；答案带一句解析 */
private fun renderBlank(q: CurricQuestion): Pair<String, String>? {
    val prompt = q.prompt.trim()
    val answer = q.answer.trim()
    if (prompt.isEmpty() || answer.isEmpty()) return null
    val stem = if (hasWriteSlot(prompt)) prompt else "$prompt（　　）"
    return stem to withExplain(answer, q.explain)
}

/** 选择题：选项洗牌 → 重新编 A/B/C → 同步换算正确字母 */
private fun renderChoice(q: CurricQuestion, rng: Random): Pair<String, String>? {
    if (q.options.size < 2) return null
    val key = q.answer.trim()
    val correct = q.options.firstOrNull { it.first.equals(key, ignoreCase = true) } ?: return null

    val order = shuffledBy(q.options, rng)
    val idx = order.indexOfFirst { it.first == correct.first }
    if (idx < 0) return null

    val opts = order.mapIndexed { i, p -> "${LETTERS[i]}. ${p.second.trim()}" }
    val stem = "${q.prompt.trim()}\n${opts.joinToString("　")}"
    return stem to withExplain("（${LETTERS[idx]}）${correct.second.trim()}", q.explain)
}

private fun withExplain(answer: String, explain: String): String {
    val e = explain.trim()
    return if (e.isEmpty()) answer else "$answer　解析：$e"
}

// ------------------------------------------------------------------ 填空

object CurricBlankGenerator : QuestionGenerator {
    override val type = QuestionType.CURRIC_BLANK
    override val subject = Subject.MATH

    // 题库里五科都有填空/简答题（语文除外），勾物理也要能出物理题
    override val crossSubject = true

    override fun supports(grade: Int): Boolean = grade in 1..9

    override fun usableIn(subjects: Collection<Subject>): Boolean =
        subjects.isEmpty() || subjects.any { CurriculumBank.countOf(it, choice = false) > 0 }

    override fun generate(ctx: GenContext): List<GeneratedQuestion> = drawCurric(ctx, choice = false)
}

// ------------------------------------------------------------------ 选择

object CurricChoiceGenerator : QuestionGenerator {
    override val type = QuestionType.CURRIC_CHOICE
    override val subject = Subject.MATH

    override val crossSubject = true

    override fun supports(grade: Int): Boolean = grade in 1..9

    override fun usableIn(subjects: Collection<Subject>): Boolean =
        subjects.isEmpty() || subjects.any { CurriculumBank.countOf(it, choice = true) > 0 }

    override fun generate(ctx: GenContext): List<GeneratedQuestion> = drawCurric(ctx, choice = true)
}
