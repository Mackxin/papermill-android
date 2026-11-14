package com.papermill.app.domain.generate

import java.util.Random

/**
 * v0.8.0 口算补两个题型（借鉴 oralcalc 那类「口算生成器」的参数思路，实现自己写）。
 *
 * 已有的口算生成器只出「求得数」，但家长真正想练的往往是另外两件事：
 *  1. **求未知项**：7 + （　）= 12 —— 考的是逆运算，比顺算难一档；
 *  2. **有余数除法**：23 ÷ 5 = （　）……（　）—— 二年级下学期开始的重点。
 *
 * 进位 / 借位控制在 [GradeProfiles] 里本来就有（allowCarry / allowBorrow），不重复造。
 */

private fun rr(rng: Random, from: Int, to: Int): Int =
    if (to <= from) from else from + rng.nextInt(to - from + 1)

/** 求未知项：把等式里的一项挖空，答案就是那一项 */
object OralMissingGenerator : BaseGenerator() {
    override val type = QuestionType.ORAL_MISSING
    override val subject = Subject.MATH
    override val maxGrade = 6
    override val topic = "四则运算"

    override fun make(ctx: GenContext): Pair<String, String> {
        val p = GradeProfiles.of(ctx.grade)
        val rng = ctx.rng

        // 逆运算考的是心算，数字必须跟着年级走小步：
        // 直接拿 addMax/2，四年级会蹦出「4013-（　）=2764」这种四位数，
        // 那是笔算题不是口算题，孩子只能干瞪眼。
        val max = when (ctx.grade) {
            1 -> 10
            2 -> 20
            3 -> 50
            4 -> 100
            else -> 200
        }

        return when (rng.nextInt(if (p.mulMax > 0) 4 else 3)) {
            0 -> {
                // a + （ ）= c  → 答案 b
                val a = rr(rng, 1, max)
                val b = rr(rng, 1, max)
                "$a+（　）=${a + b}" to "$b"
            }
            1 -> {
                // （ ）+ b = c  → 答案 a
                val a = rr(rng, 1, max)
                val b = rr(rng, 1, max)
                "（　）+$b=${a + b}" to "$a"
            }
            2 -> {
                // c - （ ）= a  → 答案 b
                val a = rr(rng, 1, max)
                val b = rr(rng, 1, max)
                "${a + b}-（　）=$a" to "$b"
            }
            else -> {
                // a × （ ）= c  → 答案 b（三年级起 p.mulMax > 0）
                val a = rr(rng, 2, p.mulMax.coerceAtLeast(2))
                val b = rr(rng, 2, p.mulMax.coerceAtLeast(2))
                "$a×（　）=${a * b}" to "$b"
            }
        }
    }
}

/** 有余数除法：被除数 = 除数 × 商 + 余数，余数必须小于除数 */
object OralRemainderGenerator : BaseGenerator() {
    override val type = QuestionType.ORAL_REMAINDER
    override val subject = Subject.MATH
    override val minGrade = 3
    override val maxGrade = 6
    override val topic = "除法与余数"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val divisor = rr(rng, 2, 9)
        val quotient = rr(rng, 2, 9)
        // 余数 1..divisor-1：特意不取 0，整除题已经有「表内除法」，这里只练带余数
        val remainder = rr(rng, 1, divisor - 1)
        val dividend = divisor * quotient + remainder
        val stem = "$dividend÷$divisor=（　）……（　）"
        return stem to "$quotient……$remainder"
    }
}
