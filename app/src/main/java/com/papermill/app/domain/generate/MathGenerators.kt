package com.papermill.app.domain.generate

import java.util.Random
import kotlin.math.abs

private fun r(rng: Random, from: Int, to: Int): Int =
    if (to <= from) from else from + rng.nextInt(to - from + 1)

private fun gcd(a: Int, b: Int): Int {
    var x = abs(a); var y = abs(b)
    while (y != 0) { val t = x % y; x = y; y = t }
    return if (x == 0) 1 else x
}

/**
 * 真正的「答案自校验」：把生成的算式重新解析一遍求值，和答案比对。
 * 只要生成器写错了运算符或抄错了变量，这里会直接抛异常，
 * 该道题被 BaseGenerator 的 runCatching 丢弃 —— 绝不会带着错答案印到纸上。
 */
private fun verifyArithmetic(stem: String, answer: String) {
    val m = Regex("^(-?\\d+)([+\\-×÷])(-?\\d+)=").find(stem) ?: return
    val a = m.groupValues[1].toLong()
    val b = m.groupValues[3].toLong()
    val expected = when (m.groupValues[2]) {
        "+" -> a + b
        "-" -> a - b
        "×" -> a * b
        "÷" -> if (b != 0L && a % b == 0L) a / b else return
        else -> return
    }
    require(expected == answer.toLongOrNull()) { "算式与答案不一致：$stem / $answer" }
}

// ------------------------------------------------------------------ 小学口算

object OralGenerator : BaseGenerator() {
    override val type = QuestionType.ORAL
    override val subject = Subject.MATH
    override val maxGrade = 6
    override val topic = "四则运算"

    override fun make(ctx: GenContext): Pair<String, String> {
        val p = GradeProfiles.of(ctx.grade)
        val rng = ctx.rng
        val grade = ctx.grade

        // 一年级不出现进位/退位、不出现乘除：a+b<=10，减法 a>=b
        if (grade == 1) {
            return if (rng.nextBoolean()) {
                val a = r(rng, 1, 9)
                val b = r(rng, 1, 10 - a)
                val stem = "$a+$b=（　　）"
                val ans = "${a + b}"
                verifyArithmetic(stem, ans); stem to ans
            } else {
                val a = r(rng, 2, 10)
                val b = r(rng, 1, a)
                val stem = "$a-$b=（　　）"
                val ans = "${a - b}"
                verifyArithmetic(stem, ans); stem to ans
            }
        }

        return when (r(rng, 0, 3)) {
            0 -> {
                val a = r(rng, 1, (p.addMax / 2).coerceAtLeast(2))
                val b = r(rng, 1, (p.addMax - a).coerceAtLeast(1))
                val stem = "$a+$b=（　　）"
                val ans = "${a + b}"
                verifyArithmetic(stem, ans); stem to ans
            }
            1 -> {
                val a = r(rng, 2, p.addMax)
                val b = r(rng, 1, a)
                val stem = "$a-$b=（　　）"
                val ans = "${a - b}"
                verifyArithmetic(stem, ans); stem to ans
            }
            2 -> {
                val a = r(rng, 2, p.mulMax.coerceAtLeast(3))
                val b = r(rng, 2, p.mulMax.coerceAtLeast(3))
                val stem = "$a×$b=（　　）"
                val ans = "${a * b}"
                verifyArithmetic(stem, ans); stem to ans
            }
            else -> {
                val b = r(rng, 2, p.divisorMax.coerceAtLeast(3))
                val q = r(rng, 2, 9)
                val stem = "${b * q}÷$b=（　　）"
                val ans = "$q"
                verifyArithmetic(stem, ans); stem to ans
            }
        }
    }
}

// ------------------------------------------------------------------ 竖式 / 脱式 / 分数 / 方程

object VerticalGenerator : BaseGenerator() {
    override val type = QuestionType.VERTICAL
    override val subject = Subject.MATH
    override val minGrade = 2
    override val maxGrade = 6
    override val topic = "竖式计算"

    override fun make(ctx: GenContext): Pair<String, String> {
        val p = GradeProfiles.of(ctx.grade)
        val rng = ctx.rng
        return if (rng.nextBoolean()) {
            val a = r(rng, 20, p.addMax)
            val b = r(rng, 11, (p.addMax / 2).coerceAtLeast(12))
            val stem = "用竖式计算：$a+$b＝（　　）"
            val ans = "${a + b}"
            verifyArithmetic(stem.replace("用竖式计算：", "").replace("＝", "="), ans)
            stem to ans
        } else {
            val a = r(rng, 30, p.addMax)
            val b = r(rng, 11, a - 1)
            val stem = "用竖式计算：$a-$b＝（　　）"
            val ans = "${a - b}"
            verifyArithmetic(stem.replace("用竖式计算：", "").replace("＝", "="), ans)
            stem to ans
        }
    }
}

object ExpressionGenerator : BaseGenerator() {
    override val type = QuestionType.EXPRESSION
    override val subject = Subject.MATH
    override val minGrade = 3
    override val topic = "混合运算"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        return if (rng.nextBoolean()) {
            val a = r(rng, 5, 60)
            val b = r(rng, 2, 12)
            val c = r(rng, 2, 9)
            "$a+$b×$c＝（　　）" to "${a + b * c}"
        } else {
            val a = r(rng, 3, 25)
            val b = r(rng, 2, 20)
            val c = r(rng, 2, 8)
            "($a+$b)×$c＝（　　）" to "${(a + b) * c}"
        }
    }
}

object FractionGenerator : BaseGenerator() {
    override val type = QuestionType.FRACTION
    override val subject = Subject.MATH
    override val minGrade = 4
    override val topic = "分数运算"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val d = r(rng, 4, GradeProfiles.of(ctx.grade).fractionMaxDen.coerceAtLeast(6))
        val a = r(rng, 1, d - 1)
        val b = r(rng, 1, d - a)
        val n = a + b
        val g = gcd(n, d)
        val rn = n / g
        val rd = d / g
        val ans = if (rd == 1) "$rn" else "$rn/$rd"
        val stem = if (rng.nextBoolean()) "$a/$d + $b/$d＝（　　）" else "$n/$d - $a/$d＝（　　）"
        val finalAns = if (stem.startsWith("$n/$d -")) "$b/$d".let {
            val g2 = gcd(b, d); if (d / g2 == 1) "${b / g2}" else "${b / g2}/${d / g2}"
        } else ans
        return stem to finalAns
    }
}

object EquationGenerator : BaseGenerator() {
    override val type = QuestionType.EQUATION
    override val subject = Subject.MATH
    override val minGrade = 5
    override val topic = "简易方程"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        return when (r(rng, 0, 2)) {
            0 -> {
                val x = r(rng, 2, 30)
                val a = r(rng, 2, 30)
                "解方程：x+$a=${x + a}，x＝（　　）" to "$x"
            }
            1 -> {
                val x = r(rng, 2, 20)
                val a = r(rng, 2, 9)
                "解方程：${a}x=${a * x}，x＝（　　）" to "$x"
            }
            else -> {
                val x = r(rng, 2, 20)
                val a = r(rng, 2, 9)
                val c = r(rng, 1, 20)
                "解方程：${a}x+$c=${a * x + c}，x＝（　　）" to "$x"
            }
        }
    }
}

// ------------------------------------------------------------------ 应用题

object WordProblemGenerator : BaseGenerator() {
    override val type = QuestionType.WORD_PROBLEM
    override val subject = Subject.MATH
    override val topic = "应用题"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val a = r(rng, 6, 48)
        val b = r(rng, 2, minOf(a, 20))
        val c = r(rng, 2, 12)
        return when (r(rng, 0, 5)) {
            0 -> "小明有 $a 个苹果，又买了 $b 个，一共有多少个？" to "${a + b} 个"
            1 -> "书架上有 ${a + b} 本书，借走 $b 本，还剩多少本？" to "$a 本"
            2 -> "每盒有 $a 支铅笔，$c 盒一共有多少支？" to "${a * c} 支"
            3 -> "把 ${a * c} 颗糖平均分给 $c 个小朋友，每人分到几颗？" to "$a 颗"
            4 -> "一根绳子长 $a 米，用去 $b 米，还剩多少米？" to "${a - b} 米"
            else -> "小红每天读 $b 页书，读了 $c 天，一共读了多少页？" to "${b * c} 页"
        }
    }
}

// ------------------------------------------------------------------ 初中数学

object RationalGenerator : BaseGenerator() {
    override val type = QuestionType.RATIONAL
    override val subject = Subject.MATH
    override val minGrade = 7
    override val topic = "有理数运算"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val a = r(rng, 2, 30)
        val b = r(rng, 2, 30)
        return when (r(rng, 0, 3)) {
            0 -> "(-$a)+$b＝（　　）" to "${-a + b}"
            1 -> "(-$a)-$b＝（　　）" to "${-a - b}"
            2 -> "(-$a)×$b＝（　　）" to "${-a * b}"
            else -> {
                val q = r(rng, 2, 9)
                "(-${a * q})÷$a＝（　　）" to "${-q}"
            }
        }
    }
}

object AlgebraGenerator : BaseGenerator() {
    override val type = QuestionType.ALGEBRA
    override val subject = Subject.MATH
    override val minGrade = 7
    override val topic = "整式与方程"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        return if (rng.nextBoolean()) {
            val a = r(rng, 2, 9)
            val b = r(rng, 2, 9)
            val c = r(rng, 1, a + b - 1)
            "化简：${a}x+${b}x-${c}x＝（　　）" to "${a + b - c}x"
        } else {
            val x = r(rng, 2, 15)
            val a = r(rng, 2, 9)
            val c = r(rng, 1, 20)
            "解方程：${a}x+$c=${a * x + c}，x＝（　　）" to "$x"
        }
    }
}

object RadicalGenerator : BaseGenerator() {
    override val type = QuestionType.RADICAL
    override val subject = Subject.MATH
    override val minGrade = 8
    override val topic = "二次根式"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val a = r(rng, 2, 12)
        val b = r(rng, 2, 9)
        val a2 = a * a
        val b2 = b * b
        return when (r(rng, 0, 2)) {
            0 -> "√$a2 × √$b2＝（　　）" to "${a * b}"
            1 -> "√$a2 + √$b2＝（　　）" to "${a + b}"
            else -> {
                val k = r(rng, 2, 6)
                "√${a2 * k * k} ÷ √$a2＝（　　）" to "$k"
            }
        }
    }
}
