package com.papermill.app.domain.content

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * 小学科学自编题库加载器。
 *
 * ============================================================
 * 内容从哪来
 * ============================================================
 * 全部按小学科学课标的知识点**自己写**，不复制任何教辅 / 题库原题
 * （和 `bank/builtin_questions.json` 是同一套原则，见 README「版权」一节）。
 * 年级顺序参照教科版教材的大致脉络：
 *  - 一年级 认识植物 / 认识动物 / 观察物体 / 比较与测量
 *  - 二年级 我们的地球家园 / 材料 / 我们自己
 *  - 三年级 水 / 空气 / 天气 / 植物生长
 *  - 四年级 声音 / 呼吸与消化 / 运动和力 / 电路
 *  - 五年级 光 / 热 / 地球表面 / 生物与环境 / 时间的测量
 *  - 六年级 微小世界 / 地球的运动 / 能量 / 工具与技术 / 物质变化
 *
 * 数据由 `tools/build_science_bank.py` 生成，改题改脚本后重跑，不要手改 JSON。
 *
 * ============================================================
 * 为什么难度要「就近取」而不是「精确筛」
 * ============================================================
 * 每个年级每种题型只有 12 道，按难度一分就只剩 4 道 —— 出 10 道判断会凑不齐，
 * 卷子会莫名其妙短一截。所以这里按「离所选难度的距离」排序后取前 N 道：
 * 选「基础」优先给 d1，d1 用完了才补 d2、d3，既尊重难度设置，也保证题量。
 */
data class ScienceQuestion(
    val grade: Int,
    /** 1 基础 / 2 标准 / 3 提高 */
    val difficulty: Int,
    /** 知识点，如「水」「声音」「地球的运动」 */
    val topic: String,
    val kind: Kind,
    val stem: String,
    /** 选择题的选项（判断题 / 填空题为空） */
    val options: List<String>,
    /** 选择题是 A/B/C/D；判断题是 √ / ×；填空题是答案文字 */
    val answer: String,
    val explain: String,
) {
    enum class Kind { CHOICE, JUDGE, BLANK }
}

object ScienceBank {

    private var all: List<ScienceQuestion> = emptyList()

    fun init(ctx: Context): Int = runCatching {
        load(ctx.assets.open("science/bank.json").bufferedReader().use { it.readText() })
    }.getOrDefault(0)

    fun load(json: String): Int {
        val arr = JSONObject(json).optJSONArray("questions") ?: JSONArray()
        val list = ArrayList<ScienceQuestion>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val optsArray = o.optJSONArray("options")
            val opts = ArrayList<String>()
            if (optsArray != null) {
                for (j in 0 until optsArray.length()) opts.add(optsArray.optString(j))
            }
            val stem = o.optString("stem")
            val answer = o.optString("answer")
            // 题干或答案空的条目直接跳过：印出去会变成一道没法做的题
            if (stem.isBlank() || answer.isBlank()) continue
            list.add(
                ScienceQuestion(
                    grade = o.optInt("grade", 1),
                    difficulty = o.optInt("difficulty", 2),
                    topic = o.optString("topic"),
                    kind = when (o.optString("kind")) {
                        "choice" -> ScienceQuestion.Kind.CHOICE
                        "judge" -> ScienceQuestion.Kind.JUDGE
                        else -> ScienceQuestion.Kind.BLANK
                    },
                    stem = stem,
                    options = opts,
                    answer = answer,
                    explain = o.optString("explain"),
                )
            )
        }
        all = list
        return list.size
    }

    fun total(): Int = all.size

    fun countOf(grade: Int, kind: ScienceQuestion.Kind): Int =
        all.count { it.grade == grade && it.kind == kind }

    fun pool(grade: Int, kind: ScienceQuestion.Kind): List<ScienceQuestion> =
        all.filter { it.grade == grade && it.kind == kind }

    /** 按原题干反查（调试体检用：核对洗牌后的答案字母有没有指错选项） */
    fun findByStem(stem: String): ScienceQuestion? {
        val key = stem.trim()
        return all.firstOrNull { it.stem.trim() == key }
    }

    /** 就近难度取 [count] 道：先按 |难度差| 升序（稳定排序），再让调用方洗牌 */
    fun draw(
        grade: Int,
        kind: ScienceQuestion.Kind,
        difficulty: Int,
        count: Int,
    ): List<ScienceQuestion> {
        val pool = pool(grade, kind)
        if (pool.isEmpty() || count <= 0) return emptyList()
        val ordered = pool.sortedBy { abs(it.difficulty - difficulty) }
        if (kind != ScienceQuestion.Kind.JUDGE) return ordered.take(count)

        // 判断题把 √ / × 对半分。
        // 随机抽 10 道很容易抽成「八个√两个×」，学生连蒙带猜就能过 ——
        // 那不是测理解，是测运气。一半一半才逼着每道都真的想一下。
        val yes = ordered.filter { it.answer.trim() == "√" }
        val no = ordered.filter { it.answer.trim() == "×" }
        val out = ArrayList<ScienceQuestion>(count)
        out.addAll(yes.take(count - count / 2))
        out.addAll(no.take(count / 2))
        if (out.size < count) {
            out.addAll(ordered.filter { it !in out }.take(count - out.size))
        }
        return out
    }
}
