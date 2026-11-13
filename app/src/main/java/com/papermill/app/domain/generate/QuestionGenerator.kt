package com.papermill.app.domain.generate

import java.util.Random

data class GenContext(
    val grade: Int,
    val count: Int,
    val difficulty: Int,
    val rng: Random,
    /** 已出现过的题干 hash：同卷内 + 最近几张历史卷，用来防重复 */
    val blocked: MutableSet<Long>,
    /** 本次出卷勾选的科目（交叉学科时用；空 = 不限制） */
    val subjects: Set<Subject> = emptySet(),
)

interface QuestionGenerator {
    val type: QuestionType
    val subject: Subject
    val minGrade: Int get() = 1
    val maxGrade: Int get() = 9

    fun supports(grade: Int): Boolean =
        subject.availableFor(grade) && grade in minGrade..maxGrade

    fun generate(ctx: GenContext): List<GeneratedQuestion>
}

/**
 * 绝大多数题型都是「随机造一题」的循环，所以抽一层基类。
 *
 * 三个硬约定：
 *  1. 答案必须是生成过程的**副产品**（[make] 同时返回题干和答案），不是事后重新求值；
 *  2. `stableHash(stem)` 做去重，重试上限 = count*20，取不到就如实少给，绝不死循环；
 *  3. `runCatching` 兜住单个生成器的意外（比如除数取到 0），坏题直接丢弃重来。
 */
abstract class BaseGenerator : QuestionGenerator {

    /** 返回 题干 to 答案 */
    abstract fun make(ctx: GenContext): Pair<String, String>

    open val topic: String = ""

    override fun generate(ctx: GenContext): List<GeneratedQuestion> {
        val out = ArrayList<GeneratedQuestion>(ctx.count)
        val maxTries = (ctx.count * 20).coerceAtLeast(60)
        var tries = 0
        while (out.size < ctx.count && tries < maxTries) {
            tries++
            val pair = runCatching { make(ctx) }.getOrNull() ?: continue
            val stem = pair.first.trim()
            val answer = pair.second.trim()
            if (stem.isEmpty() || answer.isEmpty()) continue
            if (!ctx.blocked.add(stableHash(stem))) continue
            out.add(GeneratedQuestion(no = 0, type = type, stem = stem, answer = answer, topic = topic))
        }
        return out
    }
}

/** FNV-1a 64 位，题干去空白后取哈希。用途：同卷去重、历史卷去重、错题去重。 */
fun stableHash(text: String): Long {
    val normalized = text.trim().replace(Regex("\\s+"), "")
    // FNV 的偏移量超过 Long.MAX，必须用 ULong 写，否则 Kotlin 字面量越界
    var hash: ULong = 0xcbf29ce484222325UL
    for (ch in normalized) {
        hash = hash xor ch.code.toULong()
        hash *= 0x100000001b3UL
    }
    return hash.toLong()
}

object GeneratorRegistry {

    private val all: List<QuestionGenerator> = listOf(
        // 小学数学
        OralGenerator, VerticalGenerator, ExpressionGenerator, FractionGenerator,
        EquationGenerator, WordProblemGenerator,
        // 初中数学
        RationalGenerator, AlgebraGenerator, RadicalGenerator,
        // 语文 / 英语
        PinyinGenerator, HanziGenerator, DictationGenerator, EnglishWordGenerator,
        // 理化
        PhysicsGenerator, ChemistryGenerator,
        // 内置自编题库
        BankQuestionGenerator,
        // v0.4.0：外部资源站题型规则转成的自编生成器（放在末尾，保证默认卷仍是原来的主题型）
        FillBlankGenerator, MakeTenGenerator, ChainCalcGenerator, MixedCalcGenerator,
        TensCalcGenerator, TwoDigitGenerator, UnitConvertGenerator, CompareGenerator,
        NumberPatternGenerator, CountingGenerator, LetterOrderGenerator,
    )

    fun availableFor(grade: Int, subject: Subject): List<QuestionType> =
        availableFor(grade, listOf(subject))

    /**
     * 交叉学科：返回所有勾选科目能出的题型。
     * KNOWLEDGE（自编题库）是跨科通用题型，只要勾了任意科目就出现。
     */
    fun availableFor(grade: Int, subjects: Collection<Subject>): List<QuestionType> =
        all.filter {
            it.supports(grade) && (it.type == QuestionType.KNOWLEDGE || it.subject in subjects)
        }.map { it.type }.distinct()

    fun subjectOf(type: QuestionType): Subject? = all.firstOrNull { it.type == type }?.subject

    fun of(type: QuestionType, grade: Int): QuestionGenerator? =
        all.firstOrNull { it.type == type && it.supports(grade) }

    /** 每个大题用独立种子，改动某一题的数量不会牵动其它大题的题目 */
    fun sectionSeed(paperSeed: Long, sectionIndex: Int): Long =
        paperSeed xor (sectionIndex * 0x9E3779B97F4A7C15UL.toLong())
}
