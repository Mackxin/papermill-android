package com.papermill.app.domain.generate

import java.util.Random

/**
 * v0.4.0 新增题型：把外部练习资源站的「题型规则」做成自编生成器。
 *
 * ============================================================
 * 版权边界（重要）
 * ============================================================
 * 参考的是「有哪些题型」这件事本身（填括号、凑十、连加连减、整十数、
 * 两位数加减、元角分换算、比大小、找规律、数数、字母顺序），
 * 这些属于**题型模式 / 数学事实**，不构成受保护的表达。
 *
 * 这里没有抓取、复制、翻译或存储任何第三方网站的原题、PDF、图片或版面，
 * 每一道题都由下面的算法现场生成，答案直接由生成参数推出（不是事后猜的），
 * 因此可以离线无限出题、按年级 / 科目 / 题型任意组合。
 *
 * ============================================================
 * 为什么单独成一个文件
 * ============================================================
 * 这些题都是一年级起步的短题，一行就能排 3 栏，正好适合「把一页纸排满」。
 * 和 MathGenerators.kt 里那些需要书写过程的竖式 / 应用题分开，
 * 便于以后按学段裁剪题库。
 */

private fun r(rng: Random, from: Int, to: Int): Int =
    if (to <= from) from else from + rng.nextInt(to - from + 1)

/** 按年级收紧数域：一年级 10 以内、二年级 20 以内、三年级起 100 以内 */
private fun bound(grade: Int): Int = when {
    grade <= 1 -> 10
    grade == 2 -> 20
    grade == 3 -> 100
    else -> 1000
}

// ------------------------------------------------------------------ 填括号

/** 4－？＝1、？＋8＝13 —— 缺项填空，练习逆运算 */
object FillBlankGenerator : BaseGenerator() {
    override val type = QuestionType.FILL_BLANK
    override val subject = Subject.MATH
    override val maxGrade = 4
    override val topic = "填括号"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val max = bound(ctx.grade)
        return when (r(rng, 0, 3)) {
            0 -> {
                val c = r(rng, 3, max)
                val a = r(rng, 1, c - 1)
                "$a＋？＝$c（　　）" to "${c - a}"
            }
            1 -> {
                val a = r(rng, 3, max)
                val c = r(rng, 0, a - 1)
                "$a－？＝$c（　　）" to "${a - c}"
            }
            2 -> {
                val c = r(rng, 3, max)
                val b = r(rng, 1, c - 1)
                "？＋$b＝$c（　　）" to "${c - b}"
            }
            else -> {
                val b = r(rng, 1, (max / 2).coerceAtLeast(2))
                val c = r(rng, 0, (max / 2).coerceAtLeast(2))
                "？－$b＝$c（　　）" to "${b + c}"
            }
        }
    }
}

/** ？＋8＝10 —— 凑十 / 凑二十，进位加法的地基 */
object MakeTenGenerator : BaseGenerator() {
    override val type = QuestionType.MAKE_TEN
    override val subject = Subject.MATH
    override val maxGrade = 2
    override val topic = "凑十法"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val base = if (ctx.grade <= 1 || rng.nextBoolean()) 10 else 20
        val b = r(rng, 1, base - 1)
        return if (rng.nextBoolean()) {
            "？＋$b＝$base（　　）" to "${base - b}"
        } else {
            "$b＋（　　）＝$base" to "${base - b}"
        }
    }
}

// ------------------------------------------------------------------ 连加连减 / 加减混合

/** 2＋1＋5＝、15－4－7＝ —— 三个数连算，中间结果始终不为负 */
object ChainCalcGenerator : BaseGenerator() {
    override val type = QuestionType.CHAIN_CALC
    override val subject = Subject.MATH
    override val maxGrade = 4
    override val topic = "连加连减"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val max = bound(ctx.grade)
        return if (rng.nextBoolean()) {
            val a = r(rng, 1, (max / 3).coerceAtLeast(3))
            val b = r(rng, 1, (max / 3).coerceAtLeast(3))
            val c = r(rng, 1, (max / 3).coerceAtLeast(3))
            "$a＋$b＋$c＝（　　）" to "${a + b + c}"
        } else {
            val a = r(rng, 6, max)
            val b = r(rng, 1, (a / 2).coerceAtLeast(1))
            val c = r(rng, 1, (a - b).coerceAtLeast(1))
            "$a－$b－$c＝（　　）" to "${a - b - c}"
        }
    }
}

/** 19－3＋0＝、5－0＋1＝ —— 加减混合，两个方向都练到 */
object MixedCalcGenerator : BaseGenerator() {
    override val type = QuestionType.MIXED_CALC
    override val subject = Subject.MATH
    override val maxGrade = 4
    override val topic = "加减混合"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val max = bound(ctx.grade)
        return if (rng.nextBoolean()) {
            val a = r(rng, 5, max)
            val b = r(rng, 1, a)
            val c = r(rng, 1, (max / 2).coerceAtLeast(2))
            "$a－$b＋$c＝（　　）" to "${a - b + c}"
        } else {
            val a = r(rng, 1, (max / 2).coerceAtLeast(2))
            val b = r(rng, 1, (max / 2).coerceAtLeast(2))
            val c = r(rng, 1, a + b)
            "$a＋$b－$c＝（　　）" to "${a + b - c}"
        }
    }
}

// ------------------------------------------------------------------ 整十数 / 两位数

/** 50＋3、80－10 —— 整十数加一位数、整十数加减整十数 */
object TensCalcGenerator : BaseGenerator() {
    override val type = QuestionType.TENS_CALC
    override val subject = Subject.MATH
    override val maxGrade = 2
    override val topic = "整十数运算"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val t = r(rng, 1, 8) * 10
        val one = r(rng, 1, 9)
        val ten = r(rng, 1, 9) * 10
        return when (r(rng, 0, 3)) {
            0 -> "$t＋$one＝（　　）" to "${t + one}"
            1 -> "$t－$one＝（　　）" to "${t - one}"
            2 -> "$t＋$ten＝（　　）" to "${t + ten}"
            else -> {
                val big = (t + ten).coerceAtMost(90)
                "$big－$ten＝（　　）" to "${big - ten}"
            }
        }
    }
}

/** 37＋7、37－20 —— 两位数加一位数 / 整十数 */
object TwoDigitGenerator : BaseGenerator() {
    override val type = QuestionType.TWO_DIGIT
    override val subject = Subject.MATH
    override val maxGrade = 3
    override val topic = "两位数加减"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val a = r(rng, 21, 89)
        val b = if (rng.nextBoolean()) r(rng, 2, 9) else r(rng, 1, 8) * 10
        return if (rng.nextBoolean()) {
            "$a＋$b＝（　　）" to "${a + b}"
        } else {
            "$a－$b＝（　　）" to "${a - b}"
        }
    }
}

// ------------------------------------------------------------------ 单位换算

/** 6角9分＝？分、3米5厘米＝？厘米、2时＝？分 —— 事实性换算，自行编排 */
object UnitConvertGenerator : BaseGenerator() {
    override val type = QuestionType.UNIT_CONVERT
    override val subject = Subject.MATH
    override val maxGrade = 5
    override val topic = "单位换算"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        return when (r(rng, 0, 4)) {
            0 -> {
                val y = r(rng, 1, 9); val j = r(rng, 1, 9)
                "$y 元 $j 角＝（　　）角" to "${y * 10 + j}"
            }
            1 -> {
                val j = r(rng, 1, 9); val f = r(rng, 1, 9)
                "$j 角 $f 分＝（　　）分" to "${j * 10 + f}"
            }
            2 -> {
                val m = r(rng, 1, 9); val cm = r(rng, 1, 99)
                "$m 米 $cm 厘米＝（　　）厘米" to "${m * 100 + cm}"
            }
            3 -> {
                val h = r(rng, 1, 6)
                "$h 时＝（　　）分" to "${h * 60}"
            }
            else -> {
                val kg = r(rng, 1, 9)
                "$kg 千克＝（　　）克" to "${kg * 1000}"
            }
        }
    }
}

// ------------------------------------------------------------------ 比大小 / 找规律

/** 7＋2 ○ 10（填＞、＜或＝）—— 先算再比 */
object CompareGenerator : BaseGenerator() {
    override val type = QuestionType.COMPARE
    override val subject = Subject.MATH
    override val maxGrade = 4
    override val topic = "比大小"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val max = bound(ctx.grade).coerceAtMost(50)
        val a = r(rng, 1, max); val b = r(rng, 1, max)
        val c = r(rng, 1, max); val d = r(rng, 1, max)
        val left = a + b
        val right = c + d
        val sym = when {
            left > right -> "＞"
            left < right -> "＜"
            else -> "＝"
        }
        val text = if (rng.nextBoolean()) {
            "$a＋$b ○ $c＋$d（填＞＜＝）"
        } else {
            "$a－${a.coerceAtMost(b)} ○ $c（填＞＜＝）"
        }
        // 第二种写法左右两边要按实际出现的表达式重新比，避免答案对不上
        val answer = if (text.startsWith("$a－")) {
            val l = a - a.coerceAtMost(b)
            when { l > c -> "＞"; l < c -> "＜"; else -> "＝" }
        } else sym
        return text to answer
    }
}

/** 2，4，6，8，（　），12 —— 等差数列找规律 */
object NumberPatternGenerator : BaseGenerator() {
    override val type = QuestionType.NUMBER_PATTERN
    override val subject = Subject.MATH
    override val maxGrade = 6
    override val topic = "找规律"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val start = r(rng, 1, 20)
        val step = r(rng, 2, if (ctx.grade <= 2) 5 else 12)
        val seq = (0..5).map { start + it * step }
        val hidden = r(rng, 2, 4)
        val parts = seq.mapIndexed { i, v -> if (i == hidden) "（　　）" else "$v" }
        return "按规律填数：${parts.joinToString("，")}" to "${seq[hidden]}"
    }
}

/** ○○○ 一共有几个、从 3 数到 8 数了几个数 —— 低年级数数 */
object CountingGenerator : BaseGenerator() {
    override val type = QuestionType.COUNTING
    override val subject = Subject.MATH
    override val maxGrade = 2
    override val topic = "数数与认数"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        return when (r(rng, 0, 2)) {
            0 -> {
                val n = r(rng, 3, 9)
                "${"○".repeat(n)} 一共有几个○？（　　）" to "$n"
            }
            1 -> {
                val a = r(rng, 1, 6)
                val b = a + r(rng, 2, 5)
                "从 $a 数到 $b，一共数了几个数？（　　）" to "${b - a + 1}"
            }
            else -> {
                val n = r(rng, 2, 19)
                "$n 的后面一个数是（　　）" to "${n + 1}"
            }
        }
    }
}

// ------------------------------------------------------------------ 英语字母

/** D 的前一个字母、g 的大写形式 —— 低年级字母顺序与大小写 */
object LetterOrderGenerator : BaseGenerator() {
    override val type = QuestionType.LETTER_ORDER
    override val subject = Subject.ENGLISH
    override val minGrade = 3
    override val maxGrade = 6
    override val topic = "字母顺序"

    private val LETTERS = ('A'..'Z').toList()

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        return when (r(rng, 0, 3)) {
            0 -> {
                val i = r(rng, 1, 25)
                "字母 ${LETTERS[i]} 的前一个字母是（　　）" to "${LETTERS[i - 1]}"
            }
            1 -> {
                val i = r(rng, 0, 24)
                "字母 ${LETTERS[i]} 的后一个字母是（　　）" to "${LETTERS[i + 1]}"
            }
            2 -> {
                val i = r(rng, 0, 25)
                "写出 ${LETTERS[i].lowercaseChar()} 的大写形式（　　）" to "${LETTERS[i]}"
            }
            else -> {
                val i = r(rng, 0, 23)
                "按字母顺序，${LETTERS[i]} 后面第 2 个字母是（　　）" to "${LETTERS[i + 2]}"
            }
        }
    }
}
