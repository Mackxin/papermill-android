package com.papermill.app.domain.content

import android.content.Context
import com.papermill.app.domain.generate.GradeProfiles
import org.json.JSONObject

/**
 * 名卷库 —— shijuan.on2on.cn「小学试卷库」那份下载目录的本地索引（776 条）。
 *
 * ============================================================
 * 这里存的是「索引」，不是「试卷」
 * ============================================================
 * 那个站的试卷本身是网盘里的 PDF / Word，页面上一个题目字都没有，只有标题和一个
 * 网盘链接。所以我们也不打包、不转载任何文件：这份表里只有标题、年级科目类型，
 * 和一条指向网盘的链接。用户点了是自己去网盘取，跟在他浏览器里点一遍一样。
 *
 * 数据由 `tools/fetch-papers.py` 抓取生成，两个端共用同一份 `papers/library.json`
 * （网页版在 `web/data/papers/`，App 在 `assets/papers/`），别手改 JSON。
 *
 * 一条经验：这类分享链接失效是常态（分享者删了、网盘清理了）。所以界面上要写清楚
 * 「打不开就换一条」，不能让用户以为是 App 坏了。
 */
data class PaperResource(
    val id: String,
    val title: String,
    /** 1~6 */
    val grade: Int,
    /** MATH / CHINESE / ENGLISH / SCIENCE */
    val subject: String,
    /** 期末测试 / 期中测试 / 单元测试 … */
    val type: String,
    /** 上学期 / 下学期 */
    val term: String,
    val year: Int,
    val url: String,
) {
    /** 印在标题下面的那一行小字：四年级 · 数学 · 期末测试 · 上学期 */
    val meta: String
        get() = listOf(
            // 用 GradeProfiles 的说法（「六年级」），跟筛选 chip 上写的保持一致 ——
            // 卡片上印「6年级」、上面 chip 写「六年级」，看着像两套东西
            if (grade > 0) GradeProfiles.gradeLabel(grade) else "",
            PaperLibrary.subjectName(subject),
            type,
            term,
            if (year > 0) "$year 年" else "",
        ).filter { it.isNotEmpty() }.joinToString(" · ")
}

object PaperLibrary {

    private var all: List<PaperResource> = emptyList()
    private var grades: List<Int> = emptyList()
    private var subjects: List<String> = emptyList()
    private var types: List<String> = emptyList()

    fun subjectName(code: String): String = when (code) {
        "MATH" -> "数学"
        "CHINESE" -> "语文"
        "ENGLISH" -> "英语"
        "SCIENCE" -> "科学"
        else -> code
    }

    fun init(ctx: Context): Int = runCatching {
        val text = ctx.assets.open("papers/library.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val arr = root.optJSONArray("items") ?: return@runCatching 0
        val list = ArrayList<PaperResource>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val url = o.optString("u")
            val title = o.optString("n")
            // 没标题或没链接的记录没有任何用处，直接丢掉
            if (url.isEmpty() || title.isEmpty()) continue
            list += PaperResource(
                id = o.optString("i"),
                title = title,
                grade = o.optInt("g"),
                subject = o.optString("s"),
                type = o.optString("t"),
                term = o.optString("m"),
                year = o.optInt("y"),
                url = url,
            )
        }
        all = list
        grades = list.map { it.grade }.filter { it > 0 }.distinct().sorted()
        // 科目按「数学语文英语科学」的固定顺序，不按出现次数 —— 每次打开顺序都一样
        val order = listOf("MATH", "CHINESE", "ENGLISH", "SCIENCE")
        subjects = order.filter { code -> list.any { it.subject == code } }
        types = list.map { it.type }.filter { it.isNotEmpty() }.distinct()
        list.size
    }.getOrDefault(0)

    val total: Int get() = all.size
    val allGrades: List<Int> get() = grades
    val allSubjects: List<String> get() = subjects
    val allTypes: List<String> get() = types

    fun countOfGrade(g: Int): Int = all.count { it.grade == g }
    fun countOfSubject(s: String): Int = all.count { it.subject == s }
    fun countOfType(t: String): Int = all.count { it.type == t }

    /**
     * 筛选。三个条件互相独立，空值 = 不限。
     *
     * 关键词只搜标题：这些标题本身就是「哪个版本 + 哪个年级 + 哪一册 + 什么类型」，
     * 搜「苏教版」「第五单元」「圆的」都能直接命中，比另做一套标签更好用。
     */
    fun query(grade: Int = 0, subject: String = "", type: String = "", keyword: String = ""): List<PaperResource> {
        val kw = keyword.trim()
        return all.filter { p ->
            (grade == 0 || p.grade == grade) &&
                (subject.isEmpty() || p.subject == subject) &&
                (type.isEmpty() || p.type == type) &&
                (kw.isEmpty() || p.title.contains(kw))
        }
    }
}
