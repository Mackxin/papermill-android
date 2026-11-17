package com.papermill.app.domain.generate

import java.util.Random

/**
 * 自编题（v0.19.0）：用户自己写的一批题，原样排进卷子。
 *
 * ============================================================
 * 为什么要有它
 * ============================================================
 * 内置 49 种题型再全，也覆盖不到「老师今天发的这 20 个词」「这周错的 8 道竖式」。
 * 用户的原话是「自定义出题的自由度还是不够高，有什么办法可以自由组合的文字」——
 * 要的是**把自己的内容交给 App 排版**，而不是再从题库里随机抽。
 *
 * 所以这里不生成题，只做「一行 → 一道题」的转换。五种模式覆盖了家里最常练的几类：
 * 算式、挖空填空、英语单词、看词抄写、完全自己写的题干。
 *
 * ============================================================
 * 为什么去重放在这一层
 * ============================================================
 * 用户同一段话里复制粘贴两遍、或者词表里本来就有重复是很常见的。
 * 印出来两道一模一样的题等于白占一行，所以解析时**按出现顺序去重**，
 * 并把这个「去掉了几条」回传给界面说清楚 —— 静默吞掉内容比重复更糟。
 */

/** 一行文字怎么变成一道题 */
enum class CustomMode(
    val label: String,
    val hint: String,
    val sample: String,
    /** 这种模式默认留几条答题线 */
    val answerLines: Int,
) {
    CALC(
        "算式",
        "每行一个算式，App 自动算答案；除不尽会印成「商……余数」",
        "24+18\n7×8\n96÷4\n7÷2",
        0,
    ),
    BLANK(
        "挖空填空",
        "用 [ ] 圈出要挖掉的词，其余照原样印",
        "太阳从[东方]升起\n[春天]来了，燕子飞回来了",
        1,
    ),
    WORD_EN(
        "单词默写",
        "每行「英文 中文」，纸上印中文、孩子写英文",
        "apple 苹果\nbanana 香蕉\norange 橙子",
        1,
    ),
    COPY(
        "看词抄写",
        "每行一个词，印出来并留一条书写线（适合听写前抄一遍）",
        "春天\n柳树\n燕子\n小溪",
        1,
    ),
    PLAIN(
        "直接成题",
        "每行一道完整的题干；想附答案就写「题干 | 答案」",
        "3 个苹果吃掉 1 个，还剩几个？|2 个\n写出三个偏旁是「氵」的字|江 河 湖",
        1,
    ),
    ;

    companion object {
        fun of(name: String?): CustomMode = entries.firstOrNull { it.name == name } ?: PLAIN
    }
}

/**
 * 一份自编题。多份可以同时挂在同一张卷上（比如「词语抄写」+「口算自定义」）。
 *
 * [studentId] 为空表示「所有学生共用」；非空时只在那位学生被选中时出现 ——
 * 两个孩子各有各的词表，不能混在一张纸上。
 */
data class CustomSheet(
    val id: String,
    val studentId: String = "",
    val title: String,
    val mode: CustomMode = CustomMode.PLAIN,
    val raw: String = "",
    /** 一行几栏：题干长（应用题 / 句子）就填 1 */
    val columns: Int = 2,
    /** 每题下面留几条书写线 */
    val answerLines: Int = 1,
    /** 打乱顺序：默认开，同一批词出第二张卷时顺序就不一样了 */
    val shuffle: Boolean = true,
    /** 只用前 N 条（0 = 全部）。想「这 40 个词今天只考 20 个」时用 */
    val count: Int = 0,
    /** 每题分值，只影响大题标题里印的「每题 N 分」 */
    val points: Int = 2,
) {
    /** 用户原文按行切开，去掉空行 */
    fun rawLines(): List<String> =
        raw.split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }

    /** 去掉重复项（英文不区分大小写）后还剩几条 */
    fun uniqueLines(): List<String> = dedupe(rawLines())

    companion object {
        /** 解析结果：题目 + 被去重掉几条，界面要如实告诉用户 */
        data class Result(val questions: List<GeneratedQuestion>, val duplicates: Int)

        private fun dedupe(lines: List<String>): List<String> {
            val seen = HashSet<String>()
            val out = ArrayList<String>(lines.size)
            lines.forEach { line ->
                val key = line.lowercase().replace(Regex("\\s+"), " ")
                if (seen.add(key)) out.add(line)
            }
            return out
        }

        /**
         * 把原文变成题目。无效行（算式写错、挖空没标记）会被跳过 ——
         * 印一道算不出答案的题，比少印一道糟糕得多。
         */
        fun build(sheet: CustomSheet, rng: Random): Result {
            val lines = sheet.uniqueLines()
            val duplicates = sheet.rawLines().size - lines.size
            val parsed = lines.mapNotNull { line -> toQuestion(sheet.mode, line) }
            val ordered = if (sheet.shuffle) {
                val a = ArrayList(parsed)
                for (i in a.size - 1 downTo 1) {
                    val j = rng.nextInt(i + 1)
                    val t = a[i]
                    a[i] = a[j]
                    a[j] = t
                }
                a
            } else parsed
            val capped = if (sheet.count > 0) ordered.take(sheet.count) else ordered
            return Result(
                capped.map { (stem, answer) ->
                    GeneratedQuestion(
                        no = 0,
                        type = QuestionType.CUSTOM,
                        stem = stem,
                        answer = answer,
                        topic = sheet.title,
                    )
                },
                duplicates,
            )
        }

        /** 界面预览用：这批内容最终能出几道题 */
        fun validCount(sheet: CustomSheet): Int = build(sheet, Random(0)).questions.size

        private val CALC_RE =
            Regex("^(-?\\d+(?:\\.\\d+)?)\\s*([+\\-×xX*÷/])\\s*(-?\\d+(?:\\.\\d+)?)$")
        private val HOLE_RE = Regex("[\\[【]([^\\]】]+)[\\]】]")

        private fun toQuestion(mode: CustomMode, line: String): Pair<String, String>? = when (mode) {
            CustomMode.CALC -> calc(line)
            CustomMode.BLANK -> blank(line)
            CustomMode.WORD_EN -> wordEn(line)
            CustomMode.COPY -> line to line
            CustomMode.PLAIN -> plain(line)
        }

        /** 算式：支持 + - × ÷（x、*、/ 都认），末尾多写一个 = 也不影响 */
        private fun calc(line: String): Pair<String, String>? {
            val text = line.removeSuffix("=").removeSuffix("＝").replace("＝", "=").trim()
            val m = CALC_RE.find(text.replace(" ", "")) ?: return null
            val a = m.groupValues[1].toDoubleOrNull() ?: return null
            val b = m.groupValues[3].toDoubleOrNull() ?: return null
            val op = m.groupValues[2]
            // 纸上要印课本里的符号：用户敲 * / x 是为了手机上好打，
            // 但小学卷子上不能出现「7*8」，那是编程不是数学。
            val shown = when (op) {
                "*", "x", "X", "×" -> "×"
                "/", "÷" -> "÷"
                else -> op
            }
            val stem = "${fmt(m.groupValues[1])} $shown ${fmt(m.groupValues[3])} = （　　）"
            return when (op) {
                "+" -> stem to fmtNum(a + b)
                "-" -> stem to fmtNum(a - b)
                "×", "x", "X", "*" -> stem to fmtNum(a * b)
                else -> {
                    if (b == 0.0) return null
                    val q = a / b
                    when {
                        q == Math.floor(q) -> stem to fmtNum(q)
                        // 除不尽：**整数**除法按小学写法印「商……余数」；
                        // 一旦操作数本身带小数，余数就没意义了，老老实实给小数
                        a == Math.floor(a) && b == Math.floor(b) -> {
                            val qi = Math.floor(q).toInt()
                            val r = a.toInt() - qi * b.toInt()
                            stem to "$qi……$r"
                        }
                        else -> stem to fmtNum(Math.round(q * 100) / 100.0)
                    }
                }
            }
        }

        private fun blank(line: String): Pair<String, String>? {
            val holes = HOLE_RE.findAll(line).toList()
            if (holes.isEmpty()) return null
            val stem = HOLE_RE.replace(line, "＿＿＿")
            val answer = holes.joinToString("、") { it.groupValues[1] }
            return stem to answer
        }

        private fun wordEn(line: String): Pair<String, String>? {
            val parts = line.split(Regex("\\s*[|｜=＝:：]\\s*|\\s+"), limit = 2)
                .map { it.trim() }.filter { it.isNotEmpty() }
            val en = parts.firstOrNull() ?: return null
            // 只认字母（含 - '）开头的英文，避免把一整句中文当单词
            if (!en.first().isLetter() || en.any { it.code > 127 }) return null
            val zh = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
            val stem = if (zh != null) {
                "$zh　＿＿＿＿＿＿"
            } else {
                "＿＿＿＿＿＿（首字母 ${en.first().uppercase()}）"
            }
            return stem to en
        }

        private fun plain(line: String): Pair<String, String>? {
            val idx = line.lastIndexOfAny(charArrayOf('|', '｜'))
            return if (idx > 0 && idx < line.length - 1) {
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            } else {
                // 没写答案：题照样印，答案页标「见题面」——
                // 空着会更糟（答案页会出现一道没有答案的题，家长以为漏印）
                line to "见题面"
            }
        }

        private fun fmt(raw: String): String = raw.toDoubleOrNull()?.let { fmtNum(it) } ?: raw

        private fun fmtNum(v: Double): String =
            if (v == Math.floor(v) && !v.isInfinite()) v.toInt().toString()
            else String.format("%.2f", v).trimEnd('0').trimEnd('.')
    }
}
