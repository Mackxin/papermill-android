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
            val crossSubject = spec.effectiveSubjects().size > 1
            val prefix = if (crossSubject && type != QuestionType.KNOWLEDGE) {
                GeneratorRegistry.subjectOf(type)?.let { "${it.label}·" } ?: ""
            } else ""

            sections.add(
                GeneratedSection(
                    index = sections.size + 1,
                    title = "${CN_NUM.getOrElse(sections.size) { (sections.size + 1).toString() }}、" +
                        "$prefix${type.label}（每题 2 分，共 ${questions.size * 2} 分）",
                    type = type,
                    questions = questions,
                    // 需要书写过程的题强制单栏，否则横线会被挤到很窄
                    columns = if (type.answerLines > 0) 1 else spec.columns,
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

    /** 默认规格：按年级科目自动挑两个最合适的题型，打开就能直接出一份 */
    fun defaultSpec(grade: Int, subject: Subject): PaperSpec = defaultSpec(grade, listOf(subject))

    fun defaultSpec(grade: Int, subjects: List<Subject>): PaperSpec {
        val primary = subjects.firstOrNull() ?: Subject.MATH
        val types = GeneratorRegistry.availableFor(grade, subjects)
        val counts = LinkedHashMap<QuestionType, Int>()
        types.take(2).forEachIndexed { i, t ->
            counts[t] = if (t.answerLines > 0) 4 else if (i == 0) 20 else 10
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

    fun forSelection(grade: Int, subject: Subject): List<Preset> = forSelection(grade, listOf(subject))

    fun forSelection(grade: Int, subjects: List<Subject>): List<Preset> {
        val available = GeneratorRegistry.availableFor(grade, subjects).toSet()
        val raw = listOf(
            Preset("daily", "口算天天练", "一页 30 题，5 分钟", mapOf(QuestionType.ORAL to 30)),
            Preset("calc", "计算专项", "口算 + 竖式 + 脱式", mapOf(
                QuestionType.ORAL to 20, QuestionType.VERTICAL to 6, QuestionType.EXPRESSION to 6,
                QuestionType.RATIONAL to 20, QuestionType.ALGEBRA to 10,
            )),
            Preset("unit", "单元综合", "各题型混合，附答案页", buildMap {
                available.take(4).forEachIndexed { i, t ->
                    put(t, if (t.answerLines > 0) 3 else if (i == 0) 20 else 8)
                }
            }),
            Preset("words", "字词默写", "看拼音写词语 + 补全", mapOf(
                QuestionType.PINYIN to 16, QuestionType.DICTATION to 12, QuestionType.HANZI to 8,
                QuestionType.ENGLISH_WORD to 24,
            )),
            Preset("knowledge", "知识点题库", "内置自编选择 / 判断题，按所选科目抽题", mapOf(
                QuestionType.KNOWLEDGE to 20,
            )),
        )
        return raw.mapNotNull { p ->
            val usable = p.wanted.filterKeys { it in available }
            if (usable.isEmpty()) null else p.copy(wanted = usable)
        }
    }
}
