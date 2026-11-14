package com.papermill.app.domain.content

import android.content.Context
import com.papermill.app.domain.generate.Subject
import org.json.JSONArray
import org.json.JSONObject

/**
 * 课标题库（知识点 + 题目）加载器。
 *
 * ============================================================
 * 数据从哪来 / 许可
 * ============================================================
 * 来自开源项目 [cn-math-taxonomy](https://github.com/xiaoyang111222/cn-math-taxonomy)，
 * 许可 **CC BY-NC 4.0（署名 — 非商业性使用）**。
 *  - 署名与改动说明见 `assets/licenses/CN_MATH_TAXONOMY_LICENSE.txt` 与 App「关于」页；
 *  - **一旦本 App 商业化（付费、广告、随硬件销售），必须删掉 assets/taxonomy 并下线相关题型**。
 *
 * ============================================================
 * 为什么拆成五个文件、还配一份 index
 * ============================================================
 * 原始数据 14MB，全塞进 APK 且每次启动都解析并不现实。这里：
 *  - 导入脚本按科目裁成五个文件（合计约 2.9MB），**用哪个科目才读哪个文件**；
 *  - 另有一份几百字节的 `index.json` 只放数量统计，
 *    「内置内容」页显示真实题量时不需要碰那 2.9MB。
 *
 * 解析结果按科目缓存：一科 2500 道题解析一次约 100~200ms，
 * 出一次卷只解析一次，不要每题都读盘。
 */
data class CurricQuestion(
    val id: String,
    val topicId: String,
    val grade: Int,
    /** 这道题属于哪一科：选择题要按科目分组出大题，答案页也要标出科目 */
    val subject: Subject,
    /** true = 选择题（answer 是 A/B/C/D），false = 填空或简答（answer 是一段文字） */
    val choice: Boolean,
    val prompt: String,
    /** 原选项：key to 文本；填空题为空 */
    val options: List<Pair<String, String>>,
    val answer: String,
    val explain: String,
    /** 1 基础 / 2 标准 / 3 提高 */
    val difficulty: Int,
)

/** 一个知识点：名称、所属章节、掌握标准、一道诊断题 */
data class CurricTopic(
    val id: String,
    val name: String,
    val grade: Int,
    val semester: String,
    val chapter: String,
    val desc: String,
    val criteria: List<String>,
    val diagnosticQuestion: String,
    val diagnosticAnswer: String,
)

/**
 * 教材单元（= 数据里 chapter 这一栏聚合出来的）。
 *
 * 上游每个知识点都带 `c`（章节）字段，原文长这样：
 *  - 小学：「一年级上册 第1单元 5以内数的认识和加、减法」
 *  - 初中：「七上 第二章 有理数的运算 2.2.2」（物理还有「八上 第一章 机械运动 1.1」）
 * 所以单元目录**不用另抄一份**，直接按 chapter 聚合就行 —— 好处是题目、知识点、
 * 单元三者永远对得上：改了题库，单元里立刻跟着变。
 */
data class CurricUnit(
    /** chapter 清洗后的单元名，如「第1单元 5以内数的认识和加、减法」 */
    val name: String,
    val subject: Subject,
    val grade: Int,
    /** "上" / "下" */
    val semester: String,
    val topics: List<CurricTopic>,
) {
    val topicIds: List<String> get() = topics.map { it.id }
}

object CurriculumBank {

    private var appContext: Context? = null

    /** 各科目题量统计（来自 index.json） */
    private var stats: JSONObject? = null

    private val cache = HashMap<String, SubjectData>()

    class SubjectData(
        val topics: List<CurricTopic>,
        val questions: List<CurricQuestion>,
    ) {
        val blankCount: Int get() = questions.count { !it.choice }
        val choiceCount: Int get() = questions.count { it.choice }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        stats = runCatching {
            JSONObject(readAsset("taxonomy/index.json"))
        }.getOrNull()
    }

    private fun readAsset(path: String): String {
        val ctx = appContext ?: error("CurriculumBank 尚未初始化")
        return ctx.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun keyOf(subject: Subject): String = subject.code.lowercase()

    @Synchronized
    fun data(subject: Subject): SubjectData {
        val key = keyOf(subject)
        cache[key]?.let { return it }
        val parsed = runCatching {
            val root = JSONObject(readAsset("taxonomy/$key.json"))
            SubjectData(
                topics = parseTopics(root.optJSONArray("topics")),
                questions = parseQuestions(root.optJSONArray("questions"), subject),
            )
        }.getOrDefault(SubjectData(emptyList(), emptyList()))
        cache[key] = parsed
        return parsed
    }

    private fun parseTopics(arr: JSONArray?): List<CurricTopic> {
        if (arr == null) return emptyList()
        val out = ArrayList<CurricTopic>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val cr = o.optJSONArray("cr")
            val criteria = ArrayList<String>()
            if (cr != null) for (j in 0 until cr.length()) criteria.add(cr.optString(j))
            out.add(
                CurricTopic(
                    id = o.optString("i"),
                    name = o.optString("n"),
                    grade = o.optInt("g"),
                    semester = o.optString("s"),
                    chapter = o.optString("c"),
                    desc = o.optString("d"),
                    criteria = criteria,
                    diagnosticQuestion = o.optString("dq"),
                    diagnosticAnswer = o.optString("da"),
                )
            )
        }
        return out
    }

    private fun parseQuestions(arr: JSONArray?, subject: Subject): List<CurricQuestion> {
        if (arr == null) return emptyList()
        val out = ArrayList<CurricQuestion>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val opts = ArrayList<Pair<String, String>>()
            val oa = o.optJSONArray("o")
            if (oa != null) {
                for (j in 0 until oa.length()) {
                    val pair = oa.optJSONArray(j) ?: continue
                    opts.add(pair.optString(0) to pair.optString(1))
                }
            }
            out.add(
                CurricQuestion(
                    id = o.optString("i"),
                    topicId = o.optString("ti"),
                    grade = o.optInt("g"),
                    subject = subject,
                    choice = o.optInt("t") == 2,
                    prompt = o.optString("p"),
                    options = opts,
                    answer = o.optString("a"),
                    explain = o.optString("e"),
                    difficulty = o.optInt("f", 2),
                )
            )
        }
        return out
    }

    /** 知识点 id → 名称。答案页/错题本用名称归类，不显示 cnm_p123 这种内部 id。 */
    @Synchronized
    fun topicName(subject: Subject, topicId: String): String {
        val map = topicNames.getOrPut(keyOf(subject)) {
            data(subject).topics.associate { it.id to it.name }
        }
        return map[topicId] ?: ""
    }

    private val topicNames = HashMap<String, Map<String, String>>()

    // ---------------------------------------------------------------- 查询

    /**
     * 某年级 + 勾选科目的题池。
     *
     * 三层收窄，越靠前越「对味」：
     *  1. **本年级**的题（四年级卷子就该是四年级的题）；
     *  2. 不够时补**近两年**（三、四年级），当作复习题 —— 四年级混进一道「5 的前面是几」
     *     那种一年级的题，孩子一眼就知道不是这学期的，卷子立刻显得敷衍；
     *  3. 还不够（小学一二年级只有一年可退）才放宽到全部低年级。
     *
     * 绝不取高年级的题：超前题孩子没学过，做不出来只会挫败。
     * 难度在同一年段内再筛一次，同难度不足 8 道就放宽 —— 宁可难度不齐，也不要空着不出题。
     *
     * [topicIds] 非空时是**按单元出卷**：只取这些知识点下的题，
     * 此时年级收窄整个跳过（单元本身已经框死了年级，再按「近两年」扩反而把别的单元混进来）。
     */
    fun pool(
        subjects: Set<Subject>,
        grade: Int,
        difficulty: Int,
        choice: Boolean?,
        topicIds: Set<String> = emptySet(),
    ): List<CurricQuestion> {
        val targets = if (subjects.isEmpty()) Subject.entries else subjects.toList()
        val collected = ArrayList<CurricQuestion>()
        for (s in targets) {
            for (q in data(s).questions) {
                if (topicIds.isNotEmpty() && q.topicId !in topicIds) continue
                if (q.grade > grade) continue
                if (choice != null && q.choice != choice) continue
                collected.add(q)
            }
        }
        if (collected.isEmpty()) return collected
        if (topicIds.isNotEmpty()) return byDifficulty(collected, difficulty)

        val sameGrade = collected.filter { it.grade == grade }
        if (sameGrade.size >= MIN_POOL) return byDifficulty(sameGrade, difficulty)

        val nearby = collected.filter { it.grade in (grade - 2)..grade }
        if (nearby.size >= MIN_POOL) return byDifficulty(nearby, difficulty)

        return byDifficulty(collected, difficulty)
    }

    /** 某个单元（或知识点集合）里有多少道题 —— 单元卡片上要显示「N 道」，出卷时也按它封顶 */
    fun countIn(topicIds: Set<String>, choice: Boolean): Int {
        if (topicIds.isEmpty()) return 0
        var n = 0
        Subject.entries.forEach { s ->
            data(s).questions.forEach { q ->
                if (q.topicId in topicIds && q.choice == choice) n++
            }
        }
        return n
    }

    /**
     * 知识点 → (填空题数, 选择题数)。
     *
     * 课本页一屏要摆三十来个单元，如果每个单元都调 [countIn] 全量扫一遍，
     * 两千多道题要扫三十遍。这里一次扫完缓存成 map，页面只需查表。
     */
    @Synchronized
    fun countsByTopic(subject: Subject): Map<String, Pair<Int, Int>> {
        val key = keyOf(subject)
        countCache[key]?.let { return it }
        val blank = HashMap<String, Int>()
        val choice = HashMap<String, Int>()
        data(subject).questions.forEach { q ->
            val map = if (q.choice) choice else blank
            map[q.topicId] = (map[q.topicId] ?: 0) + 1
        }
        val merged = HashMap<String, Pair<Int, Int>>(blank.size + choice.size)
        (blank.keys + choice.keys).forEach { id ->
            merged[id] = (blank[id] ?: 0) to (choice[id] ?: 0)
        }
        countCache[key] = merged
        return merged
    }

    private val countCache = HashMap<String, Map<String, Pair<Int, Int>>>()

    private fun byDifficulty(list: List<CurricQuestion>, difficulty: Int): List<CurricQuestion> {
        val same = list.filter { it.difficulty == difficulty }
        return if (same.size >= MIN_POOL / 2) same else list
    }

    /** 池子小于这个数就往外扩一层 —— 太小的话洗牌一遍都不够出一大题 */
    private const val MIN_POOL = 16

    /** 某科目某年级的知识点（含更低年级），按年级升序 */
    fun topics(subject: Subject, grade: Int): List<CurricTopic> =
        data(subject).topics.filter { it.grade <= grade }.sortedBy { it.grade }

    /**
     * 课本页用：某科目某年级**这一学年**的教材单元（不混入低年级）。
     *
     * 按 chapter 聚合，保持源数据里的先后顺序（上游是按教材顺序排的）。
     */
    @Synchronized
    fun units(subject: Subject, grade: Int): List<CurricUnit> {
        val key = "${keyOf(subject)}-$grade"
        unitCache[key]?.let { return it }

        val order = LinkedHashMap<String, MutableList<CurricTopic>>()
        val semesters = LinkedHashMap<String, String>()
        data(subject).topics.forEach { t ->
            if (t.grade != grade) return@forEach
            val unit = unitName(t.chapter)
            if (unit.isEmpty()) return@forEach
            order.getOrPut(unit) { ArrayList() }.add(t)
            semesters.putIfAbsent(unit, t.semester)
        }
        val list = order.map { (unit, topics) ->
            CurricUnit(
                name = unit,
                subject = subject,
                grade = grade,
                semester = semesters[unit].orEmpty(),
                topics = topics,
            )
        }.sortedBy { if (it.semester == "下") 1 else 0 }

        unitCache[key] = list
        return list
    }

    /**
     * 把 chapter 原文洗成能直接印在纸上的单元名。
     *
     *  「一年级上册 第1单元 5以内数的认识和加、减法」→「第1单元 5以内数的认识和加、减法」
     *  「七上 第二章 有理数的运算 2.2.2」        →「第二章 有理数的运算」
     *
     * 末尾那串小节号（2.2.2 / 1.1）要去掉：物理的 chapter 是**小节级**的，
     * 不去掉的话八年级会冒出八十个「单元」，看着像报错。
     */
    private fun unitName(chapter: String): String = chapter
        .replace(Regex("^[一二三四五六七八九]年级[上下]册\\s*"), "")
        .replace(Regex("^[七八九][上下]\\s*"), "")
        .replace(Regex("\\s*\\d+(\\.\\d+)+\\s*$"), "")
        .trim()

    private val unitCache = HashMap<String, List<CurricUnit>>()

    /**
     * 按题干原文找回源题（debug 自检用）。
     *
     * 不能借道 [pool]：pool 会按年级/难度筛，四年级的题在九年级池里根本不会出现，
     * 自检就会一直报「找不到」。
     */
    fun findByPrompt(prompt: String): CurricQuestion? {
        val p = prompt.trim()
        Subject.entries.forEach { s ->
            data(s).questions.firstOrNull { it.prompt.trim() == p }?.let { return it }
        }
        return null
    }

    // ---------------------------------------------------------------- 统计

    fun totalQuestions(): Int = stats?.optJSONObject("subjects")?.let { o ->
        o.keys().asSequence().sumOf { o.optJSONObject(it)?.optInt("questions") ?: 0 }
    } ?: 0

    fun totalTopics(): Int = stats?.optJSONObject("subjects")?.let { o ->
        o.keys().asSequence().sumOf { o.optJSONObject(it)?.optInt("topics") ?: 0 }
    } ?: 0

    /** 五科合起来的教材单元数（课本页「按单元出卷」的总规模） */
    fun totalUnits(): Int = stats?.optJSONObject("subjects")?.let { o ->
        o.keys().asSequence().sumOf { o.optJSONObject(it)?.optInt("units") ?: 0 }
    } ?: 0

    /** 这门科目下有没有这类题（语文全是选择题，没有填空题，题型就不该出现） */
    fun countOf(subject: Subject, choice: Boolean): Int {
        val o = stats?.optJSONObject("subjects")?.optJSONObject(keyOf(subject)) ?: return 0
        return o.optInt(if (choice) "choice" else "blank")
    }

    /** 来源与许可（「关于」页与 README 都要用到） */
    const val SOURCE = "https://github.com/xiaoyang111222/cn-math-taxonomy"
    const val LICENSE = "CC BY-NC 4.0"

    /** debug 用：清掉缓存（换数据文件后重新读） */
    fun clearCache() {
        cache.clear()
        topicNames.clear()
        unitCache.clear()
        countCache.clear()
    }

    /** 供导入脚本校验用：assets 里到底有没有这些文件 */
    fun hasData(): Boolean = runCatching {
        readAsset("taxonomy/index.json").isNotEmpty()
    }.getOrDefault(false)
}
