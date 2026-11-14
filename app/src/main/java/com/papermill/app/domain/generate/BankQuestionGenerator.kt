package com.papermill.app.domain.generate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

data class BankQuestion(
    val minGrade: Int,
    val subject: Subject,
    val topic: String,
    val stem: String,
    val options: List<String>,
    val answer: String,
    val analysis: String,
)

/**
 * 内置自编题库。
 *
 * ============================================================
 * 为什么不是「市面上的题目」
 * ============================================================
 * 教辅、菁优网、学科网等题库都有版权，抓取或内置进 App 会侵权，
 * 公开上架更是直接踩线。这里全部是**按知识点自编**的题目（选择题 / 判断题），
 * 不复制任何原题。可以按年级、科目、知识点随意组合，
 * 数据放在 assets 里，随 App 走，完全离线。
 */
object BankQuestionSource {

    private var questions: List<BankQuestion> = emptyList()

    fun loadFromAssets(ctx: Context): Int = runCatching {
        load(ctx.assets.open("bank/builtin_questions.json").bufferedReader().use { it.readText() })
    }.getOrDefault(0)

    /** 注意根是 {"schema":…, "questions":[…]}，不是裸数组；之前写成 JSONArray(json) 会静默返回 0 题 */
    fun load(json: String): Int {
        val arr = JSONObject(json).optJSONArray("questions") ?: JSONArray()
        val list = ArrayList<BankQuestion>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val optsArray: JSONArray? = o.optJSONArray("options")
            val opts = ArrayList<String>()
            if (optsArray != null) {
                for (j in 0 until optsArray.length()) opts.add(optsArray.optString(j))
            }
            list.add(
                BankQuestion(
                    minGrade = o.optInt("grade", 1),
                    subject = runCatching { Subject.valueOf(o.optString("subject")) }.getOrDefault(Subject.MATH),
                    topic = o.optString("topic"),
                    stem = o.optString("stem"),
                    options = opts,
                    answer = o.optString("answer"),
                    analysis = o.optString("analysis"),
                )
            )
        }
        questions = list
        return list.size
    }

    fun size(): Int = questions.size

    /** 某年级 + 所选科目能用的题（含低年级的复习题），供生成器轮转抽题 */
    fun pool(grade: Int, subjects: Set<Subject>): List<BankQuestion> =
        questions.filter {
            it.minGrade <= grade && (subjects.isEmpty() || it.subject in subjects)
        }

    fun pick(grade: Int, subjects: Set<Subject>, rng: Random): BankQuestion? {
        val pool = pool(grade, subjects)
        if (pool.isEmpty()) return null
        return pool[rng.nextInt(pool.size)]
    }
}

/**
 * 题库题作为一种「题型」接进出卷引擎，这样组卷逻辑一行都不用改。
 *
 * 与计算题不同，题库是**有限集合**（三年级数学可能只有 9 道可用）。
 * 如果沿用 BaseGenerator 的「随机抽 + 题干 hash 去重」，要 20 题只会出 9 题。
 * 所以这里改成：每轮把可用题洗牌过一遍，并把选项顺序重排（答案字母同步换算），
 * 这样同一道题在第二轮会变成不同的题干、且答案仍然正确 —— 既凑够题量，
 * 又不会在同一张卷里印出两道一模一样的题。
 */
object BankQuestionGenerator : BaseGenerator() {

    override val type = QuestionType.KNOWLEDGE
    override val subject = Subject.MATH
    override val topic = "知识点综合"

    // 题库里数学/语文/英语/物理/化学都有，勾哪科就出哪科的题
    override val crossSubject = true

    private val LETTERS = arrayOf("A", "B", "C", "D", "E", "F")

    // 题库是跨科通用的，所以不按 subject.availableFor 过滤
    override fun supports(grade: Int): Boolean = grade in 1..9

    override fun generate(ctx: GenContext): List<GeneratedQuestion> {
        val pool = BankQuestionSource.pool(ctx.grade, ctx.subjects)
        // 题库资源万一没读到，退回兜底常识题，保证点了不会没反应
        if (pool.isEmpty()) return super.generate(ctx)

        val out = ArrayList<GeneratedQuestion>(ctx.count)
        var round = 0
        while (out.size < ctx.count && round < 8) {
            for (q in shuffled(pool, ctx.rng)) {
                if (out.size >= ctx.count) break
                val (stem, answer) = render(q, ctx.rng)
                if (stem.isEmpty() || !ctx.blocked.add(stableHash(stem))) continue
                out.add(
                    GeneratedQuestion(
                        no = 0,
                        type = type,
                        stem = stem,
                        answer = answer,
                        topic = "${q.subject.label}·${q.topic}",
                    )
                )
            }
            round++
        }
        return out
    }

    /** 去掉原选项字母 → 洗牌 → 重新编 A/B/C，并同步换算正确答案的字母 */
    private fun render(q: BankQuestion, rng: Random): Pair<String, String> {
        val body = "【${q.topic}】${q.stem}"
        if (q.options.isEmpty()) return "$body（　）" to q.answer

        val contents = q.options.map {
            it.replace(Regex("^[A-Fa-f][.、．:：]\\s*"), "")
        }
        val correctIdx = q.answer.trim().firstOrNull()?.uppercaseChar()
            ?.let { it - 'A' }
            ?.takeIf { it in contents.indices }
            ?: -1

        val order = shuffled(contents.indices.toList(), rng)
        val opts = order.mapIndexed { i, idx -> "${LETTERS.getOrElse(i) { (i + 65).toChar().toString() }}. ${contents[idx]}" }
        val answer = if (correctIdx >= 0) {
            LETTERS.getOrElse(order.indexOf(correctIdx)) { "A" }
        } else q.answer

        return "$body　${opts.joinToString("　")}" to answer
    }

    /** java.util.Random 版 Fisher-Yates，避免依赖 kotlin.random 的包装 */
    private fun <T> shuffled(src: List<T>, rng: Random): List<T> {
        val a = ArrayList(src)
        for (i in a.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = a[i]
            a[i] = a[j]
            a[j] = tmp
        }
        return a
    }

    override fun make(ctx: GenContext): Pair<String, String> {
        val q = BankQuestionSource.pick(ctx.grade, ctx.subjects, ctx.rng)
            ?: return FALLBACK[ctx.rng.nextInt(FALLBACK.size)]
        return render(q, ctx.rng)
    }

    private val FALLBACK = listOf(
        "【常识】一周有几天？（　）" to "7 天",
        "【常识】一年有几个月？（　）" to "12 个月",
        "【常识】一个三角形有几条边？（　）" to "3 条",
        "【常识】1 米等于多少厘米？（　）" to "100 厘米",
        "【常识】1 小时等于多少分钟？（　）" to "60 分钟",
        "【常识】正方形的四个角都是什么角？（　）" to "直角",
    )
}
