package com.papermill.app.domain.generate

import com.papermill.app.domain.content.ExamBank
import com.papermill.app.domain.content.ExamQuestion
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

    /**
     * 一二年级科学：题干里的字大多超出识字量，印在大题标题后面请家长读题。
     *
     * 刻意压到 12 个字：它要跟标题挤在同一行，长句会把整行顶到第二行，
     * 反而比单独一条说明更占地方。
     */
    const val LOW_GRADE_SCIENCE_NOTE = "请家长读题，孩子口头作答"

    /**
     * 按来源轮转抽题：同一份卷在一批题里尽量只出现一次（v0.27.0）。
     *
     * 为什么不用 `pool.shuffled(rng).take(n)`：题库里 299 份卷的出题量极不均，
     * 最多的那份切出 35 道。纯随机抽 10 道，「一半来自同一张卷」是常态，
     * 排在卷面上就是两次相邻的大题里出现同一份期末卷的题 —— 家长一眼看得出来。
     *
     * 做法是分三轮：① 卷内各自打乱 ② 卷间再打乱（否则永远是那几份下载最早的卷优先）
     * ③ 每轮每份卷各出 1 道，出不满再走下一轮。想让某份卷多出也做不到，
     * 顺序全由 [rng] 定 —— 同 seed 仍可复现，点「换一套」照样换一批。
     */
    private fun pickSpread(pool: List<ExamQuestion>, n: Int, rng: Random): List<ExamQuestion> {
        if (n <= 0 || pool.isEmpty()) return emptyList()
        // 没有来源 id 的题（旧题库）各算一组，免得全被并成一组又变成纯随机
        val groups = pool.groupBy { it.source.ifEmpty { it.stem } }
        val queues = groups.values
            .map { g -> g.shuffled(rng).toMutableList() }
            .shuffled(rng)
        val out = ArrayList<ExamQuestion>(n)
        while (out.size < n) {
            var took = false
            for (q in queues) {
                if (q.isEmpty()) continue
                out.add(q.removeAt(0))
                took = true
                if (out.size == n) break
            }
            if (!took) break // 全抽干了，如实少给
        }
        return out
    }

    fun compose(spec: PaperSpec, historyHashes: Set<Long> = emptySet()): GeneratedPaper {
        val blocked = historyHashes.toMutableSet()
        val sections = ArrayList<GeneratedSection>()
        var nextNo = 1

        // 知识点专项：选了知识点就只出这些知识点，一个知识点一道大题。
        // 这时候 spec.counts 不参与 —— 专项卷的意义就是把「考什么」锁死，
        // 再掺进阅读、应用题就成了普通练习卷，用户会以为专项没生效。
        if (spec.points.isNotEmpty()) {
            spec.points.forEachIndexed { i, point ->
                val per = spec.pointCount.coerceIn(5, 50)
                // 每个知识点独立种子：改动其中一个的题量，不会把别的知识点洗牌
                val rng = Random(GeneratorRegistry.sectionSeed(spec.seed, 700 + i))
                val items = MathPointGenerators.generate(point, per, rng)
                    .mapIndexed { k, q -> q.copy(no = nextNo + k) }
                nextNo += items.size
                if (items.isNotEmpty()) {
                    sections.add(pointSectionOf(spec, point, items, sections.size + 1))
                }
            }
        }

        // 真题卷：从真题库抽题，一个大类一个大题。
        // 开了真题就只出真题 —— 跟知识点专项同一个道理：混着出，用户会以为开关没生效。
        //
        // v0.27.0 加了「真题库里真有这个年级的题」这一条：真题库只覆盖 1–6 年级，
        // 学前和初一一开真题就一道题都抽不到，最后渲染出一张 0 页 PDF，
        // PdfRenderer 报的却是 "file not in PDF format or corrupted" —— 用户根本猜不到
        // 是「这个年级没真题」。抽不到就回退到自编题，宁可出自己编的也不要出空卷。
        val examSubjects = spec.effectiveSubjects().map { it.code }.toSet()
        val useExam = spec.points.isEmpty() && spec.exam && ExamBank.items.isNotEmpty() &&
            spec.examFamilies.any { family ->
                ExamBank.items.any {
                    it.grade == spec.grade && it.subject in examSubjects && it.type in family.types
                }
            }
        if (useExam) {
            val per = spec.examPer.coerceIn(1, 30)
            spec.examFamilies.forEach { family ->
                val rng = Random(GeneratorRegistry.sectionSeed(spec.seed, 900 + family.ordinal))
                // 严格同年级。以前试过「不够就往邻年级借」，结果三年级卷里冒出
                // 「四年级同学去植树」—— 难度对不上年级，家长一眼就看出来。
                // 不够就如实少给。
                val pool = ExamBank.items.filter {
                    it.grade == spec.grade && it.subject in examSubjects && it.type in family.types
                }
                if (pool.isEmpty()) return@forEach

                val picked = pickSpread(pool, per, rng)
                val questions = picked.mapIndexed { k, q ->
                    GeneratedQuestion(
                        no = nextNo + k,
                        type = family.renderType,
                        stem = q.stem,
                        answer = q.answer,
                        topic = q.section,
                    )
                }
                nextNo += questions.size
                sections.add(
                    GeneratedSection(
                        index = sections.size + 1,
                        title = titleFor(family.label, POINTS_PER_QUESTION, questions.size, sections.size + 1),
                        name = family.label,
                        points = POINTS_PER_QUESTION,
                        type = family.renderType,
                        questions = questions,
                        columns = when {
                            family.renderType.columns <= 1 -> 1
                            family.renderType.answerLines > 0 -> family.renderType.columns
                            else -> maxOf(family.renderType.columns, spec.columns)
                        },
                        answerLines = family.renderType.answerLines,
                        instruction = "",
                    )
                )
            }
        }

        val ordered = spec.counts.entries
            .filter { it.value > 0 }
            .sortedBy { it.key.ordinal }

        (if (spec.points.isEmpty() && !useExam) ordered else emptyList()).forEachIndexed { index, entry ->
            val type = entry.key
            val count = entry.value.coerceIn(1, 200)
            val generator = GeneratorRegistry.of(type, spec.grade) ?: return@forEachIndexed

            val rng = Random(GeneratorRegistry.sectionSeed(spec.seed, index))
            val ctx = GenContext(
                grade = spec.grade,
                count = count,
                difficulty = kindDifficulty(spec),
                rng = rng,
                blocked = blocked,
                subjects = spec.effectiveSubjects().toSet(),
                topicIds = spec.topicIds,
                term = spec.term,
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
                sectionOf(
                    spec = spec,
                    type = type,
                    questions = trimmed,
                    ordinal = sections.size + 1,
                    instruction = listOfNotNull(
                        type.instruction.takeIf { it.isNotBlank() },
                        hoisted,
                    ).joinToString("　"),
                    prefix = prefix,
                    // 一二年级科学题干里的字超出识字量：明说一句请家长读题、口头作答。
                    // 挂在标题同一行，不再单占一行纸面
                    titleNote = LOW_GRADE_SCIENCE_NOTE.takeIf {
                        spec.grade <= 2 && type.name.startsWith("SCIENCE")
                    } ?: "",
                )
            )
        }

        // v0.19.0 自编题：用户自己写的内容，原样接在常规大题后面。
        // 它不走 GeneratorRegistry（那套是「随机造题」），所以单独拼；
        // 但标题格式 / 分值 / 栏数 / 答题线仍走同一套规则，纸上看起来没有区别。
        spec.customSheets.forEach { sheet ->
            if (sheet.raw.isBlank()) return@forEach
            val rng = Random(GeneratorRegistry.sectionSeed(spec.seed, 900 + sections.size))
            val built = CustomSheet.build(sheet, rng)
            if (built.questions.isEmpty()) return@forEach
            val qs = built.questions.mapIndexed { i, q -> q.copy(no = nextNo + i) }
            nextNo += qs.size
            val ordinal = sections.size + 1
            val sheetName = sheet.title.ifBlank { sheet.mode.label }
            sections.add(
                GeneratedSection(
                    index = ordinal,
                    title = titleFor(sheetName, sheet.points, qs.size, ordinal),
                    name = sheetName,
                    points = sheet.points,
                    type = QuestionType.CUSTOM,
                    questions = qs,
                    columns = sheet.columns.coerceIn(1, 4),
                    answerLines = sheet.answerLines.coerceIn(0, 6),
                )
            )
        }

        // 一道题都没出：不要再往下走。渲染层拿到空 sections 会产出一个 0 页 PDF，
        // Android 的 PdfRenderer 打不开，用户看到的是「file not in PDF format or corrupted」——
        // 一句完全对不上病因的报错。在源头说人话。
        if (sections.isEmpty()) {
            throw IllegalStateException(
                "这个年级/科目出不了题（真题库里没有，自编题也没抽到）—— 换个年级或科目试试"
            )
        }

        return GeneratedPaper(
            spec = spec,
            sections = sections,
            questions = sections.flatMap { it.questions },
            createdAt = System.currentTimeMillis(),
        )
    }

    /** 每题的固定分值。标题里印的「每题 N 分，共 M 分」就是它算出来的 */
    private const val POINTS_PER_QUESTION = 2

    /**
     * 用一组现成题目拼出一道大题：序号、标题、栏数、答题线、说明全按题型规则来。
     *
     * 为什么抽出来：预览页「加一题」时，如果加的题型这张卷里还没有，得现场
     * 造一道大题 —— 标题格式、分值算法、栏数规则必须跟出卷时**一模一样**。
     * 两处各写一遍，改一处忘另一处，纸上就会出现「应用题（每题 2 分，共 6 分）」
     * 底下印着 4 道题这种对不上账的东西。
     *
     * [prefix] 由调用方给：出卷时它是「数学·」这种科目前缀（多学科交叉时才加），
     * 加题时按同一规则现算。
     */
    /**
     * 大题标题：「一、口算（每题 2 分，共 60 分）」。
     *
     * 抽出来的原因（v0.18.3）：预览页删题后标题里的「共 N 分」和中文序号都要重印，
     * 否则纸上会出现「共 60 分」底下排着 27 道题、序号从「一」跳到「三」。
     * 加题 / 删题 / 出卷三处必须共用同一份格式。
     */
    fun sectionTitle(type: QuestionType, count: Int, ordinal: Int, prefix: String): String =
        titleFor("$prefix${type.label}", POINTS_PER_QUESTION, count, ordinal)

    /**
     * 大题标题的统一格式。
     *
     * 自编题的大题名是用户起的、分值也由用户定，所以标题不能只认 [QuestionType] ——
     * 删题重排时要按「原来那个名字 + 原来的每题分」重印，否则纸上会变成
     * 「五、自编题（每题 2 分，共 4 分）」，用户起的名字和 5 分都消失了。
     */
    fun titleFor(name: String, points: Int, count: Int, ordinal: Int): String =
        "${CN_NUM.getOrElse(ordinal - 1) { ordinal.toString() }}、" +
            "$name（每题 $points 分，共 ${count * points} 分）"

    fun sectionOf(
        spec: PaperSpec,
        type: QuestionType,
        questions: List<GeneratedQuestion>,
        ordinal: Int,
        instruction: String = type.instruction,
        prefix: String = "",
        titleNote: String = "",
    ): GeneratedSection = GeneratedSection(
        index = ordinal,
        title = sectionTitle(type, questions.size, ordinal, prefix),
        name = "$prefix${type.label}",
        points = POINTS_PER_QUESTION,
        type = type,
        questions = questions,
        // 栏数：题型推荐的栏数与用户选的全局栏数取较大者。
        // v0.11.2 之前只判断 `type.columns <= 1`，等于题型自己的栏数从来没生效过 ——
        // 竖式计算声明了 1 栏，就永远一栏一题、白占半张纸。
        // v0.18.2：带答题线的题型**不跟全局栏数走**。用户选 4 栏时应用题跟着变 4 栏，
        // 就成了一行半句话、答题线短得写不下算式 —— 线是给孩子手写的，
        // 栏越窄越没法用，这跟口算求「塞得下就行」不是一回事。
        columns = when {
            type.columns <= 1 -> 1
            type.answerLines > 0 -> type.columns
            else -> maxOf(type.columns, spec.columns)
        },
        answerLines = type.answerLines,
        instruction = instruction,
        titleNote = titleNote,
    )

    /**
     * 知识点专项的一个大题。
     *
     * 跟常规大题的唯一区别是大题名字用**知识点名**（「一、20以内退位减法」），
     * 而不是题型名（「一、口算」）—— 专项卷要让人一眼看出这张纸在练哪一课。
     * 栏数、答题线、竖式说明全部沿用该题型的规则，所以纸面风格和常规卷一致。
     */
    private fun pointSectionOf(
        spec: PaperSpec,
        point: MathPoint,
        questions: List<GeneratedQuestion>,
        ordinal: Int,
    ): GeneratedSection {
        val type = point.render
        return GeneratedSection(
            index = ordinal,
            title = titleFor(point.label, POINTS_PER_QUESTION, questions.size, ordinal),
            name = point.label,
            points = POINTS_PER_QUESTION,
            type = type,
            questions = questions,
            columns = when {
                type.columns <= 1 -> 1
                type.answerLines > 0 -> type.columns
                else -> maxOf(type.columns, spec.columns)
            },
            answerLines = type.answerLines,
            // 竖式那三个知识点需要「用竖式计算」这句说明；口算、比大小本来就没有说明
            instruction = type.instruction,
            // 「π 取 3.14」这类常量说明提到标题行，题干就不用每题重复一遍
            titleNote = point.note,
        )
    }

    /** 多学科交叉时大题标题的科目前缀；跨科题型（题库混装）不加，见 [compose] */
    fun subjectPrefixFor(spec: PaperSpec, type: QuestionType): String {        if (spec.effectiveSubjects().size <= 1) return ""
        val generator = GeneratorRegistry.of(type, spec.grade)
        if (generator?.crossSubject == true) return ""
        return GeneratorRegistry.subjectOf(type)?.let { "${it.label}·" } ?: ""
    }

    /**
     * 默认规格：打开就能直接出一份。
     *
     * v0.21.0 起由「卷型」决定铺多宽、题量多大、优先挑哪类题（见 [PaperKind]）：
     *  - 全部：四类题型各来一点，按年级常规配比
     *  - 单元 / 期中 / 期末：3 → 4 → 5 种题型，题量依次加大
     *  - 练习题：一两种题型大量练，一页盯住一个知识点（原来的 DAILY，单题型卷的道理没变：
     *    每个大题另起一页，「口算 20 + 竖式 4」会白占两张纸）
     *  - 拼音 / 奥数：语文、数学的专项卷，卷型自带科目
     *  - 竞赛 / 小升初：题量少、思维题多、题库难度上调一档
     *
     * 「题型倾向」仍然管以哪类题为主，两者叠加排序（卷型偏好在前，倾向在后）。
     */
    fun defaultSpec(
        grade: Int,
        subject: Subject,
        tilt: Tilt = Tilt.BALANCED,
        kind: PaperKind = PaperKind.ALL,
    ): PaperSpec = defaultSpec(grade, listOf(subject), tilt, kind)

    fun defaultSpec(
        grade: Int,
        subjects: List<Subject>,
        tilt: Tilt = Tilt.BALANCED,
        kind: PaperKind = PaperKind.ALL,
    ): PaperSpec {
        val primary = subjects.firstOrNull() ?: Subject.MATH
        val types = GeneratorRegistry.availableFor(grade, subjects)
        val counts = LinkedHashMap<QuestionType, Int>()
        if (subjects.size == 1 && primary == Subject.SCIENCE) {
            // 科学是知识型科目，一份卷子本来就是「选择 + 判断 + 填空」三块，
            // 不像数学那样一种题型刷 30 道。而且题库每个年级每型只有 12 道，
            // 默认开 30 题会印出一张只有 12 题的卷子，标题和实际对不上。
            scienceCounts(tilt, kind).forEach { (t, n) -> counts[t] = n }
        } else if (subjects.size > 1) {
            // 多学科：每个科目至少要有一个题型，否则勾了英语却一道英语题都没有
            pickForSubjects(types, subjects, tilt, kind).forEachIndexed { i, t ->
                counts[t] = countFor(t, i, kind)
            }
        } else {
            val picked = orderByTilt(types, tilt, kind).take(kind.kinds)
            picked.forEachIndexed { i, t -> counts[t] = countFor(t, i, kind) }
        }
        // 幼小衔接（v0.27.0）：常规卷是 40 题上下，学前孩子的有效专注时间撑不到一半 ——
        // 印 40 道，家长做 15 道就停了，剩下的纸白扔。整卷折半，每型至少留 4 道
        // （少于 4 道就不像一个大题了）。想更密可以从卷面设置里手动加题。
        if (grade == 0) counts.replaceAll { _, n -> (n / 2).coerceAtLeast(4) }
        return specWith(grade, primary, counts, subjects = subjects, tilt = tilt, kind = kind)
    }

    /**
     * 科学卷的三块配比。
     *
     * 科学只有「选择 / 判断 / 填空」三种题型，题库每级每型 12 道 —— 所以**上限就是 12**，
     * 写成 30 会印出一张只有 12 题的卷子，卷头写着 30 题。
     *
     * v0.12.0 之前这里写死 8/10/8，把倾向和用途整个吞了：用户在科学卷上连点七种倾向，
     * 配比一个数字都不动。现在让倾向决定「主型」，卷型决定「铺多宽」。
     */
    private fun scienceCounts(tilt: Tilt, kind: PaperKind): Map<QuestionType, Int> {
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
        when {
            // 练习题 / 只铺一两种：主型刷满 + 一个陪衬，一页刚好
            kind.kinds <= 2 -> {
                out[main] = 12
                out[others.first()] = 8
            }
            // 单元测试：三块都有，主型略多
            kind.kinds == 3 -> {
                out[main] = 12
                others.forEach { out[it] = 8 }
            }
            // 期中 / 期末 / 综合：三块都按题库上限压满
            else -> {
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
     * 按「卷型 + 倾向」排序：卷型自带的偏好题型排最前（拼音卷的看拼音写词语、
     * 竞赛卷的找规律…），其次才看题型倾向 —— 名字更贴切的优先，再其次看大类。
     *
     * 只按大类排会挑出「自编题库」这种混装题当选择题代表；
     * 名字里直接带「选择 / 填空 / 判断 / 应用」的题型才是这个倾向的正解。
     * 只排序不过滤 —— 这一年级压根没有该类题时，卷子总得能出，退化成原顺序即可。
     */
    private fun orderByTilt(types: List<QuestionType>, tilt: Tilt, kind: PaperKind): List<QuestionType> {
        val prefer = tilt.prefer
        return types.sortedByDescending { t ->
            var score = kind.preferTypes.indexOf(t).let { i -> if (i >= 0) 100 - i else 0 }
            if (prefer != null) {
                score += when {
                    t.label.contains(prefer.label) -> 2
                    t.family == prefer -> 1
                    else -> 0
                }
            }
            score
        }
    }

    /** 多学科时，每个科目按「卷型 + 倾向」挑一个代表题型，凑够 [PaperKind.kinds] 种 */
    private fun pickForSubjects(
        types: List<QuestionType>,
        subjects: List<Subject>,
        tilt: Tilt,
        kind: PaperKind,
    ): List<QuestionType> {
        val out = LinkedHashMap<QuestionType, Unit>()
        val perSubject = LinkedHashMap<Subject, Int>()
        // 先保证每科一个（挑该科里最贴合倾向的那个）
        subjects.forEach { s ->
            val mine = orderByTilt(types.filter { GeneratorRegistry.subjectOf(it) == s }, tilt, kind)
            mine.firstOrNull()?.let { out[it] = Unit; perSubject[s] = 1 }
        }
        // 再按倾向补足到目标数量，轮流照顾每个科目，避免全给第一科
        var round = 0
        while (out.size < kind.kinds + (subjects.size - 1).coerceAtLeast(0) && round < 8) {
            var added = false
            subjects.forEach { s ->
                if (out.size >= kind.kinds + (subjects.size - 1).coerceAtLeast(0)) return@forEach
                val mine = orderByTilt(types.filter { GeneratorRegistry.subjectOf(it) == s }, tilt, kind)
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
     * 第 [index] 个题型的默认题量。卷型表里给了每个位置的题量（[PaperKind.sizes]），
     * 这里再按答题线兜一道上限。
     *
     * 两重约束：
     *  - 靠前的大题题量更大，靠后的递减，否则「两大题各 30 道」直接把卷子拉成三页；
     *  - 带答题线的题一道占好几行，按行数封顶 —— 应用题给 16 道，一页根本装不下。
     */
    private fun countFor(t: QuestionType, index: Int, kind: PaperKind): Int {
        val sizes = kind.sizes
        val want = sizes[index.coerceIn(0, sizes.lastIndex)]
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
        kind: PaperKind = PaperKind.ALL,
        points: List<MathPoint> = emptyList(),
        pointCount: Int = 20,
    ): PaperSpec {
        val total = if (points.isEmpty()) counts.values.sum() else points.size * pointCount.coerceIn(5, 50)
        return PaperSpec(
            grade = grade,
            subject = subject,
            title = title ?: autoTitle(grade, subjects.ifEmpty { listOf(subject) }, kind, points),
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
            kind = kind,
            points = points,
            pointCount = pointCount,
        )
    }

    /**
     * 自动标题：「小学 · 三年级 · 数学期末测试」。
     *
     * 点一下卷型，卷头那几个字立刻跟着变 —— 这是最快的反馈：不用回头看配比，
     * 也知道这一次点上了（只有「配比真的变了」还不够，用户得看得见）。
     */
    fun autoTitle(
        grade: Int,
        subjects: List<Subject>,
        kind: PaperKind,
        points: List<MathPoint> = emptyList(),
    ): String {
        // 学前（g=0）的阶段名和年级名撞车（都是「学前」），拼出来是「学前 · 学前」——
        // 印在卷头上就是一句废话。撞车时只留一个。
        val stage = GradeProfiles.stageLabel(grade)
        val gl = GradeProfiles.gradeLabel(grade)
        val head = if (stage == gl) stage else "$stage · $gl"
        // 专项卷的标题直接写知识点：家长拿到一张纸，第一眼看的就是「练的哪一课」
        if (points.size == 1) return "$head · ${points[0].label}专项"
        if (points.size > 1) return "$head · 数学专项（${points.size} 个知识点）"
        return if (subjects.size > 1) {
            "$head · ${kindWord(kind)}"
        } else {
            "$head · ${subjects.firstOrNull()?.label ?: ""}${kindWord(kind)}"
        }
    }

    /** 卷头上印的卷型名：跟网页端 KIND_WORD 一字不差 */
    fun kindWord(kind: PaperKind): String = when (kind) {
        PaperKind.ALL -> "综合练习"
        PaperKind.UNIT -> "单元测试"
        PaperKind.MIDTERM -> "期中测试"
        PaperKind.FINAL -> "期末测试"
        PaperKind.PRACTICE -> "练习"
        PaperKind.PINYIN -> "拼音专项"
        PaperKind.REVIEW -> "复习卷"
        PaperKind.COMPREHENSIVE -> "综合测试"
        PaperKind.CONTEST -> "竞赛卷"
        PaperKind.OLYMPIAD -> "奥数卷"
        PaperKind.GRADUATION -> "小升初冲刺"
    }

    /** 卷型自带的难度加成（竞赛 / 奥数 / 小升初 +1）：与用户选的难度相加，1~3 封顶 */
    fun kindDifficulty(spec: PaperSpec): Int = (spec.difficulty + spec.kind.boost).coerceIn(1, 3)

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
        }.map { p ->
            // 学前（g=0）的预设卡也折半：25 道比大小 + 找规律对 5 岁孩子是一小时的量，
            // 跟 defaultSpec 那边同一个道理。每型至少留 3 道，少了不成一个大题。
            if (grade != 0) p
            else p.copy(wanted = p.wanted.mapValues { (_, n) -> (n / 2).coerceAtLeast(3) })
        }
    }

}
