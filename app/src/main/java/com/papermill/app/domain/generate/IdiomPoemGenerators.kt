package com.papermill.app.domain.generate

import com.papermill.app.domain.content.Idioms
import com.papermill.app.domain.content.Poem
import com.papermill.app.domain.content.Poems
import java.util.Random

/**
 * v0.8.0 成语 / 古诗题型：从内置库里**随机抽题**，不是每次现编。
 *
 * 这正是用户要的「题库型」：库越大，同一题型能出的题越多；
 * 追加内容只要往 [Idioms.items] / [Poems.items] 里加行，四个题型立刻能用上新内容。
 *
 * 所有答案都在抽题那一刻算定（选项洗牌后正确字母同步换算），
 * 不会出现「选项换了、答案没换」的错题。
 */

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

private fun letterAt(index: Int): String = ('A'.code + index).toChar().toString()

/** 把选项拼成「A. xxx　B. xxx」的多行题干 */
private fun optionsText(options: List<String>): String =
    options.mapIndexed { i, s -> "${letterAt(i)}. $s" }.joinToString("　")

// ------------------------------------------------------------------ 成语填空

object IdiomFillGenerator : BaseGenerator() {
    override val type = QuestionType.IDIOM_FILL
    override val subject = Subject.CHINESE
    override val minGrade = 3
    override val maxGrade = 6
    override val topic = "成语积累"

    override fun make(ctx: GenContext): Pair<String, String> {
        val pool = Idioms.pool(ctx.grade)
        check(pool.size >= 8) { "成语库不足" }
        val idiom = pool[ctx.rng.nextInt(pool.size)]
        val chars = idiom.word.toCharArray()
        // 不挖第一个字：四字成语的首字往往是辨识点，挖掉以后基本只能靠蒙
        val idx = 1 + ctx.rng.nextInt(chars.size - 1)
        val missing = chars[idx]
        chars[idx] = '＿'
        val stem = "把成语补充完整：${String(chars)}（　）"
        return stem to "$missing（${idiom.word}：${idiom.meaning}）"
    }
}

// ------------------------------------------------------------------ 成语释义

object IdiomMeaningGenerator : BaseGenerator() {
    override val type = QuestionType.IDIOM_MEANING
    override val subject = Subject.CHINESE
    override val minGrade = 3
    override val maxGrade = 6
    override val topic = "成语理解"

    override fun make(ctx: GenContext): Pair<String, String> {
        val pool = Idioms.pool(ctx.grade)
        check(pool.size >= 16) { "成语库不足" }
        val idiom = pool[ctx.rng.nextInt(pool.size)]

        // 干扰项优先取「不同主题」的释义：同一主题里常有一对近义词，
        // 放一个题里会出现两个都对的情况，题就废了
        val others = pool
            .filter { it.word != idiom.word && it.meaning != idiom.meaning }
            .sortedByDescending { if (it.tag != idiom.tag) 1 else 0 }
            .let { list ->
                // 前一半是异主题，打散后取 3 条，够用且不呆板
                val head = list.take(list.size / 2)
                shuffledBy(head, ctx.rng)
            }
            .map { it.meaning }
            .distinct()
            .take(3)

        check(others.size >= 3) { "干扰项不足" }
        val options = shuffledBy(others + idiom.meaning, ctx.rng)
        val answer = letterAt(options.indexOf(idiom.meaning))
        return "「${idiom.word}」的意思是（　）\n${optionsText(options)}" to answer
    }
}

// ------------------------------------------------------------------ 古诗接句

object PoemFillGenerator : BaseGenerator() {
    override val type = QuestionType.POEM_FILL
    override val subject = Subject.CHINESE
    override val minGrade = 1
    override val maxGrade = 6
    override val topic = "古诗积累"

    override fun make(ctx: GenContext): Pair<String, String> {
        // 只要至少两句的诗；单句节选没法出接句题
        val pool = Poems.pool(ctx.grade).filter { it.lines.size >= 2 }
        check(pool.isNotEmpty()) { "古诗库不足" }
        val poem = pool[ctx.rng.nextInt(pool.size)]
        val i = ctx.rng.nextInt(poem.lines.size - 1)
        val prev = poem.lines[i]
        val next = poem.lines[i + 1]
        val stem = "《${poem.title}》：$prev 下一句是（　）"
        return stem to next
    }
}

// ------------------------------------------------------------------ 古诗常识

object PoemMatchGenerator : BaseGenerator() {
    override val type = QuestionType.POEM_MATCH
    override val subject = Subject.CHINESE
    override val minGrade = 2
    override val maxGrade = 6
    override val topic = "古诗常识"

    /** 朝代选项池：只放本库真出现过的，免得出现「夏朝」这种离谱干扰项 */
    private val dynasties = listOf("汉", "三国", "南北朝", "唐", "宋", "元", "明", "清")

    override fun make(ctx: GenContext): Pair<String, String> {
        val pool = Poems.pool(ctx.grade)
        check(pool.isNotEmpty()) { "古诗库不足" }
        val poem = pool[ctx.rng.nextInt(pool.size)]
        val line = poem.lines[ctx.rng.nextInt(poem.lines.size)]

        return when (ctx.rng.nextInt(3)) {
            // 诗句 → 作者
            0 -> {
                val wrong = shuffledBy(
                    Poems.authors.filter { it != poem.author }, ctx.rng
                ).take(3)
                check(wrong.size >= 3) { "干扰项不足" }
                val options = shuffledBy(wrong + poem.author, ctx.rng)
                "「$line」出自哪位诗人？（　）\n${optionsText(options)}" to
                    "(${letterAt(options.indexOf(poem.author))}) ${poem.author}"
            }
            // 诗句 → 篇名
            1 -> {
                val wrong = shuffledBy(
                    pool.map { it.title }.filter { it != poem.title }.distinct(), ctx.rng
                ).take(3)
                check(wrong.size >= 3) { "干扰项不足" }
                val options = shuffledBy(wrong + poem.title, ctx.rng)
                "「$line」出自哪一首？（　）\n${optionsText(options)}" to
                    "(${letterAt(options.indexOf(poem.title))}) 《${poem.title}》"
            }
            // 诗句 → 朝代
            else -> {
                val wrong = shuffledBy(
                    dynasties.filter { it != poem.dynasty }, ctx.rng
                ).take(3)
                check(wrong.size >= 3) { "干扰项不足" }
                val options = shuffledBy(wrong + poem.dynasty, ctx.rng)
                "「$line」（${poem.author}）写于哪个朝代？（　）\n${optionsText(options)}" to
                    "(${letterAt(options.indexOf(poem.dynasty))}) ${poem.dynasty}"
            }
        }
    }
}

/** 给内容总览页用的规模信息（避免 UI 直接依赖具体数据类） */
object IdiomPoemStats {
    val idiomCount: Int get() = Idioms.items.size
    val poemCount: Int get() = Poems.items.size
    val poemLineCount: Int get() = Poems.items.sumOf { it.lines.size }
}
