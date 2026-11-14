package com.papermill.app.domain.generate

import com.papermill.app.domain.content.Grammar
import com.papermill.app.domain.content.GrammarItem
import com.papermill.app.domain.content.Phoneme
import com.papermill.app.domain.content.Phonics
import com.papermill.app.domain.content.Textbook
import java.util.Random

/**
 * v0.7.0 内置内容生成器：音标、语法、课本同步。
 *
 * ============================================================
 * 为什么不能直接用 [BaseGenerator]
 * ============================================================
 * BaseGenerator 是「随机造一题 + 题干 hash 去重」，适合口算这类无限题源。
 * 但内置内容是**有限集合**（43 道语法题 / 48 个音标 / 每级 20 个字），
 * 直接套用会踩两个坑，都实测遇到过：
 *
 *  1. **同题重复**：语法题库里有一道「They ___ in the classroom.」，
 *     随机抽两次时选项打乱顺序不同 → 题干 hash 不同 → 去重失效，
 *     同一张卷子上出现了两道一模一样的题。
 *  2. **答案有歧义**：音标题原本是「单词 bag 中含有哪个音标」，
 *     干扰项里放了 /ɡ/ —— 可 bag 本身就同时含 /b/ 和 /ɡ/，
 *     两个选项都对，学生怎么写都算错一道。
 *
 * 所以这里统一改成 [FinitePoolGenerator]：洗牌整轮、逐个取用，
 * 保证一张卷里同一道素材只出现一次；题目形态也改成无歧义的问法。
 */

private val LETTERS = arrayOf("A", "B", "C", "D", "E", "F")

/** java.util.Random 版 Fisher-Yates（不用 kotlin.random，避免多引一层包装） */
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

/** 把「正确内容 + 干扰内容」洗成 A/B/C/D，返回 选项串 与 答案字母 */
private fun buildOptions(
    correct: String,
    distractors: List<String>,
    rng: Random,
): Pair<String, String> {
    val contents = (listOf(correct) + distractors).distinct()
    val order = shuffled(contents.indices.toList(), rng)
    val opts = order.mapIndexed { i, idx ->
        "${LETTERS.getOrElse(i) { 'A' + i }}. ${contents[idx]}"
    }
    val answer = LETTERS.getOrElse(order.indexOf(0)) { "A" }
    return opts.joinToString("　") to answer
}

/**
 * 有限题源基类：每轮把可用素材洗牌后逐个消费，取够题量就停。
 *
 * 只要 `count <= 素材数`，同一张卷里绝不会出现两道同源题；
 * 超过时才会开第二轮（选项重排后题干不同，仍然不是重复题）。
 */
abstract class FinitePoolGenerator<T> : QuestionGenerator {

    /** 知识点标签，错题本按它归类（与 BaseGenerator 的同名字段对齐） */
    open val topic: String = ""

    /** 当前年级可用的素材 */
    abstract fun poolFor(grade: Int): List<T>

    /** 把一条素材渲染成「题干 to 答案」 */
    abstract fun render(item: T, ctx: GenContext): Pair<String, String>

    override fun generate(ctx: GenContext): List<GeneratedQuestion> {
        val pool = poolFor(ctx.grade)
        if (pool.isEmpty()) return emptyList()

        // 只走一遍：素材洗完逐个取，取完就停，绝不开第二轮。
        // 开第二轮看起来能凑够题量，代价是同一张卷上出现两道一模一样的题 ——
        // 少给几道（页脚会提示题量不足）远好过多印两道重复题。
        val out = ArrayList<GeneratedQuestion>(ctx.count)
        for (item in shuffled(pool, ctx.rng)) {
            if (out.size >= ctx.count) break
            val pair = runCatching { render(item, ctx) }.getOrNull() ?: continue
            val stem = pair.first.trim()
            val answer = pair.second.trim()
            if (stem.isEmpty() || answer.isEmpty()) continue
            if (!ctx.blocked.add(stableHash(stem))) continue
            out.add(
                GeneratedQuestion(no = 0, type = type, stem = stem, answer = answer, topic = topic)
            )
        }
        return out
    }
}

// ------------------------------------------------------------------ 音标

/**
 * 音标辨认：问「这个单词里的**元音**音标是哪个」。
 *
 * 只做元音，是因为元音在这批例词里各只有一个（双元音算一个整体），
 * 而辅音一个词常有 2~3 个 —— 「bag 中含有哪个音标」把 /b/ 和 /ɡ/ 一起放进选项，
 * 两个都对，题目就废了。元音题配上「同类干扰项」（短元音只跟短元音混），
 * 唯一解是确定的，可以放心自动判分。
 */
object PhoneticPickGenerator : FinitePoolGenerator<Phoneme>() {

    override val type = QuestionType.PHONETIC_PICK
    override val subject = Subject.ENGLISH
    override val minGrade = 3
    override val topic = "英语·音标"

    override fun poolFor(grade: Int): List<Phoneme> = Phonics.vowels

    override fun render(item: Phoneme, ctx: GenContext): Pair<String, String> {
        val word = item.examples[ctx.rng.nextInt(item.examples.size)]
        // 干扰项只取同类元音：短元音配短元音，双元音配双元音
        val sameKind = Phonics.vowels.filter { it.kind == item.kind && it.ipa != item.ipa }
        val distractors = shuffled(sameKind, ctx.rng).take(3).map { it.ipa }
        val (opts, answer) = buildOptions(item.ipa, distractors, ctx.rng)
        return "单词 $word 中的元音音标是（　）　$opts" to answer
    }
}

/**
 * 看音标写单词：给音标 + 中文，写出对应单词。
 *
 * 中文提示是必需的 —— 只给音标的话，/iː/ 对应 see / tea / green 三个词都算对，
 * 自动判分就没法做了。答案取每个音素的第一个例词。
 */
object PhoneticWriteGenerator : FinitePoolGenerator<Phoneme>() {

    override val type = QuestionType.PHONETIC_WRITE
    override val subject = Subject.ENGLISH
    override val minGrade = 3
    override val topic = "英语·音标"

    override fun poolFor(grade: Int): List<Phoneme> = Phonics.all

    override fun render(item: Phoneme, ctx: GenContext): Pair<String, String> =
        "根据音标写出单词：${item.ipa}　（${item.zh}）" to item.examples.first()
}

// ------------------------------------------------------------------ 语法

/**
 * 语法选择：从 [Grammar.items] 按年级取题，选项打乱后生成 4 选 1。
 *
 * 答案里带一句「为什么」，直接印在答案页 —— 语法题光给答案没有意义。
 */
object GrammarGenerator : FinitePoolGenerator<GrammarItem>() {

    override val type = QuestionType.GRAMMAR
    override val subject = Subject.ENGLISH
    override val minGrade = 3
    override val topic = "英语·语法"

    override fun poolFor(grade: Int): List<GrammarItem> = Grammar.itemsFor(grade)

    override fun render(item: GrammarItem, ctx: GenContext): Pair<String, String> {
        val distractors = item.options.filter { it != item.answer }
        val (opts, letter) = buildOptions(item.answer, distractors, ctx.rng)
        return "【${item.point}】${item.stem}　$opts" to
            "$letter. ${item.answer}（${item.explain}）"
    }
}

// ------------------------------------------------------------------ 课本同步

/** 课本生字注音：给汉字写拼音，抽当前年级及以下累积的字表 */
object TextbookHanziGenerator : FinitePoolGenerator<com.papermill.app.domain.content.GradeHanzi>() {

    override val type = QuestionType.TEXTBOOK_HANZI
    override val subject = Subject.CHINESE
    override val minGrade = 1
    override val topic = "语文·课本生字"

    override fun poolFor(grade: Int) = Textbook.hanziFor(grade)

    override fun render(
        item: com.papermill.app.domain.content.GradeHanzi,
        ctx: GenContext,
    // 大题标题已经写了「课本生字注音」，题干再重复一遍会让整页都是同一句话
    ): Pair<String, String> = "${item.hanzi}　（　　　）" to item.pinyin
}

/** 课本单词默写：给中文写英语单词，抽当前年级及以下的累积词表 */
object TextbookWordGenerator : FinitePoolGenerator<com.papermill.app.domain.content.GradeEnWord>() {

    override val type = QuestionType.TEXTBOOK_WORD
    override val subject = Subject.ENGLISH
    override val minGrade = 3
    override val topic = "英语·课本单词"

    override fun poolFor(grade: Int) = Textbook.wordsFor(grade)

    override fun render(
        item: com.papermill.app.domain.content.GradeEnWord,
        ctx: GenContext,
    // 同上：指令放在大题标题里，题干只留中文，卷面才像真的默写纸
    ): Pair<String, String> = "${item.zh}　＿＿＿＿＿＿" to item.en
}
