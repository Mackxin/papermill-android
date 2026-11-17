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
    /** 按单元出卷时限定在这些知识点里（空 = 不限制） */
    val topicIds: Set<String> = emptySet(),
    /**
     * 学期：0 不限 / 1 上册 / 2 下册（v0.19.0）。
     *
     * 只有**真带学期数据**的题源会读它（课标题库的知识点有 `semester`）。
     * 口算、字词这些按年级分段、本来就分不出上下册，传进来也不会变少 ——
     * 硬按字段猜反而会造出错误的分册。
     */
    val term: Int = 0,
    /**
     * 题库型生成器专用：池子被 [blocked] 挡光时，是否允许放宽「历史避让」。
     *
     *  - 正常出卷 = true：题库就十几道题，连着出两张同年级卷子，第二张会被历史避让挡光，
     *    于是一个大题整块消失。允许放宽，宁可重印两道旧题也要把卷子填满。
     *  - 「补满这一页」的试排探针 = false：它要的是**真正的追加**，
     *    池子干了就该返回空，让界面老实说「题已经全部用上了」，而不是把旧题复制一份贴上。
     */
    val allowReuse: Boolean = true,
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

    /**
     * 返回 题干 to 答案。
     *
     * 改成 open（v0.16.0）：看图题只实现 [makeFigure]，没有纯文字的版本，
     * 让它强制实现一个永远用不到的 make() 只会逼出一行 `error(...)` 占位。
     */
    open fun make(ctx: GenContext): Pair<String, String> =
        error("${this::class.simpleName} 没实现 make()，也不是看图题生成器")

    /**
     * 带配图的版本（v0.16.0 看图题用）。
     *
     * 默认返回 null，走 [make]。看图题重写它、把图形数据一起带出来 ——
     * 图不是「题干里的字符」，出题阶段必须**同时**定下数字和图形，
     * 否则答案算的是一回事、图上画的是另一回事（这类错印出来没人能发现）。
     */
    open fun makeFigure(ctx: GenContext): FigureQuestion? = null

    open val topic: String = ""

    override fun generate(ctx: GenContext): List<GeneratedQuestion> {
        val out = ArrayList<GeneratedQuestion>(ctx.count)
        val maxTries = (ctx.count * 20).coerceAtLeast(60)
        var tries = 0
        while (out.size < ctx.count && tries < maxTries) {
            tries++
            val figQ = runCatching { makeFigure(ctx) }.getOrNull()
            val stem: String
            val answer: String
            if (figQ != null) {
                stem = figQ.stem.trim()
                answer = figQ.answer.trim()
            } else {
                val pair = runCatching { make(ctx) }.getOrNull() ?: continue
                stem = pair.first.trim()
                answer = pair.second.trim()
            }
            if (stem.isEmpty() || answer.isEmpty()) continue
            // 看图题的去重键必须带上图形数据：钟面题、统计图题的题干是**固定句式**
            // （「看钟面，写出时刻」），只有数字在图上。只按题干去重的话，
            // 同一张卷子出 8 道钟面题会挡住后 7 道，卷面剩一道、后面全白。
            if (!ctx.blocked.add(stableHash(stem + figureKey(figQ?.figure)))) continue
            out.add(
                GeneratedQuestion(
                    no = 0,
                    type = type,
                    stem = stem,
                    answer = answer,
                    topic = topic,
                    figure = figQ?.figure,
                )
            )
        }
        return out
    }
}

/**
 * 看图题的生成结果：题干 + 答案 + 图形数据三件套。
 *
 * 三样必须在同一次随机里一次算完 —— 分两步生成（先出题干、再补图）
 * 会让「图上 5 个、答案写 7」这种错悄悄印到纸上。
 */
data class FigureQuestion(
    val stem: String,
    val answer: String,
    val figure: FigureSpec,
)

/**
 * 把配图数据压成一段可比较文本，拼进去重键。
 *
 * 只取「真正决定这道题是什么」的字段：几组几个、时针分针、统计图的分类与数值。
 * 不放 caption 这类展示字段 —— 改个说法不该被当成新题。
 */
private fun figureKey(spec: FigureSpec?): String {
    if (spec == null) return ""
    return buildString {
        append(spec.kind)
        append(spec.groups.joinToString(","))
        append('|')
        append(spec.shapes.joinToString(","))
        append('|')
        append(spec.labels.joinToString(","))
        append('|')
        append(spec.values.joinToString(","))
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
        // v0.11.0 小学科学（内容全自编，见 domain/content/ScienceBank.kt）
        ScienceChoiceGenerator, ScienceJudgeGenerator, ScienceBlankGenerator,
        // v0.16.0 看图题（配图由 core/pdf/figure/FigurePainter 现画，不打包图片）
        // 放在最后：默认卷的「智能配比」按列表顺序取前 N 种，追加在末尾
        // 才保证用户原来出过的卷子题型不变，要看图题自己勾选
        FigureCountGenerator, FigureExprGenerator, ClockReadGenerator, BarChartGenerator,
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
