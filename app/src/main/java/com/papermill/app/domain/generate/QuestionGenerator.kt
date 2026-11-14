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

    /**
     * 这个题型是否「跨科目」。
     *
     * 大部分题型绑死一科（口算只给数学、音标只给英语），勾了那科才出现。
     * 但内置题库（自编题库 / 课标题库）内容本身就是各科混装的：
     * 勾物理也该能出物理题，不能因为它的 `subject` 字段写着 MATH 就在物理卷里消失。
     * 这类题型把 [crossSubject] 置 true，再由 [usableIn] 决定「当前勾选里到底有没有货」。
     */
    val crossSubject: Boolean get() = false

    fun supports(grade: Int): Boolean =
        subject.availableFor(grade) && grade in minGrade..maxGrade

    /** 勾了 [subjects] 这些科目时，这个题型该不该出现在参数页 */
    fun usableIn(subjects: Collection<Subject>): Boolean =
        crossSubject || subject in subjects

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
        // v0.5.0 英语题型（题型模式自研、词表句子自编）
        WordScrambleGenerator, SentenceOrderGenerator, ClozeGenerator,
        // v0.5.0 主题识字（自编词库 + 校对过的拼音）
        ThemePinyinGenerator, ThemeSortGenerator, ThemeGroupGenerator,
        // v0.7.0 音标 / 语法 / 课本同步（内置内容，见 domain/content）
        PhoneticPickGenerator, PhoneticWriteGenerator, GrammarGenerator,
        TextbookHanziGenerator, TextbookWordGenerator,
        // v0.8.0 成语 / 古诗题库题型（内容见 domain/content/Idioms.kt、Poems.kt）
        IdiomFillGenerator, IdiomMeaningGenerator, PoemFillGenerator, PoemMatchGenerator,
        // v0.8.0 口算变式（找未知项 / 带余数除法）
        OralMissingGenerator, OralRemainderGenerator,
        // v0.9.0 课标题库（cn-math-taxonomy · CC BY-NC 4.0，内容见 domain/content/CurriculumBank.kt）
        CurricBlankGenerator, CurricChoiceGenerator,
    )

    /** 关于页实时统计题型总数 */
    val count: Int get() = all.size

    fun availableFor(grade: Int, subject: Subject): List<QuestionType> =
        availableFor(grade, listOf(subject))

    /**
     * 交叉学科：返回所有勾选科目能出的题型。
     *
     * 是否「该出现」交给每个生成器自己判断（[QuestionGenerator.usableIn]）：
     *  - 普通题型：勾了它的科目就出现；
     *  - 跨科题型（自编题库 / 课标题库）：得真的挑得出题才出现 ——
     *    语文没有填空题，勾语文时「知识点填空」就不该摆在那儿让人点了没反应。
     */
    fun availableFor(grade: Int, subjects: Collection<Subject>): List<QuestionType> =
        all.filter { it.supports(grade) && it.usableIn(subjects) }.map { it.type }.distinct()

    fun subjectOf(type: QuestionType): Subject? = all.firstOrNull { it.type == type }?.subject

    fun of(type: QuestionType, grade: Int): QuestionGenerator? =
        all.firstOrNull { it.type == type && it.supports(grade) }

    /** 每个大题用独立种子，改动某一题的数量不会牵动其它大题的题目 */
    fun sectionSeed(paperSeed: Long, sectionIndex: Int): Long =
        paperSeed xor (sectionIndex * 0x9E3779B97F4A7C15UL.toLong())
}
