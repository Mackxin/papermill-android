package com.papermill.app.domain.generate

import java.util.Random

/**
 * 出卷编排：把「用户选的规格」变成「一张有题号、有分值的卷子」。
 *
 * 种子三层，保证可复现：
 *   卷子 seed → 每个大题 seed = paperSeed xor (sectionIndex * 常数) → 每题顺序推进同一个 Random。
 * 同 spec + 同 seed 在任何手机上出同一张卷；点「换一套」只换 seed。
 */
object PaperComposer {

    private val CN_NUM = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    fun compose(spec: PaperSpec, historyHashes: Set<Long> = emptySet()): GeneratedPaper {
        val blocked = historyHashes.toMutableSet()
        val sections = ArrayList<GeneratedSection>()
        var nextNo = 1

        val ordered = spec.counts.entries
            .filter { it.value > 0 }
            .sortedBy { it.key.ordinal }

        ordered.forEachIndexed { index, entry ->
            val type = entry.key
            val count = entry.value.coerceIn(1, 200)
            val generator = GeneratorRegistry.of(type, spec.grade) ?: return@forEachIndexed

            val rng = Random(GeneratorRegistry.sectionSeed(spec.seed, index))
            val ctx = GenContext(
                grade = spec.grade,
                count = count,
                difficulty = spec.difficulty,
                rng = rng,
                blocked = blocked,
                subjects = spec.effectiveSubjects().toSet(),
            )
            val questions = generator.generate(ctx)
                .mapIndexed { i, q -> q.copy(no = nextNo + i) }
            nextNo += questions.size
            if (questions.isEmpty()) return@forEachIndexed

            // 多学科交叉时给大题加科目前缀，例如「二、语文·看拼音写词语」
            // 跨科题型（自编题库 / 课标题库）不加：同一个大题里本身就是几科混着的，
            // 写「数学·知识点选择」反而会误导
            val crossSubject = spec.effectiveSubjects().size > 1
            val prefix = if (crossSubject && !generator.crossSubject) {
                GeneratorRegistry.subjectOf(type)?.let { "${it.label}·" } ?: ""
            } else ""

            sections.add(
                GeneratedSection(
                    index = sections.size + 1,
                    title = "${CN_NUM.getOrElse(sections.size) { (sections.size + 1).toString() }}、" +
                        "$prefix${type.label}（每题 2 分，共 ${questions.size * 2} 分）",
                    type = type,
                    questions = questions,
                    // 需要书写过程、或题干本身很长（连词成句 / 选词填空）的题型强制单栏，
                    // 否则 2 栏会把英文长句挤成好几行，印出来既难看又难写
                    columns = if (type.answerLines > 0 || type.columns <= 1) 1 else spec.columns,
                    answerLines = type.answerLines,
                )
            )
        }

        return GeneratedPaper(
            spec = spec,
            sections = sections,
            questions = sections.flatMap { it.questions },
            createdAt = System.currentTimeMillis(),
        )
    }

    /**
     * 默认规格：打开就能直接出一份。
     * 单科时只用**一个题型** —— 每个大题在排版上会另起一页，
     * 「口算 20 + 竖式 4」会白占两张纸；单题型卷配合自动补题能正好排满一页。
     * 勾了多学科时才给两个题型，保证各科都有题。
     */
    fun defaultSpec(grade: Int, subject: Subject): PaperSpec = defaultSpec(grade, listOf(subject))

    fun defaultSpec(grade: Int, subjects: List<Subject>): PaperSpec {
        val primary = subjects.firstOrNull() ?: Subject.MATH
        val types = GeneratorRegistry.availableFor(grade, subjects)
        val counts = LinkedHashMap<QuestionType, Int>()
        if (subjects.size > 1) {
            types.take(2).forEachIndexed { i, t ->
                counts[t] = if (t.answerLines > 0) 4 else if (i == 0) 20 else 10
            }
        } else {
            val first = types.firstOrNull()
            if (first != null) counts[first] = if (first.answerLines > 0) 8 else 30
        }
        return specWith(grade, primary, counts, subjects = subjects)
    }

    fun specWith(
        grade: Int,
        subject: Subject,
        counts: Map<QuestionType, Int>,
        seed: Long = System.currentTimeMillis(),
        title: String? = null,
        isMistakePaper: Boolean = false,
        subjects: List<Subject> = emptyList(),
    ): PaperSpec {
        val total = counts.values.sum()
        val autoTitle = if (subjects.size > 1) {
            "${GradeProfiles.stageLabel(grade)} · ${GradeProfiles.gradeLabel(grade)} · 综合练习"
        } else {
            "${GradeProfiles.stageLabel(grade)} · ${GradeProfiles.gradeLabel(grade)} · ${subject.label}练习"
        }
        return PaperSpec(
            grade = grade,
            subject = subject,
            title = title ?: autoTitle,
            subtitle = "共 $total 题　满分 ${total * 2} 分",
            counts = counts,
            difficulty = 2,
            columns = 2,
            answerMode = AnswerMode.LAST_PAGE,
            showNameRow = true,
            fontSize = 11f,
            seed = seed,
            isMistakePaper = isMistakePaper,
            subjects = subjects,
        )
    }
}

/** 首页快捷模板：每个模板只做题型组合，具体可用题型由年级科目自动裁剪。 */
object PaperTemplates {

    data class Preset(
        val key: String,
        val label: String,
        val desc: String,
        val wanted: Map<QuestionType, Int>,
    )

    /** v0.5.0 英语专项：单词拼装 + 连词成句 + 选词填空（句子长，均为单栏题型） */
    val englishFunCounts: Map<QuestionType, Int> = mapOf(
        QuestionType.WORD_SCRAMBLE to 10,
        QuestionType.SENTENCE_ORDER to 5,
        QuestionType.CLOZE to 6,
    )

    /** v0.5.0 主题识字专项：6 大生活场景，看拼音写词 + 找不同类 + 归类填空 */
    val themeLiteracyCounts: Map<QuestionType, Int> = mapOf(
        QuestionType.THEME_PINYIN to 12,
        QuestionType.THEME_SORT to 6,
        QuestionType.THEME_GROUP to 6,
    )

    /** v0.7.0 音标与语法专项：48 音素 + 43 道自编语法题 */
    val ipaGrammarCounts: Map<QuestionType, Int> = mapOf(
        QuestionType.PHONETIC_PICK to 8,
        QuestionType.PHONETIC_WRITE to 6,
        QuestionType.GRAMMAR to 10,
    )

    /** v0.7.0 课本同步专项：生字注音 + 单词默写，抽的是按年级分段的清单 */
    val textbookCounts: Map<QuestionType, Int> = mapOf(
        QuestionType.TEXTBOOK_HANZI to 14,
        QuestionType.TEXTBOOK_WORD to 14,
    )

    fun forSelection(grade: Int, subject: Subject): List<Preset> = forSelection(grade, listOf(subject))

    fun forSelection(grade: Int, subjects: List<Subject>): List<Preset> {
        val available = GeneratorRegistry.availableFor(grade, subjects).toSet()
        val raw = listOf(
            Preset("daily", "口算天天练", "一页 30 题，5 分钟", mapOf(QuestionType.ORAL to 30)),
            // v0.4.0：外部练习站的题型规则转成的自编计算题组合
            Preset("variety", "计算题大全", "填括号 + 凑十 + 连加连减 + 换算", mapOf(
                QuestionType.FILL_BLANK to 8, QuestionType.MAKE_TEN to 5,
                QuestionType.CHAIN_CALC to 6, QuestionType.TENS_CALC to 5,
                QuestionType.TWO_DIGIT to 5, QuestionType.UNIT_CONVERT to 5,
            )),
            Preset("thinking", "思维小练", "比大小 + 找规律 + 填括号", mapOf(
                QuestionType.COMPARE to 8, QuestionType.NUMBER_PATTERN to 6,
                QuestionType.FILL_BLANK to 6, QuestionType.COUNTING to 5,
            )),
            Preset("calc", "计算专项", "口算 + 竖式 + 脱式", mapOf(
                QuestionType.ORAL to 20, QuestionType.VERTICAL to 6, QuestionType.EXPRESSION to 6,
                QuestionType.RATIONAL to 20, QuestionType.ALGEBRA to 10,
            )),
            Preset("unit", "单元综合", "各题型混合，附答案页", buildMap {
                available.take(4).forEachIndexed { i, t ->
                    put(t, if (t.answerLines > 0) 3 else if (i == 0) 20 else 8)
                }
            }),
            // v0.5.0：英语 3 题型 / 主题识字 3 题型，词表句式全部自编
            Preset("english_fun", "英语趣味练", "单词拼装 + 连词成句 + 选词填空", englishFunCounts),
            Preset("theme_literacy", "主题识字卷", "超市 / 学校 / 交通…6 大生活场景", themeLiteracyCounts),
            // v0.7.0：内置音标 / 语法 / 课本同步清单
            Preset("ipa_grammar", "音标语法卷", "音标辨认 + 看音标写单词 + 语法选择", ipaGrammarCounts),
            Preset("textbook", "课本同步卷", "课本生字注音 + 课本单词默写", textbookCounts),
            Preset("words", "字词默写", "看拼音写词语 + 补全", mapOf(
                QuestionType.PINYIN to 16, QuestionType.DICTATION to 12, QuestionType.HANZI to 8,
                QuestionType.ENGLISH_WORD to 24,
            )),
            Preset("knowledge", "知识点题库", "内置自编选择 / 判断题，按所选科目抽题", mapOf(
                QuestionType.KNOWLEDGE to 20,
            )),
            // v0.9.0：课标题库（cn-math-taxonomy），五科知识点配套题，题库随上游持续变大
            Preset("curric", "知识点闯关", "课标知识点：填空 + 选择，按所选科目抽题", mapOf(
                QuestionType.CURRIC_BLANK to 10, QuestionType.CURRIC_CHOICE to 10,
            )),
        )
        return raw.mapNotNull { p ->
            val usable = p.wanted.filterKeys { it in available }
            if (usable.isEmpty()) null else p.copy(wanted = usable)
        }
    }
}
