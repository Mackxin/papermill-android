package com.papermill.app.domain.generate

import java.util.Locale
import java.util.Random
import kotlin.math.abs

/**
 * 小学数学知识点专项出题器 —— 逐条对照 shijuan.on2on.cn 的 15 个知识点页面移植。
 *
 * 那个站每个知识点一页，页面里的 generate() 就是它全部的出题规则；这里把
 * **数字范围、进位/退位条件、去重方式**原样搬过来，只把「画线」换成我们自己的
 * 「（　　）」写法，并把答案一起算出来（它那站是生成完再显示答案，用的是同一套数）。
 *
 * 一条硬约定：每个函数只造一道题，造不出来返回 null 让上层重试 ——
 * 「20以内退位减法」这种带条件的范围，硬塞不满足条件的数就变成错题。
 */
object MathPointGenerators {

    private fun r(rng: Random, from: Int, to: Int): Int =
        if (to <= from) from else from + rng.nextInt(to - from + 1)

    private fun gcd(a: Int, b: Int): Int {
        var x = abs(a); var y = abs(b)
        while (y != 0) { val t = x % y; x = y; y = t }
        return if (x == 0) 1 else x
    }

    /** 同分母分数约分：分母约成 1 时直接印整数（3/3 印成 1，不印 1/1） */
    private fun simplify(num: Int, den: Int): String {
        val g = gcd(num, den)
        val n = num / g
        val d = den / g
        return if (d == 1) "$n" else "$n/$d"
    }

    /** 造一道题：返回「题干 to 答案」，造不出来返回 null */
    fun make(point: MathPoint, rng: Random): Pair<String, String>? = when (point) {
        MathPoint.G1_COMPARE -> compare10(rng)
        MathPoint.G1_ADD10 -> add10(rng)
        MathPoint.G1_SUB10 -> sub10(rng)
        MathPoint.G1_SUB20 -> sub20(rng)
        MathPoint.G1_ADDSUB100 -> addSub100(rng)
        MathPoint.G2_ADDSUB100ADV -> addSub100Adv(rng)
        MathPoint.G2_MUL -> mulTable(rng)
        MathPoint.G2_DIV -> divTable(rng)
        MathPoint.G2_DIVREMAIN -> divRemain(rng)
        MathPoint.G2_MULADDSUB -> mulAddSub(rng)
        MathPoint.G3_ADDSUBWAN -> addSubWan(rng)
        MathPoint.G3_MULMORE -> mulMore(rng)
        MathPoint.G3_PERIMETER -> perimeter(rng)
        MathPoint.G3_FRACTIONADDSUB -> fractionAddSub(rng)
        MathPoint.G3_MULTWO -> mulTwo(rng)
        MathPoint.G4_MULTHREE -> mulThree(rng)
        MathPoint.G4_DIVTWO -> divTwo(rng)
        MathPoint.G4_DECIMALADDSUB -> decimalAddSub(rng)
        MathPoint.G4_MIXEDCALC -> mixedCalc(rng)
        MathPoint.G4_BIGCOMPARE -> bigCompare(rng)
        MathPoint.G5_DECIMALMUL -> decimalMul(rng)
        MathPoint.G5_DECIMALDIV -> decimalDiv(rng)
        MathPoint.G5_GCDLCM -> gcdLcm(rng)
        MathPoint.G5_REDUCE -> reduceFraction(rng)
        MathPoint.G5_FRACTIONADDSUBADV -> fractionAddSubAdv(rng)
        MathPoint.G5_POLYGONAREA -> polygonArea(rng)
        MathPoint.G5_CUBOIDVOLUME -> cuboidVolume(rng)
        MathPoint.G5_EQUATION -> simpleEquation(rng)
        MathPoint.G6_FRACTIONMUL -> fractionMul(rng)
        MathPoint.G6_FRACTIONDIV -> fractionDiv(rng)
        MathPoint.G6_PERCENT -> percentOf(rng)
        MathPoint.G6_CIRCLE -> circle(rng)
    }

    /**
     * 一个知识点的整批题。
     *
     * 去重按题干：同一个算式在一页上出现两次，家长第一反应是「这软件在重复」。
     * 重试上限跟着题量走（每个知识点能造出的不同题有上限，比如表内乘法 64 道），
     * 取不到就如实少给，绝不死循环。
     */
    fun generate(point: MathPoint, count: Int, rng: Random): List<GeneratedQuestion> {
        val want = count.coerceIn(1, 50)
        val seen = HashSet<String>()
        val out = ArrayList<GeneratedQuestion>(want)
        val maxTries = want * 80 + 80
        var tries = 0
        while (out.size < want && tries < maxTries) {
            tries++
            val pair = runCatching { make(point, rng) }.getOrNull() ?: continue
            val stem = pair.first.trim()
            val answer = pair.second.trim()
            if (stem.isEmpty() || answer.isEmpty()) continue
            if (!seen.add(stem)) continue
            out.add(
                GeneratedQuestion(
                    no = 0,
                    type = point.render,
                    stem = stem,
                    answer = answer,
                    topic = point.label,
                )
            )
        }
        return out
    }

    // ------------------------------------------------------------------ 一年级

    /** 10以内数的比较：两个 0~10 的数填 ＞ ＜ ＝ */
    private fun compare10(rng: Random): Pair<String, String> {
        val a = r(rng, 0, 10)
        val b = r(rng, 0, 10)
        val sym = when {
            a > b -> "＞"
            a < b -> "＜"
            else -> "＝"
        }
        return "$a ○ $b（填＞＜＝）" to sym
    }

    /** 10以内加法：和不超过 10 */
    private fun add10(rng: Random): Pair<String, String> {
        val a = r(rng, 0, 10)
        val b = r(rng, 0, 10 - a)
        return "$a+$b=（　　）" to "${a + b}"
    }

    /** 10以内减法：被减数不超过 10，不会减出负数 */
    private fun sub10(rng: Random): Pair<String, String> {
        val a = r(rng, 0, 10)
        val b = r(rng, 0, a)
        return "$a-$b=（　　）" to "${a - b}"
    }

    /** 20以内退位减法：只出个位不够减、必须退位的那种 */
    private fun sub20(rng: Random): Pair<String, String>? {
        val a = r(rng, 11, 20)
        val b = r(rng, 2, 9)
        // 个位够减就不是退位题了 —— 这条判断是「20以内退位减法」和「20以内减法」的区别
        if (a % 10 >= b) return null
        return "$a-$b=（　　）" to "${a - b}"
    }

    /** 100以内不进退位加减法：个位、十位都不进位也不退位 */
    private fun addSub100(rng: Random): Pair<String, String>? {
        if (rng.nextBoolean()) {
            val aT = r(rng, 1, 9)
            val aU = r(rng, 0, 9)
            val bT = r(rng, 1, 9)
            val bU = r(rng, 0, 9)
            if (aU + bU >= 10) return null
            if (aT + bT >= 10) return null
            val a = aT * 10 + aU
            val b = bT * 10 + bU
            return "$a+$b=（　　）" to "${a + b}"
        }
        val aT = r(rng, 2, 9)
        val aU = r(rng, 1, 9)
        val bT = r(rng, 1, aT)
        val bU = r(rng, 0, aU)
        val a = aT * 10 + aU
        val b = bT * 10 + bU
        if (b == 0) return null
        return "$a-$b=（　　）" to "${a - b}"
    }

    // ------------------------------------------------------------------ 二年级

    /** 100以内进退位加减法：加要进位、减要退位 */
    private fun addSub100Adv(rng: Random): Pair<String, String>? {
        if (rng.nextBoolean()) {
            val a = r(rng, 11, 89)
            val b = r(rng, 2, 99 - a)
            if (a % 10 + b % 10 < 10) return null
            return "$a+$b=（　　）" to "${a + b}"
        }
        val a = r(rng, 12, 99)
        val b = r(rng, 2, a)
        if (a % 10 >= b % 10) return null
        return "$a-$b=（　　）" to "${a - b}"
    }

    /** 表内乘法：1~9 的口诀 */
    private fun mulTable(rng: Random): Pair<String, String> {
        val a = r(rng, 2, 9)
        val b = r(rng, 2, 9)
        return "$a×$b=（　　）" to "${a * b}"
    }

    /** 表内除法：用口诀求商，一定除得尽 */
    private fun divTable(rng: Random): Pair<String, String> {
        val b = r(rng, 2, 9)
        val q = r(rng, 2, 9)
        return "${b * q}÷$b=（　　）" to "$q"
    }

    /** 有余数的除法：余数一定小于除数，商与余数分两格写 */
    private fun divRemain(rng: Random): Pair<String, String>? {
        val b = r(rng, 2, 9)
        val q = r(rng, 1, 9)
        val rem = r(rng, 1, b - 1)
        val a = b * q + rem
        if (a > 99) return null
        return "$a÷$b=（　）……（　）" to "$q……$rem"
    }

    /** 乘加 / 乘减：先乘后加减，结果不出 100 */
    private fun mulAddSub(rng: Random): Pair<String, String>? {
        val a = r(rng, 2, 9)
        val b = r(rng, 2, 9)
        val p = a * b
        if (rng.nextBoolean()) {
            val cMax = minOf(20, 100 - p)
            if (cMax < 1) return null
            val c = r(rng, 1, cMax)
            return "$a×$b+$c=（　　）" to "${p + c}"
        }
        val c = r(rng, 1, minOf(20, p - 1))
        return "$a×$b-$c=（　　）" to "${p - c}"
    }

    // ------------------------------------------------------------------ 三年级

    /** 万以内加减法（笔算）：留竖式空，加的和、减的差都不超过 9999 */
    private fun addSubWan(rng: Random): Pair<String, String> {
        if (rng.nextBoolean()) {
            val a = r(rng, 100, 9899)
            val b = r(rng, 100, 9999 - a)
            return "$a+$b＝（　　）" to "${a + b}"
        }
        val a = r(rng, 200, 9999)
        val b = r(rng, 100, a - 1)
        return "$a-$b＝（　　）" to "${a - b}"
    }

    /** 多位数乘一位数：两位数、三位数各占一半 */
    private fun mulMore(rng: Random): Pair<String, String>? {
        val a = if (rng.nextBoolean()) r(rng, 10, 99) else r(rng, 100, 999)
        val b = r(rng, 2, 9)
        val p = a * b
        if (p > 10000) return null
        return "$a×$b＝（　　）" to "$p"
    }

    /** 长方形正方形周长：用文字给条件，孩子自己列式 */
    private fun perimeter(rng: Random): Pair<String, String>? {
        if (rng.nextBoolean()) {
            val len = r(rng, 6, 40)
            val wid = r(rng, 4, len)
            val ans = 2 * (len + wid)
            if (ans > 200) return null
            return "一个长方形，长 $len 厘米，宽 $wid 厘米，周长是多少厘米？" to "$ans 厘米"
        }
        val side = r(rng, 4, 50)
        val ans = 4 * side
        if (ans > 200) return null
        return "一个正方形，边长 $side 厘米，周长是多少厘米？" to "$ans 厘米"
    }

    /** 分数简单加减法：同分母，结果约成最简 */
    private fun fractionAddSub(rng: Random): Pair<String, String> {
        val den = r(rng, 3, 10)
        if (rng.nextBoolean()) {
            val a = r(rng, 1, den - 2)
            val c = r(rng, 1, den - a - 1)
            return "$a/$den + $c/$den＝（　　）" to simplify(a + c, den)
        }
        val a = r(rng, 2, den - 1)
        val c = r(rng, 1, a - 1)
        return "$a/$den - $c/$den＝（　　）" to simplify(a - c, den)
    }

    /** 两位数乘两位数：积不超过 9999 */
    private fun mulTwo(rng: Random): Pair<String, String>? {
        val a = r(rng, 11, 99)
        val b = r(rng, 11, 99)
        val p = a * b
        if (p > 9999) return null
        return "$a×$b＝（　　）" to "$p"
    }

    // ------------------------------------------------------------------ 四年级

    /** 三位数乘两位数：积控制在五位数以内，够一个竖式的书写量 */
    private fun mulThree(rng: Random): Pair<String, String> {
        val a = r(rng, 102, 998)
        val b = r(rng, 11, 98)
        return "$a×$b＝（　　）" to "${a * b}"
    }

    /** 除数是两位数的除法：一定除得尽，商是两位数 */
    private fun divTwo(rng: Random): Pair<String, String>? {
        val b = r(rng, 11, 89)
        val qMax = minOf(99, 9999 / b)
        if (qMax < 10) return null
        val q = r(rng, 10, qMax)
        return "${b * q}÷$b＝（　　）" to "$q"
    }

    /**
     * 十分位上的整数 → 「3.4」这样的字符串。
     *
     * 全部换算成整数再算，是为了绕开 0.1+0.2=0.30000000000000004 这类浮点尾巴 ——
     * 网页端出于同一个理由也是这么干的（见 web/js/gen/points.js）。
     */
    private fun oneDecimal(tenths: Int): String = String.format(Locale.US, "%.1f", tenths / 10.0)

    /** 一位小数加减法：结果不出 100，小数点对齐 */
    private fun decimalAddSub(rng: Random): Pair<String, String> {
        return if (rng.nextBoolean()) {
            val a = r(rng, 1, 900)
            val b = r(rng, 1, 1000 - a)
            "${oneDecimal(a)}+${oneDecimal(b)}＝（　　）" to oneDecimal(a + b)
        } else {
            val a = r(rng, 11, 999)
            val b = r(rng, 1, a - 1)
            "${oneDecimal(a)}-${oneDecimal(b)}＝（　　）" to oneDecimal(a - b)
        }
    }

    /** 整数四则混合：先乘除后加减、带括号的也要先算括号，四种骨架轮着出 */
    private fun mixedCalc(rng: Random): Pair<String, String>? = when (r(rng, 0, 3)) {
        0 -> {
            val a = r(rng, 2, 9)
            val b = r(rng, 11, 60)
            val c = r(rng, 11, 40)
            "$a×($b+$c)＝（　　）" to "${a * (b + c)}"
        }
        1 -> {
            val a = r(rng, 2, 9)
            val b = r(rng, 11, 60)
            val c = r(rng, 11, a * b - 1)
            if (c >= a * b) null else "$a×$b-$c＝（　　）" to "${a * b - c}"
        }
        2 -> {
            val d = r(rng, 12, 48)
            val q = r(rng, 2, 9)
            val add = r(rng, 11, 90)
            "${d * q}÷$d+$add＝（　　）" to "${q + add}"
        }
        else -> {
            val b = r(rng, 2, 9)
            val c = r(rng, 11, 40)
            val a = r(rng, b * c + 10, 999)
            "$a-$b×$c＝（　　）" to "${a - b * c}"
        }
    }

    /** 万以上数的比较：五位数与五位数、六位数与六位数混着来 */
    private fun bigCompare(rng: Random): Pair<String, String> {
        val five = rng.nextBoolean()
        val lo = if (five) 10000 else 100000
        val hi = if (five) 99999 else 999999
        val a = r(rng, lo, hi)
        val b = r(rng, lo, hi)
        val sym = when {
            a > b -> "＞"
            a < b -> "＜"
            else -> "＝"
        }
        return "$a ○ $b（填＞＜＝）" to sym
    }

    // ------------------------------------------------------------------ 五年级

    /** 小数乘整数：一位小数乘一位或两位数，积仍是一位小数 */
    private fun decimalMul(rng: Random): Pair<String, String> {
        val a = r(rng, 2, 99)
        val b = r(rng, 2, 99)
        return "${oneDecimal(a)}×$b＝（　　）" to oneDecimal(a * b)
    }

    /** 小数除以整数：被除数一位小数，除得尽 */
    private fun decimalDiv(rng: Random): Pair<String, String> {
        val b = r(rng, 2, 9)
        val q = r(rng, 11, 99)
        return "${oneDecimal(b * q)}÷$b＝（　　）" to oneDecimal(q)
    }

    /** 最大公因数 / 最小公倍数：只出真能约的（公因数 ≥ 2），互质对没练头 */
    private fun gcdLcm(rng: Random): Pair<String, String>? {
        val a = r(rng, 4, 48)
        val b = r(rng, 4, 48)
        val g = gcd(a, b)
        if (g < 2) return null
        return if (rng.nextBoolean()) {
            "$a 和 $b 的最大公因数是（　　）" to "$g"
        } else {
            "$a 和 $b 的最小公倍数是（　　）" to "${(a / g) * b}"
        }
    }

    /** 约分：分子分母都带公因数才出，答案按最简式印 */
    private fun reduceFraction(rng: Random): Pair<String, String>? {
        val den = r(rng, 3, 9) * r(rng, 2, 8)
        val num = r(rng, 2, den - 1)
        if (num >= den) return null
        if (gcd(num, den) < 2) return null
        return "$num/$den＝（　　）（约成最简分数）" to simplify(num, den)
    }

    /**
     * 异分母分数加减。
     *
     * 分母固定成倍数关系（3 与 6、4 与 8 这种），通分只要变一个分母 ——
     * 随便挑两个互质的 7 和 9，通分出来分母 63，五年级的孩子心算不了。
     */
    private fun fractionAddSubAdv(rng: Random): Pair<String, String>? {
        val base = r(rng, 2, 6)
        val den2 = base * r(rng, 2, 3)
        val n1 = r(rng, 1, base - 1)
        val n2 = r(rng, 1, den2 - 1)
        return if (rng.nextBoolean()) {
            val num = n1 * (den2 / base) + n2
            if (num > den2 * 2) null
            else "$n1/$base + $n2/$den2＝（　　）" to simplify(num, den2)
        } else {
            val num = n1 * (den2 / base) - n2
            if (num <= 0) null
            else "$n1/$base - $n2/$den2＝（　　）" to simplify(num, den2)
        }
    }

    /** 平行四边形 / 三角形 / 梯形面积：给文字条件，孩子自己列式 */
    private fun polygonArea(rng: Random): Pair<String, String>? = when (r(rng, 0, 2)) {
        0 -> {
            val base = r(rng, 4, 20)
            val h = r(rng, 3, 15)
            "一个平行四边形，底 $base 厘米，高 $h 厘米，面积是多少平方厘米？" to "${base * h} 平方厘米"
        }
        1 -> {
            val base = r(rng, 4, 20)
            val h = r(rng, 2, 16)
            when {
                // 三角形面积要整数，别出 37.5
                (base * h) % 2 != 0 -> null
                else -> "一个三角形，底 $base 厘米，高 $h 厘米，面积是多少平方厘米？" to "${(base * h) / 2} 平方厘米"
            }
        }
        else -> {
            val up = r(rng, 3, 12)
            val down = up + r(rng, 2, 10)
            val h = r(rng, 2, 12)
            val twice = (up + down) * h
            when {
                twice % 2 != 0 -> null
                else -> "一个梯形，上底 $up 厘米，下底 $down 厘米，高 $h 厘米，面积是多少平方厘米？" to
                    "${twice / 2} 平方厘米"
            }
        }
    }

    /** 长方体 / 正方体体积：正方体只出棱长 3~12（再大就成一串四位数了） */
    private fun cuboidVolume(rng: Random): Pair<String, String> {
        if (rng.nextBoolean()) {
            val a = r(rng, 3, 12)
            return "一个正方体，棱长 $a 厘米，体积是多少立方厘米？" to "${a * a * a} 立方厘米"
        }
        val l = r(rng, 3, 15)
        val w = r(rng, 3, 12)
        val h = r(rng, 2, 10)
        return "一个长方体，长 $l 厘米，宽 $w 厘米，高 $h 厘米，体积是多少立方厘米？" to
            "${l * w * h} 立方厘米"
    }

    /** 解简易方程：ax±b=c 两种形态，解是 2~20 的整数 */
    private fun simpleEquation(rng: Random): Pair<String, String> {
        val a = r(rng, 2, 9)
        val x = r(rng, 2, 20)
        return if (rng.nextBoolean()) {
            val b = r(rng, 1, 40)
            "${a}x + $b = ${a * x + b}" to "x = $x"
        } else {
            val b = r(rng, 1, a * x - 1)
            "${a}x - $b = ${a * x - b}" to "x = $x"
        }
    }

    // ------------------------------------------------------------------ 六年级

    /** 分数乘法：真分数相乘，结果约成最简 */
    private fun fractionMul(rng: Random): Pair<String, String> {
        val b = r(rng, 2, 9)
        val d = r(rng, 2, 9)
        val a = r(rng, 1, b - 1)
        val c = r(rng, 1, d - 1)
        return "$a/$b × $c/$d＝（　　）" to simplify(a * c, b * d)
    }

    /** 分数除法：除以一个分数就是乘它的倒数 */
    private fun fractionDiv(rng: Random): Pair<String, String> {
        val b = r(rng, 2, 9)
        val d = r(rng, 2, 9)
        val a = r(rng, 1, b - 1)
        val c = r(rng, 1, d - 1)
        return "$a/$b ÷ $c/$d＝（　　）" to simplify(a * d, b * c)
    }

    /** 求一个数的百分之几：百分数只用能口算的那几个 */
    private val PERCENTS = intArrayOf(10, 20, 25, 40, 50, 60, 75, 80)

    private fun percentOf(rng: Random): Pair<String, String>? {
        val p = PERCENTS[rng.nextInt(PERCENTS.size)]
        val n = r(rng, 20, 200)
        val v = n * p / 100.0
        if (v != Math.floor(v)) return null
        return "$n 的 $p% 是（　　）" to "${v.toInt()}"
    }

    /**
     * 保留两位小数但要吃掉尾零：3.14 印「3.14」，1256 印「1256」而不是「1256.00」。
     * 网页端的 round2 是同一套规则，两端答案要对得上。
     */
    private fun round2(n: Double): String {
        val v = Math.round(n * 100) / 100.0
        return if (v == Math.floor(v) && !v.isInfinite()) "${v.toLong()}" else "$v"
    }

    /** 圆的周长与面积：半径 / 直径 × 周长 / 面积 四种问法；π 统一取 3.14 */
    private fun circle(rng: Random): Pair<String, String> {
        val radius = r(rng, 1, 20)
        val d = radius * 2
        return when (r(rng, 0, 3)) {
            0 -> "一个圆的半径是 $radius 厘米，周长是多少厘米？" to
                "${round2(2 * 3.14 * radius)} 厘米"
            1 -> "一个圆的直径是 $d 厘米，周长是多少厘米？" to
                "${round2(3.14 * d)} 厘米"
            2 -> "一个圆的半径是 $radius 厘米，面积是多少平方厘米？" to
                "${round2(3.14 * radius * radius)} 平方厘米"
            else -> "一个圆的直径是 $d 厘米，面积是多少平方厘米？" to
                "${round2(3.14 * radius * radius)} 平方厘米"
        }
    }
}
