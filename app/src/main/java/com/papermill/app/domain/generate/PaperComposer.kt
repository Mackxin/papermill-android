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
                topicIds = spec.topicIds,
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

            // 每道题都带同一句「用竖式计算：」「重排字母组成单词：」时，
            // 把它提到大题下面印一次，题干只留真正变化的部分
            val (hoisted, trimmed) = hoistInstruction(questions)

            sections.add(
                GeneratedSection(
                    index = sections.size + 1,
                    title = "${CN_NUM.getOrElse(sections.size) { (sections.size + 1).toString() }}、" +
                        "$prefix${type.label}（每题 2 分，共 ${questions.size * 2} 分）",
                    type = type,
                    questions = trimmed,
                    // 栏数：题型推荐的栏数与用户选的全局栏数取较大者。
                    // v0.11.2 之前这里只判断 `type.columns <= 1`，等于题型自己的栏数
                    // 从来没生效过 —— 竖式计算声明了 1 栏，就永远一栏一题、白占半张纸。
                    columns = if (type.columns <= 1) 1 else maxOf(type.columns, spec.columns),
                    answerLines = type.answerLines,
                    instruction = listOfNotNull(
                        type.instruction.takeIf { it.isNotBlank() },
                        hoisted,
                    ).joinToString("　"),
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
     *
     * v0.12.0 起由「卷面用途」决定铺多宽、「题型倾向」决定以哪类题为主：
     *  - 日常巩固：一两种题型大量练，一页盯住一个知识点（原来就是这个行为）
     *  - 单元检测：三四种题型均衡
     *  - 综合复习：题型铺满、题量更大
     *
     * 单题型卷仍然有它的道理：每个大题另起一页，「口算 20 + 竖式 4」会白占两张纸。
     * 所以 DAILY 只给一种题型，靠自动补题把整页填满。
     */
    fun defaultSpec(
        grade: Int,
        subject: Subject,
        tilt: Tilt = Tilt.BALANCED,
        purpose: PaperPurpose = PaperPurpose.DAILY,
    ): PaperSpec = defaultSpec(grade, listOf(subject), tilt, purpose)

    fun defaultSpec(
        grade: Int,
        subjects: List<Subject>,
        tilt: Tilt = Tilt.BALANCED,
        purpose: PaperPurpose = PaperPurpose.DAILY,
    ): PaperSpec {
        val primary = subjects.firstOrNull() ?: Subject.MATH
        val types = GeneratorRegistry.availableFor(grade, subjects)
        val counts = LinkedHashMap<QuestionType, Int>()
        if (subjects.size == 1 && primary == Subject.SCIENCE) {
            // 科学是知识型科目，一份卷子本来就是「选择 + 判断 + 填空」三块，
            // 不像数学那样一种题型刷 30 道。而且题库每个年级每型只有 12 道，
            // 默认开 30 题会印出一张只有 12 题的卷子，标题和实际对不上。
            scienceCounts(tilt, purpose).forEach { (t, n) -> counts[t] = n }
        } else if (subjects.size > 1) {
            // 多学科：每个科目至少要有一个题型，否则勾了英语却一道英语题都没有
            pickForSubjects(types, subjects, tilt, purpose).forEachIndexed { i, t ->
                counts[t] = countFor(t, i, purpose)
            }
        } else {
            val picked = orderByTilt(types, tilt).take(purpose.kinds)
            picked.forEachIndexed { i, t -> counts[t] = countFor(t, i, purpose) }
        }
        return specWith(grade, primary, counts, subjects = subjects, tilt = tilt, purpose = purpose)
    }

    /**
     * 科学卷的三块配比。
     *
     * 科学只有「选择 / 判断 / 填空」三种题型，题库每级每型 12 道 —— 所以**上限就是 12**，
     * 写成 30 会印出一张只有 12 题的卷子，卷头写着 30 题。
     *
     * v0.12.0 之前这里写死 8/10/8，把倾向和用途整个吞了：用户在科学卷上连点七种倾向，
     * 配比一个数字都不动。现在让倾向决定「主型」，用途决定「铺多宽」。
     */
    private fun scienceCounts(tilt: Tilt, purpose: PaperPurpose): Map<QuestionType, Int> {
        // 主型：倾向指哪儿打哪儿；均衡搭配 / 该倾向科学上不适用时，默认判断题（科学最常用的辨析形式）
        val main = when (tilt.prefer) {
            QuestionFamily.CHOICE -> QuestionType.SCIENCE_CHOICE
            QuestionFamily.BLANK -> QuestionType.SCIENCE_BLANK
            QuestionFamily.JUDGE -> QuestionType.SCIENCE_JUDGE
            else -> QuestionType.SCIENCE_JUDGE
        }
        val others = listOf(
            QuestionType.SCIENCE_CHOICE,
            QuestionType.SCIENCE_JUDGE,
            QuestionType.SCIENCE_BLANK,
        ).filter { it != main }
        val out = LinkedHashMap<QuestionType, Int>()
        when (purpose) {
            // 日常巩固：主型刷满 + 一个陪衬，一页刚好
            PaperPurpose.DAILY -> {
                out[main] = 12
                out[others.first()] = 8
            }
            // 单元检测：三块都有，主型略多
            PaperPurpose.UNIT -> {
                out[main] = 12
                others.forEach { out[it] = 8 }
            }
            // 综合复习：三块都按题库上限压满
            PaperPurpose.REVIEW -> {
                out[main] = 12
                others.forEach { out[it] = 10 }
            }
        }
        return out
    }

    /**
     * 这个「年级 + 科目」能不能真做出该倾向的题。
     *
     * 没有的话 UI 必须把倾向置灰。v0.12.0 初版是**静默退化**：数学卷上点「判断题偏多」，
     * 配比一个字都不变，用户以为设上了、其实什么也没发生 —— 这跟 v0.11.2 那个
     * 「columns 声明了从没被读」是同一类病：**控件点了有反应，但反应是假的**。
     */
    fun fits(grade: Int, subjects: List<Subject>, tilt: Tilt): Boolean {
        val prefer = tilt.prefer ?: return true
        return GeneratorRegistry.availableFor(grade, subjects).any {
            it.family == prefer || it.label.contains(prefer.label)
        }
    }

    /**
     * 按倾向排序：**题型名字更贴切的优先**，其次才看大类。
     *
     * 只按大类排会挑出「自编题库」这种混装题当选择题代表；
     * 名字里直接带「选择 / 填空 / 判断 / 应用」的题型才是这个倾向的正解。
     * 只排序不过滤 —— 这一年级压根没有该类题时，卷子总得能出，退化成原顺序即可。
     */
    private fun orderByTilt(types: List<QuestionType>, tilt: Tilt): List<QuestionType> {
        val prefer = tilt.prefer ?: return types
        return types.sortedByDescending { t ->
            when {
                t.label.contains(prefer.label) -> 2
                t.family == prefer -> 1
                else -> 0
            }
        }
    }

    /** 多学科时，每个科目按倾向挑一个代表题型，凑够 [PaperPurpose.kinds] 种 */
    private fun pickForSubjects(
        types: List<QuestionType>,
        subjects: List<Subject>,
        tilt: Tilt,
        purpose: PaperPurpose,
    ): List<QuestionType> {
        val out = LinkedHashMap<QuestionType, Unit>()
        val perSubject = LinkedHashMap<Subject, Int>()
        // 先保证每科一个（挑该科里最贴合倾向的那个）
        subjects.forEach { s ->
            val mine = orderByTilt(types.filter { GeneratorRegistry.subjectOf(it) == s }, tilt)
            mine.firstOrNull()?.let { out[it] = Unit; perSubject[s] = 1 }
        }
        // 再按倾向补足到目标数量，轮流照顾每个科目，避免全给第一科
        var round = 0
        while (out.size < purpose.kinds + (subjects.size - 1).coerceAtLeast(0) && round < 8) {
            var added = false
            subjects.forEach { s ->
                if (out.size >= purpose.kinds + (subjects.size - 1).coerceAtLeast(0)) return@forEach
                val mine = orderByTilt(types.filter { GeneratorRegistry.subjectOf(it) == s }, tilt)
                val next = mine.firstOrNull { it !in out }
                if (next != null) {
                    out[next] = Unit
                    perSubject[s] = (perSubject[s] ?: 0) + 1
                    added = true
                }
            }
            if (!added) break
            round++
        }
        return out.keys.toList()
    }

    /**
     * 第 [index] 个题型的默认题量。
     *
     * 两重约束：
     *  - 靠前的大题题量更大，靠后的递减，否则「两大题各 30 道」直接把卷子拉成三页；
     *  - 带答题线的题一道占好几行，按行数封顶 —— 应用题给 16 道，一页根本装不下。
     */
    private fun countFor(t: QuestionType, index: Int, purpose: PaperPurpose): Int {
        val table = when (purpose) {
            PaperPurpose.DAILY -> intArrayOf(30, 10, 8, 8)
            PaperPurpose.UNIT -> intArrayOf(18, 10, 8, 6)
            PaperPurpose.REVIEW -> intArrayOf(16, 10, 8, 6)
        }
        val want = table[index.coerceIn(0, table.lastIndex)]
        val cap = when {
            t.answerLines >= 3 -> 8
            t.answerLines == 2 -> 10
            t.answerLines == 1 -> 16
            else -> Int.MAX_VALUE
        }
        return minOf(want, cap)
    }

    fun specWith(
        grade: Int,
        subject: Subject,
        counts: Map<QuestionType, Int>,
        seed: Long = System.currentTimeMillis(),
        title: String? = null,
        isMistakePaper: Boolean = false,
        subjects: List<Subject> = emptyList(),
        tilt: Tilt = Tilt.BALANCED,
        purpose: PaperPurpose = PaperPurpose.DAILY,
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
            tilt = tilt,
            purpose = purpose,
        )
    }

    /**
     * 把「每道题都重复一遍的做题指令」抽出来，作为大题说明只印一次。
     *
     * 判据是**最长公共前缀里最后一个冒号**：竖式计算每道题都以「用竖式计算：」开头、
     * 单词拼装以「重排字母组成单词：」开头，这种前缀一提就干净。
     *
     * 三条保守限制，避免把正常题干砍坏：
     *  - 冒号位置不能太靠前（< 4）—— 「A.」「12、」这类编号不是指令；
     *  - 前缀里至少 3 个字母 / 汉字 —— 「（　）」之类的符号串不算；
     *  - 抽完不能有题目变成空壳 —— 说明这个前缀其实是题目的全部内容。
     *
     * @return 抽出的说明（无可抽时为 null）与新题干列表
     */
    private fun hoistInstruction(
        questions: List<GeneratedQuestion>,
    ): Pair<String?, List<GeneratedQuestion>> {
        if (questions.size < 3) return null to questions
        var prefix = questions.first().stem
        questions.forEach { q ->
            prefix = commonPrefix(prefix, q.stem)
        }
        val colon = prefix.indexOfLast { it == '：' || it == ':' }
        if (colon < 3) return null to questions
        val head = prefix.substring(0, colon + 1)
        if (head.count { it.isLetter() } < 3) return null to questions
        val rest = questions.map { it.stem.substring(head.length).trim() }
        if (rest.any { it.isEmpty() }) return null to questions
        return head to questions.mapIndexed { i, q -> q.copy(stem = rest[i]) }
    }

    private fun commonPrefix(a: String, b: String): String {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return a.substring(0, i)
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
