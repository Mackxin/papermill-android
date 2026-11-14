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
                questions = parseQuestions(root.optJSONArray("questions")),
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

    private fun parseQuestions(arr: JSONArray?): List<CurricQuestion> {
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

    // ---------------------------------------------------------------- 查询

    /**
     * 某年级 + 勾选科目的题池。
     *
     * 跨年级混出：只取 `grade <= 当前年级` 的题（低年级复习题可以用，高年级超前题不能）。
     * 难度：优先取同难度的，不够再放宽 —— 宁可难度不齐，也不要空着不出题。
     */
    fun pool(
        subjects: Set<Subject>,
        grade: Int,
        difficulty: Int,
        choice: Boolean?,
    ): List<CurricQuestion> {
        val targets = if (subjects.isEmpty()) Subject.entries else subjects.toList()
        val all = ArrayList<CurricQuestion>()
        targets.forEach { s ->
            data(s).questions.forEach { q ->
                if (q.grade <= grade && (choice == null || q.choice == choice)) all.add(q)
            }
        }
        if (all.isEmpty()) return all
        val same = all.filter { it.difficulty == difficulty }
        return if (same.size >= 8) same else all
    }

    /** 某科目某年级的知识点（含更低年级），按年级升序 */
    fun topics(subject: Subject, grade: Int): List<CurricTopic> =
        data(subject).topics.filter { it.grade <= grade }.sortedBy { it.grade }

    // ---------------------------------------------------------------- 统计

    fun totalQuestions(): Int = stats?.optJSONObject("subjects")?.let { o ->
        o.keys().asSequence().sumOf { o.optJSONObject(it)?.optInt("questions") ?: 0 }
    } ?: 0

    fun totalTopics(): Int = stats?.optJSONObject("subjects")?.let { o ->
        o.keys().asSequence().sumOf { o.optJSONObject(it)?.optInt("topics") ?: 0 }
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
    }

    /** 供导入脚本校验用：assets 里到底有没有这些文件 */
    fun hasData(): Boolean = runCatching {
        readAsset("taxonomy/index.json").isNotEmpty()
    }.getOrDefault(false)
}
