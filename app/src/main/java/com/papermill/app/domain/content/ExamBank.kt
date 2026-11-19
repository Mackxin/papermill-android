package com.papermill.app.domain.content

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 真题库里的一道题 —— 从下载的公开分享试卷（docx）里切出来的原题。
 *
 * 和自编题库（[BankQuestionLoader]）的区别：这道题是**某一年某张真卷上的原题**，
 * 有出处、有原始大题名，答案也是卷子自己给的；自编题库那批是按知识点归纳的。
 */
data class ExamQuestion(
    /** 题干（选择题已把选项并进来，跟自编题库生成器一个做法） */
    val stem: String,
    val answer: String,
    /** 原卷题型：FILL / CHOICE / JUDGE / WORD / CALC / OTHER，见 tools/build-question-bank.py */
    val type: String,
    val grade: Int,
    /** MATH / CHINESE / SCIENCE / ENGLISH */
    val subject: String,
    /** 原卷里的大题名（「填空题」「判断题」…），答案页与错题本按它归类 */
    val section: String,
    /** 来源卷 id。同一份卷切出来的题共用它 —— 出卷时按它轮转，别让一张卷霸屏 */
    val source: String = "",
    /** 来源卷名（「人教版一年级数学上册期末卷」），抽查页显示出处用 */
    val sourceTitle: String = "",
)

/**
 * 真题库加载器。
 *
 * 为什么懒加载：题库有几 MB、上万道题，跟着 App 启动一起读会让冷启动多等好几秒。
 * 用户第一次进「真题」那张卡片时才在后台线程读，读完把 [ready] 置真让界面重画。
 */
object ExamBank {
    private val started = AtomicBoolean(false)

    @Volatile
    var items: List<ExamQuestion> = emptyList()
        private set

    @Volatile
    var ready: Boolean = false
        private set

    @Volatile
    var failed: Boolean = false
        private set

    /** 某个年级 + 科目 + 大类下有多少道（界面用来提示「这个年级还没真题」） */
    fun countOf(grade: Int, subjects: Set<String>, types: Set<String>): Int =
        items.count { it.grade == grade && it.subject in subjects && it.type in types }

    /**
     * 后台读一次。重复调用无害（[started] 拦住）。
     * 读完在**后台线程**回调 [onReady]，调用方自己切回主线程再刷界面。
     */
    fun warmUp(ctx: Context, onReady: () -> Unit = {}) {
        if (!started.compareAndSet(false, true)) {
            if (ready) onReady()
            return
        }
        Thread {
            try {
                val raw = ctx.assets.open(PATH).bufferedReader().use { it.readText() }
                items = parse(raw)
                ready = true
            } catch (e: Throwable) {
                // 精简安装包 / 旧版本升级都可能没有这个 assets 文件，空库不算错误
                failed = true
            }
            onReady()
        }.apply { isDaemon = true; name = "exam-bank" }.start()
    }

    private fun parse(raw: String): List<ExamQuestion> {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("items") ?: return emptyList()
        // 卷名在顶层存了一份（题里只留 id）——先摊成 id → 卷名，再逐题挂上去
        val names = HashMap<String, String>()
        root.optJSONObject("sources")?.let { obj ->
            obj.keys().forEach { k -> names[k] = obj.optString(k) }
        }
        val out = ArrayList<ExamQuestion>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val stem = o.optString("q").trim()
            if (stem.length < 6) continue
            val opts = o.optJSONArray("o")
            val full = if (opts != null && opts.length() > 0) {
                val sb = StringBuilder(stem)
                for (k in 0 until opts.length()) sb.append('　').append(opts.optString(k))
                sb.toString()
            } else {
                stem
            }
            val src = o.optString("src")
            out.add(
                ExamQuestion(
                    stem = full,
                    answer = o.optString("a"),
                    type = o.optString("t"),
                    grade = o.optInt("g"),
                    subject = o.optString("s"),
                    section = o.optString("sec"),
                    source = src,
                    sourceTitle = names[src].orEmpty(),
                )
            )
        }
        return out
    }

    private const val PATH = "papers/questions.json"
}
