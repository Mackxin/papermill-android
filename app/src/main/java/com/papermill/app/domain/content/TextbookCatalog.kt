package com.papermill.app.domain.content

import android.content.Context
import com.papermill.app.domain.generate.Subject
import org.json.JSONObject

/**
 * 教材目录（TapXWorld/ChinaTextbook）。
 *
 * ============================================================
 * 这里为什么只有链接，没有一页 PDF
 * ============================================================
 * 那个仓库 **没有 LICENSE**（GitHub API 返回 license: null），
 * 而教材本身的著作权属于人教社等出版社 —— 把 PDF 打进公开的 APK 就是二次分发，明确侵权。
 * 体积上也根本不可能：仓库 41.5GB，光人教版小学数学 12 册就 294MB。
 *
 * 所以本 App 只做「目录 + 链接」：点了用浏览器打开原仓库，内容始终从原站加载。
 * 换句话说，App 从不持有、也不转发任何一页教材。
 */
data class TextbookEntry(
    /** 小学 / 初中 */
    val stage: String,
    val subject: Subject,
    val publisher: String,
    val grade: Int,
    /** 上 / 下 / 全 */
    val term: String,
    val title: String,
    val url: String,
    val sizeMb: Float,
    /** 分片数：>1 表示原仓库把它切成 .pdf.1 .pdf.2，用户需自行合并 */
    val parts: Int,
) {
    val termLabel: String
        get() = when (term) {
            "上" -> "上册"; "下" -> "下册"; else -> "全一册"
        }
}

object TextbookCatalog {

    private var entries: List<TextbookEntry> = emptyList()
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        entries = runCatching {
            val json = ctx.assets.open("textbook/catalog.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(json).optJSONArray("books") ?: return@runCatching emptyList()
            val out = ArrayList<TextbookEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val subj = subjectOf(o.optString("sub")) ?: continue
                out.add(
                    TextbookEntry(
                        stage = o.optString("s"),
                        subject = subj,
                        publisher = o.optString("p"),
                        grade = o.optInt("g"),
                        term = o.optString("t"),
                        title = o.optString("n"),
                        url = o.optString("u"),
                        sizeMb = o.optDouble("mb", 0.0).toFloat(),
                        parts = o.optInt("k", 1),
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    /** 字符串 → 科目枚举。汉字科目名不可能撞车，直接 when。 */
    private fun subjectOf(label: String): Subject? = when (label) {
        "语文" -> Subject.CHINESE
        "数学" -> Subject.MATH
        "英语" -> Subject.ENGLISH
        "物理" -> Subject.PHYSICS
        "化学" -> Subject.CHEMISTRY
        else -> null
    }

    val size: Int get() = entries.size

    /** 某个年级 + 某科的所有册次（上 / 下 / 全），按册次排序 */
    fun books(grade: Int, subject: Subject): List<TextbookEntry> =
        entries.filter { it.grade == grade && it.subject == subject }
            .sortedBy { if (it.term == "上") 0 else if (it.term == "下") 1 else 2 }

    /** 该年级有教材的科目（用来把没有教材的科目置灰） */
    fun subjectsAt(grade: Int): Set<Subject> =
        entries.filter { it.grade == grade }.map { it.subject }.toSet()

    fun totalSizeMb(): Int = entries.sumOf { it.sizeMb.toDouble() }.toInt()

    const val SOURCE = "https://github.com/TapXWorld/ChinaTextbook"
}
