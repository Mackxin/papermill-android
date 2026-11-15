package com.papermill.app.domain.generate

import com.papermill.app.domain.content.ScienceBank
import com.papermill.app.domain.content.ScienceQuestion
import java.util.Random

/**
 * v0.11.0 小学科学：三个题型都从 [ScienceBank] 里抽题。
 *
 * 选择题要洗牌选项并同步换算答案字母 —— 印出来答案错位是最严重的事故，
 * 所以这里的答案字母是从「洗牌后的位置」算出来的，不是抄原题的。
 * 判断题和填空题没有选项，答案原样带出，附一句解析。
 *
 * 去重用题干 hash（`ctx.blocked`），同一张卷不会出现两道一样的题。
 */

private val SCIENCE_LETTERS = arrayOf("A", "B", "C", "D", "E")

private fun <T> shuffledCopy(src: List<T>, rng: Random): MutableList<T> {
    val a = ArrayList(src)
    for (i in a.size - 1 downTo 1) {
        val j = rng.nextInt(i + 1)
        val t = a[i]
        a[i] = a[j]
        a[j] = t
    }
    return a
}

private fun withScienceExplain(answer: String, explain: String): String {
    val e = explain.trim()
    return if (e.isEmpty()) answer else "$answer　解析：$e"
}

/**
 * 抽题主流程：按难度就近取 → 洗牌 → 逐条渲染。
 *
 * ============================================================
 * 为什么要分轮，而不是「抽一遍就完事」
 * ============================================================
 * 科学题库每个年级每种题型只有 12 道，而 `ctx.blocked` 里装着**最近几张历史卷**的题干 hash。
 * 连着出两张同年级的科学卷时，第一张能把这 12 道全占掉，第二张一抽就被历史避让挡光 ——
 * 实测过：要 8 道填空、池里 8 道、最后出 0 道，整个大题从卷子上凭空消失，
 * 界面上写着 30 题、印出来只有 22 题。
 *
 * 所以规则定成：
 *  1. **同一道题在这张卷里只出现一次**（`paperSeen`）—— 这条永远生效；
 *  2. **历史避让只在头两轮生效**，第三轮起直接放行 ——
 *     宁可两道旧题重印，也不能因为池子小就让一个大题整块不见。
 * 判断题 / 填空题没有选项，重印就是原样重印；选择题会重新洗牌，选项顺序不同。
 */
private fun drawScience(ctx: GenContext, kind: ScienceQuestion.Kind, type: QuestionType): List<GeneratedQuestion> {
    val picked = ScienceBank.draw(ctx.grade, kind, ctx.difficulty, ctx.count)
    if (picked.isEmpty()) return emptyList()

    val paperSeen = HashSet<Long>()
    val out = ArrayList<GeneratedQuestion>(ctx.count)
    var round = 0
    while (out.size < ctx.count && round < 4) {
        for (q in shuffledCopy(picked, ctx.rng)) {
            if (out.size >= ctx.count) break
            val rendered = when (kind) {
                ScienceQuestion.Kind.CHOICE -> renderScienceChoice(q, ctx.rng)
                ScienceQuestion.Kind.JUDGE -> renderScienceJudge(q)
                ScienceQuestion.Kind.BLANK -> renderScienceBlank(q)
            } ?: continue
            // 纸内去重：按原题干，换个选项顺序也算同一道题
            val srcHash = stableHash(q.stem)
            if (srcHash in paperSeen) continue
            // 历史避让用**渲染后题干**的 hash —— 全 App（历史卷、补题探针）都是这个口径。
            // 早先这里图省事用了原题干 hash，两套口径对不上：补题探针拿渲染 hash 去挡，
            // 这边认不出来，于是同一道题被当成新题又贴了一遍，34 题的卷子变成 46 题。
            val renderHash = stableHash(rendered.first)
            val bypass = ctx.allowReuse && round >= 2
            if (!bypass && renderHash in ctx.blocked) continue
            // 注意：纸内去重只在「真的要印」时才记账。
            // 若在这里之前就记进 paperSeen，第 0 轮被历史挡掉的题到了第 2 轮也放不出来了。
            paperSeen.add(srcHash)
            ctx.blocked.add(srcHash)
            ctx.blocked.add(renderHash)
            out.add(
                GeneratedQuestion(
                    no = 0,
                    type = type,
                    stem = rendered.first,
                    answer = rendered.second,
                    topic = "科学·${q.topic}",
                )
            )
        }
        round++
    }
    return out
}

/** 选择题：选项洗牌 → 重新编 A/B/C → 答案字母按新位置算 */
private fun renderScienceChoice(q: ScienceQuestion, rng: Random): Pair<String, String>? {
    val key = q.answer.trim().uppercase()
    val correctIdx = key.firstOrNull()?.let { it - 'A' } ?: return null
    if (correctIdx !in q.options.indices) return null

    val order = shuffledCopy(q.options.indices.toList(), rng)
    val opts = order.mapIndexed { i, origin -> "${SCIENCE_LETTERS[i]}. ${q.options[origin].trim()}" }
    val newIdx = order.indexOf(correctIdx)
    // 答案把选项文字一起带上：家长拿着答案页就能对，不用再回头翻原卷；
    // 顺便让调试体检能机器核对「字母指向的文字 == 源题的正确选项」
    val answer = "（${SCIENCE_LETTERS[newIdx]}）${q.options[correctIdx].trim()}"
    return "【${q.topic}】${q.stem.trim()}\n${opts.joinToString("　")}" to
        withScienceExplain(answer, q.explain)
}

/** 判断题：题干后面留一个括号，答案 √ / × */
private fun renderScienceJudge(q: ScienceQuestion): Pair<String, String> {
    val stem = "${q.stem.trim()}（　）"
    return stem to withScienceExplain(q.answer.trim(), q.explain)
}

/** 填空题：题干自带括号，答案直接是文字 */
private fun renderScienceBlank(q: ScienceQuestion): Pair<String, String> {
    val stem = q.stem.trim()
    return stem to withScienceExplain(q.answer.trim(), q.explain)
}

// ------------------------------------------------------------------ 三个题型

object ScienceChoiceGenerator : QuestionGenerator {
    override val type = QuestionType.SCIENCE_CHOICE
    override val subject = Subject.SCIENCE

    // 不覆写 usableIn：科学不跨科，勾了科学才出现，走默认的「subject in subjects」即可

    override fun generate(ctx: GenContext): List<GeneratedQuestion> =
        drawScience(ctx, ScienceQuestion.Kind.CHOICE, type)
}

object ScienceJudgeGenerator : QuestionGenerator {
    override val type = QuestionType.SCIENCE_JUDGE
    override val subject = Subject.SCIENCE

    override fun generate(ctx: GenContext): List<GeneratedQuestion> =
        drawScience(ctx, ScienceQuestion.Kind.JUDGE, type)
}

object ScienceBlankGenerator : QuestionGenerator {
    override val type = QuestionType.SCIENCE_BLANK
    override val subject = Subject.SCIENCE

    override fun generate(ctx: GenContext): List<GeneratedQuestion> =
        drawScience(ctx, ScienceQuestion.Kind.BLANK, type)
}
