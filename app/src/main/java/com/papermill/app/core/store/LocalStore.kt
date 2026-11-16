package com.papermill.app.core.store

import android.content.Context
import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.GeneratedPaper
import com.papermill.app.domain.generate.PaperSpec
import com.papermill.app.domain.generate.QuestionType
import com.papermill.app.domain.generate.Subject
import com.papermill.app.domain.generate.stableHash
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedQuestion(
    val no: Int,
    val stem: String,
    val answer: String,
    val type: String,
    val topic: String,
)

data class SavedPaper(
    val id: String,
    val title: String,
    val grade: Int,
    val subject: String,
    val createdAt: Long,
    val counts: Map<String, Int>,
    val fileName: String,
    val questions: List<SavedQuestion>,
)

/**
 * 全局偏好设置（单独一个 settings.json，不和试卷库混在一起）。
 *
 * [dockGlass]：底部 tab 栏是否用半透明玻璃效果。
 * 默认 **false = 不透明实底** —— 透明会透出下面滚动的卡片文字，可读性打折扣，
 * 所以做成「想要的人自己去设置里开」，而不是所有人一起忍受。
 *
 * v0.17.0 起把这套**卷面样式**也放进来：姓名栏 / 一行标题 / 页脚 / 行距。
 * 用户的原话是「这些可以后台隐藏掉」「行间距后台自定义」——
 * 「后台」在这里就是「设一次、以后每张卷都这样」，所以必须跨启动保存，
 * 不能只活在当前会话里。
 */
data class AppSettings(
    val dockGlass: Boolean = false,
    val showNameRow: Boolean = true,
    val headerOneLine: Boolean = false,
    /** 0 默认文案 / 1 自定义 / 2 不印 */
    val footerMode: Int = 0,
    val footerCustom: String = "",
    val showCreatedAt: Boolean = true,
    /** LineGap 的枚举名；存字符串而不是序号，将来加档位不会错位 */
    val lineGap: String = "NORMAL",
    /**
     * 底部 tab 栏显示哪几个页签（v0.18.0）。
     *
     * 存逗号分隔的枚举名而不是 JSON 数组：这份数据只有「有序的一小串名字」，
     * 数组要额外处理空值/脏值，字符串简单过滤一下就够了。
     * 顺序即展示顺序 —— 用户想「错题本放第一格」也在这一串里表达。
     */
    val dockTabs: String = "HOME,REFERENCE,MISTAKES,HISTORY,TEXTBOOK,MORE",
)

/**
 * 「上次看到哪儿」的快照。
 *
 * 为什么连页签都要记：用户往往是在错题本/打印纸那一页退出的，
 * 重进却回到首页，等于每次都要自己再点一遍。
 *
 * [scrolls] 的 key 是页签或页面名，value 是 `[firstVisibleItemIndex, offset]`；
 * 记 index + offset 而不是像素高度，因为字体/屏宽变化后像素值会漂。
 */
data class SessionState(
    val tab: String = "HOME",
    val screen: String = "HOME",
    val previewPage: Int = 0,
    /** 退出时正在看的那张卷（试卷库 id），重进后按它复原预览页 */
    val paperId: String = "",
    /** 参数页用的是 ScrollState（像素），单独记一个 */
    val createScroll: Int = 0,
    val scrolls: Map<String, List<Int>> = emptyMap(),
)

data class MistakeItem(    val id: String,
    val grade: Int,
    val subject: String,
    val type: String,
    val topic: String,
    val stem: String,
    val answer: String,
    val sourcePaperId: String,
    val sourceNo: Int,
    val wrongAt: Long,
    /** NEW / PRACTICED / MASTERED */
    var mastery: String,
    var practiceCount: Int,
)

/**
 * 纯本地持久化：两份 JSON（试卷库 + 错题本）。
 *
 * 首版刻意不引 Room：数据量很小（几百条），JSON 足够，且备份/恢复天然就是「拷一个文件」。
 * 数据层接口保持独立，将来换 Room 只改这个文件，UI 一行不用动。
 */
object LocalStore {

    private const val FILE_PAPERS = "papers.json"
    private const val FILE_MISTAKES = "mistakes.json"
    private const val FILE_SETTINGS = "settings.json"
    private const val FILE_SESSION = "session.json"

    private fun f(ctx: Context, name: String) = File(ctx.filesDir, name)

    private fun read(ctx: Context, name: String): JSONArray {
        val file = f(ctx, name)
        if (!file.exists()) return JSONArray()
        return runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
    }

    private fun write(ctx: Context, name: String, arr: JSONArray) {
        f(ctx, name).writeText(arr.toString(2))
    }

    // -------------------------------------------------------------- 偏好设置

    fun loadSettings(ctx: Context): AppSettings {
        val file = f(ctx, FILE_SETTINGS)
        if (!file.exists()) return AppSettings()
        return runCatching {
            val o = JSONObject(file.readText())
            AppSettings(
                dockGlass = o.optBoolean("dockGlass", false),
                showNameRow = o.optBoolean("showNameRow", true),
                headerOneLine = o.optBoolean("headerOneLine", false),
                footerMode = o.optInt("footerMode", 0),
                footerCustom = o.optString("footerCustom", ""),
                showCreatedAt = o.optBoolean("showCreatedAt", true),
                lineGap = o.optString("lineGap", "NORMAL"),
                dockTabs = o.optString("dockTabs", "HOME,REFERENCE,MISTAKES,HISTORY,TEXTBOOK,MORE"),
            )
        }.getOrDefault(AppSettings())
    }

    fun saveSettings(ctx: Context, settings: AppSettings) {
        runCatching {
            f(ctx, FILE_SETTINGS).writeText(
                JSONObject()
                    .put("dockGlass", settings.dockGlass)
                    .put("showNameRow", settings.showNameRow)
                    .put("headerOneLine", settings.headerOneLine)
                    .put("footerMode", settings.footerMode)
                    .put("footerCustom", settings.footerCustom)
                    .put("showCreatedAt", settings.showCreatedAt)
                    .put("lineGap", settings.lineGap)
                    .put("dockTabs", settings.dockTabs)
                    .toString(2)
            )
        }
    }

    // -------------------------------------------------------------- 上次位置

    /**
     * 上次浏览到哪儿：页签、页面、预览页码、正在看的那张卷、各列表的滚动位置。
     *
     * 单独存一个文件而不是塞进 settings：这份数据每天都在变，
     * 混进用户设置里会让备份/恢复把「我在哪」一起带走，没意义。
     */
    fun loadSession(ctx: Context): SessionState {
        val file = f(ctx, FILE_SESSION)
        if (!file.exists()) return SessionState()
        return runCatching {
            val o = JSONObject(file.readText())
            val scrolls = LinkedHashMap<String, List<Int>>()
            o.optJSONObject("scrolls")?.let { s ->
                s.keys().forEach { k ->
                    val a = s.optJSONArray(k) ?: return@forEach
                    if (a.length() >= 2) scrolls[k] = listOf(a.getInt(0), a.getInt(1))
                }
            }
            SessionState(
                tab = o.optString("tab", "HOME"),
                screen = o.optString("screen", "HOME"),
                previewPage = o.optInt("previewPage", 0),
                paperId = o.optString("paperId", ""),
                createScroll = o.optInt("createScroll", 0),
                scrolls = scrolls,
            )
        }.getOrDefault(SessionState())
    }

    fun saveSession(ctx: Context, session: SessionState) {
        runCatching {
            val scrolls = JSONObject()
            session.scrolls.forEach { (k, v) ->
                scrolls.put(k, JSONArray().put(v.getOrElse(0) { 0 }).put(v.getOrElse(1) { 0 }))
            }
            f(ctx, FILE_SESSION).writeText(
                JSONObject()
                    .put("tab", session.tab)
                    .put("screen", session.screen)
                    .put("previewPage", session.previewPage)
                    .put("paperId", session.paperId)
                    .put("createScroll", session.createScroll)
                    .put("scrolls", scrolls)
                    .toString(2)
            )
        }
    }

    // -------------------------------------------------------------- 试卷库

    fun loadPapers(ctx: Context): List<SavedPaper> {
        val arr = read(ctx, FILE_PAPERS)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val counts = LinkedHashMap<String, Int>()
                val co = o.optJSONObject("counts") ?: JSONObject()
                co.keys().forEach { k -> counts[k] = co.optInt(k) }
                val qs = ArrayList<SavedQuestion>()
                val qa = o.optJSONArray("questions") ?: JSONArray()
                for (j in 0 until qa.length()) {
                    val q = qa.getJSONObject(j)
                    qs.add(
                        SavedQuestion(
                            q.optInt("no"), q.optString("stem"), q.optString("answer"),
                            q.optString("type"), q.optString("topic"),
                        )
                    )
                }
                SavedPaper(
                    id = o.optString("id"), title = o.optString("title"),
                    grade = o.optInt("grade"), subject = o.optString("subject"),
                    createdAt = o.optLong("createdAt"), counts = counts,
                    fileName = o.optString("fileName"), questions = qs,
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    fun savePaper(ctx: Context, paper: GeneratedPaper, fileName: String): SavedPaper {
        val s = paper.spec
        val counts = LinkedHashMap<String, Int>()
        s.counts.forEach { (t, c) -> if (c > 0) counts[t.name] = c }

        val qa = JSONArray()
        paper.questions.forEach { q ->
            qa.put(JSONObject().apply {
                put("no", q.no); put("stem", q.stem); put("answer", q.answer)
                put("type", q.type.name); put("topic", q.topic)
            })
        }
        val id = "p_${System.currentTimeMillis()}"
        val saved = SavedPaper(
            id = id, title = s.title, grade = s.grade, subject = s.subject.code,
            createdAt = paper.createdAt, counts = counts, fileName = fileName,
            questions = paper.questions.map {
                SavedQuestion(it.no, it.stem, it.answer, it.type.name, it.topic)
            },
        )
        val arr = read(ctx, FILE_PAPERS)
        arr.put(JSONObject().apply {
            put("id", id); put("title", s.title); put("grade", s.grade)
            put("subject", s.subject.code); put("createdAt", paper.createdAt)
            put("counts", JSONObject().apply { counts.forEach { (k, v) -> put(k, v) } })
            put("fileName", fileName); put("questions", qa)
        })
        // 只留最近 100 张，避免 JSON 无限膨胀
        while (arr.length() > 100) arr.remove(0)
        write(ctx, FILE_PAPERS, arr)
        return saved
    }

    /** 最近几张同科目卷的题目 hash，用于「别老是出重复的题」 */
    fun recentHashes(ctx: Context, grade: Int, subject: Subject, take: Int = 5): Set<Long> {
        val out = HashSet<Long>()
        loadPapers(ctx)
            .filter { it.grade == grade && it.subject == subject.code }
            .take(take)
            .forEach { p -> p.questions.forEach { out.add(stableHash(it.stem)) } }
        return out
    }

    // -------------------------------------------------------------- 错题本

    fun loadMistakes(ctx: Context): List<MistakeItem> {
        val arr = read(ctx, FILE_MISTAKES)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                MistakeItem(
                    id = o.optString("id"), grade = o.optInt("grade"),
                    subject = o.optString("subject"), type = o.optString("type"),
                    topic = o.optString("topic"), stem = o.optString("stem"),
                    answer = o.optString("answer"),
                    sourcePaperId = o.optString("sourcePaperId"),
                    sourceNo = o.optInt("sourceNo"), wrongAt = o.optLong("wrongAt"),
                    mastery = o.optString("mastery", "NEW"),
                    practiceCount = o.optInt("practiceCount"),
                )
            }.getOrNull()
        }.sortedByDescending { it.wrongAt }
    }

    private fun saveMistakes(ctx: Context, list: List<MistakeItem>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id); put("grade", m.grade); put("subject", m.subject)
                put("type", m.type); put("topic", m.topic); put("stem", m.stem)
                put("answer", m.answer); put("sourcePaperId", m.sourcePaperId)
                put("sourceNo", m.sourceNo); put("wrongAt", m.wrongAt)
                put("mastery", m.mastery); put("practiceCount", m.practiceCount)
            })
        }
        write(ctx, FILE_MISTAKES, arr)
    }

    /** 按题干 hash 去重；已经存在则把掌握度打回 NEW（又错了一次） */
    fun addMistakes(ctx: Context, paper: GeneratedPaper, paperId: String, numbers: Set<Int>): Int =
        addMistakesCore(
            ctx = ctx,
            grade = paper.spec.grade,
            subjectCode = paper.spec.subject.code,
            paperId = paperId,
            questions = paper.questions.map {
                SavedQuestion(it.no, it.stem, it.answer, it.type.name, it.topic)
            },
            numbers = numbers,
        )

    /**
     * 从试卷库里的历史卷记错题（v0.18.0）。
     *
     * 孩子做完卷子往往是过一阵才批改，那会儿手边只剩纸，App 早就退到后台了。
     * 这条路径不重新渲染 PDF，直接用存下来的题目快照，所以点开就能勾。
     */
    fun addMistakesFromSaved(ctx: Context, paper: SavedPaper, numbers: Set<Int>): Int =
        addMistakesCore(ctx, paper.grade, paper.subject, paper.id, paper.questions, numbers)

    /**
     * 只带题目清单的批改入口：批改面板可能来自预览页（刚生成的卷），
     * 也可能来自试卷库（几天前的卷），两者都归一到这里。
     */
    fun addMistakesForPaper(
        ctx: Context,
        grade: Int,
        subjectCode: String,
        paperId: String,
        questions: List<SavedQuestion>,
        numbers: Set<Int>,
    ): Int = addMistakesCore(ctx, grade, subjectCode, paperId, questions, numbers)

    private fun addMistakesCore(
        ctx: Context,
        grade: Int,
        subjectCode: String,
        paperId: String,
        questions: List<SavedQuestion>,
        numbers: Set<Int>,
    ): Int {
        val list = loadMistakes(ctx).toMutableList()
        val existing = list.associateBy { stableHash(it.stem) }.toMutableMap()
        var added = 0
        questions.filter { it.no in numbers }.forEach { q ->
            val hash = stableHash(q.stem)
            val hit = existing[hash]
            if (hit != null) {
                hit.mastery = "NEW"
                hit.practiceCount += 1
            } else {
                val item = MistakeItem(
                    id = "m_${System.currentTimeMillis()}_${q.no}",
                    grade = grade, subject = subjectCode,
                    type = q.type, topic = q.topic, stem = q.stem, answer = q.answer,
                    sourcePaperId = paperId, sourceNo = q.no,
                    wrongAt = System.currentTimeMillis(), mastery = "NEW", practiceCount = 0,
                )
                list.add(0, item)
                existing[hash] = item
                added++
            }
        }
        saveMistakes(ctx, list)
        return added
    }

    fun setMastery(ctx: Context, id: String, mastery: String) {
        val list = loadMistakes(ctx).toMutableList()
        list.firstOrNull { it.id == id }?.mastery = mastery
        saveMistakes(ctx, list)
    }

    fun markPracticed(ctx: Context, ids: Collection<String>) {
        val list = loadMistakes(ctx).toMutableList()
        list.filter { it.id in ids }.forEach {
            it.practiceCount += 1
            it.mastery = "PRACTICED"
        }
        saveMistakes(ctx, list)
    }

    // -------------------------------------------------------------- 备份 / 恢复

    fun exportAll(ctx: Context): String = JSONObject().apply {
        put("version", 1)
        put("papers", read(ctx, FILE_PAPERS))
        put("mistakes", read(ctx, FILE_MISTAKES))
    }.toString(2)

    fun importAll(ctx: Context, text: String): Boolean = runCatching {
        val o = JSONObject(text)
        write(ctx, FILE_PAPERS, o.getJSONArray("papers"))
        write(ctx, FILE_MISTAKES, o.getJSONArray("mistakes"))
        true
    }.getOrDefault(false)

    // -------------------------------------------------------------- 历史卷还原

    fun toSpec(p: SavedPaper): PaperSpec {
        val counts = LinkedHashMap<QuestionType, Int>()
        p.counts.forEach { (k, v) -> runCatching { counts[QuestionType.valueOf(k)] = v } }
        val subject = runCatching { Subject.valueOf(p.subject) }.getOrDefault(Subject.MATH)
        return PaperSpec(
            grade = p.grade, subject = subject, title = p.title,
            subtitle = "历史卷 · 共 ${p.questions.size} 题",
            counts = counts, difficulty = 2, columns = 2,
            answerMode = AnswerMode.LAST_PAGE, showNameRow = true,
            fontSize = 11f, seed = p.createdAt, isMistakePaper = false,
        )
    }
}
