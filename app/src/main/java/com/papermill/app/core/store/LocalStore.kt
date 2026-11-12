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

data class MistakeItem(
    val id: String,
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

    private fun f(ctx: Context, name: String) = File(ctx.filesDir, name)

    private fun read(ctx: Context, name: String): JSONArray {
        val file = f(ctx, name)
        if (!file.exists()) return JSONArray()
        return runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
    }

    private fun write(ctx: Context, name: String, arr: JSONArray) {
        f(ctx, name).writeText(arr.toString(2))
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
    fun addMistakes(ctx: Context, paper: GeneratedPaper, paperId: String, numbers: Set<Int>): Int {
        val list = loadMistakes(ctx).toMutableList()
        val existing = list.associateBy { stableHash(it.stem) }.toMutableMap()
        var added = 0
        paper.questions.filter { it.no in numbers }.forEach { q ->
            val hash = stableHash(q.stem)
            val hit = existing[hash]
            if (hit != null) {
                hit.mastery = "NEW"
                hit.practiceCount += 1
            } else {
                val item = MistakeItem(
                    id = "m_${System.currentTimeMillis()}_${q.no}",
                    grade = paper.spec.grade, subject = paper.spec.subject.code,
                    type = q.type.name, topic = q.topic, stem = q.stem, answer = q.answer,
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
