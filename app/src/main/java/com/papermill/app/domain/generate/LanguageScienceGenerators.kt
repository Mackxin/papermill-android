package com.papermill.app.domain.generate

/**
 * 从词池里取一个**本卷还没用过**的词。
 *
 * 池子里的词用光了就抛 [NoFreshMaterialException] —— BaseGenerator 会把它当成
 * 「这一道没出来」跳过，最终这一大题少几道题，而不是在同一张卷子上印两遍同一个词。
 * 这比「凑满题数」重要：重复题印出去，家长第一个发现。
 */
private fun <T> freshWord(
    ctx: GenContext,
    prefix: String,
    pool: List<T>,
    key: (T) -> String,
): T {
    val fresh = pool.filterNot { "$prefix|${key(it)}" in ctx.usedMaterials }
    if (fresh.isEmpty()) throw NoFreshMaterialException()
    val picked = fresh[ctx.rng.nextInt(fresh.size)]
    ctx.usedMaterials += "$prefix|${key(picked)}"
    return picked
}

private fun freshWord(ctx: GenContext, prefix: String, pool: List<CnWord>): CnWord =
    freshWord(ctx, prefix, pool) { it.word }


// ------------------------------------------------------------------ 语文

object PinyinGenerator : BaseGenerator() {
    override val type = QuestionType.PINYIN
    override val subject = Subject.CHINESE
    override val topic = "拼音与字词"

    override fun make(ctx: GenContext): Pair<String, String> {
        val w = freshWord(ctx, "PINYIN", WordBank.chineseFor(ctx.grade))
        // 多种题干模板只是措辞不同，素材仍是同一个词 —— 由 freshWord 保证本卷只出现一次
        val stem = when (ctx.rng.nextInt(3)) {
            0 -> "看拼音写词语：${w.pinyin} →（　　）"
            1 -> "根据拼音写出词语：${w.pinyin}（　　）"
            else -> "拼音 ${w.pinyin}，对应的词语是（　　）"
        }
        return stem to w.word
    }
}

object HanziGenerator : BaseGenerator() {
    override val type = QuestionType.HANZI
    override val subject = Subject.CHINESE
    override val topic = "字音"

    override fun make(ctx: GenContext): Pair<String, String> {
        // 题干只露首字（「给「太」注音」），按首字记账：小鸟 / 小鱼 都出「小」就是重复题
        val w = freshWord(ctx, "HANZI", WordBank.chineseFor(ctx.grade)) { it.word.take(1) }
        val syllable = w.pinyin.substringBefore(' ')
        val stem = when (ctx.rng.nextInt(3)) {
            0 -> "给「${w.word[0]}」注音（　　）"
            1 -> "写出「${w.word[0]}」的读音（　　）"
            else -> "「${w.word[0]}」的拼音是（　　）"
        }
        return stem to syllable
    }
}

object DictationGenerator : BaseGenerator() {
    override val type = QuestionType.DICTATION
    override val subject = Subject.CHINESE
    override val topic = "词语积累"

    override fun make(ctx: GenContext): Pair<String, String> {
        val w = freshWord(
            ctx, "DICTATION", WordBank.chineseFor(ctx.grade).filter { it.word.length >= 2 },
        ) { it.word.take(1) }
        val stem = when (ctx.rng.nextInt(3)) {
            0 -> "补全词语：${w.word[0]}（　）"
            1 -> "把词语补充完整：${w.word[0]}＿（　）"
            else -> "填空：${w.word[0]}（　）→ ${w.word.substring(0, 1)}？"
        }
        return stem to w.word.substring(1)
    }
}

// ------------------------------------------------------------------ 英语

object EnglishWordGenerator : BaseGenerator() {
    override val type = QuestionType.ENGLISH_WORD
    override val subject = Subject.ENGLISH
    override val topic = "词汇"

    override fun make(ctx: GenContext): Pair<String, String> {
        // 英译汉 / 汉译英是同一素材的两面，同时出现在一张卷子上看着就是重复题
        val w = freshWord(ctx, "ENGLISH_WORD", WordBank.englishFor(ctx.grade), key = { it.en })
        return when (ctx.rng.nextInt(4)) {
            0 -> "英译汉：${w.en} →（　　）" to w.zh
            1 -> "汉译英：${w.zh} →（　　）" to w.en
            2 -> "写出 ${w.en} 的中文意思（　　）" to w.zh
            else -> "写出「${w.zh}」的英文单词（　　）" to w.en
        }
    }
}

// ------------------------------------------------------------------ 物理

object PhysicsGenerator : BaseGenerator() {
    override val type = QuestionType.PHYSICS_CALC
    override val subject = Subject.PHYSICS
    override val minGrade = 8
    override val topic = "公式计算"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val v = r(rng, 5, 120)
        val t = r(rng, 2, 8)
        val rho = r(rng, 2, 9)
        val vol = r(rng, 2, 12)
        val p = r(rng, 2, 12)
        val s = r(rng, 2, 8)
        val current = r(rng, 2, 9)
        val res = r(rng, 2, 6)
        val voltage = current * res
        val force = r(rng, 5, 40)
        val dist = r(rng, 2, 12)
        val mass = r(rng, 2, 20)

        return when (r(rng, 0, 5)) {
            0 -> "一辆车 $t 小时行驶 ${v * t} 千米，平均速度是多少？" to "$v 千米/时"
            1 -> "体积 $vol cm³ 的物体质量 ${rho * vol} g，密度是多少？" to "$rho g/cm³"
            2 -> "压力 ${p * s} N 作用在 $s m² 的面积上，压强是多少？" to "$p Pa"
            3 -> "电压 $voltage V，电阻 $res Ω，通过的电流是多少？" to "$current A"
            4 -> "用 $force N 的力沿力的方向移动 $dist m，做了多少功？" to "${force * dist} J"
            else -> "质量 $mass kg 的物体受到的重力是多少？（g 取 10 N/kg）" to "${mass * 10} N"
        }
    }
}

// ------------------------------------------------------------------ 化学

object ChemistryGenerator : BaseGenerator() {
    override val type = QuestionType.CHEMISTRY_CALC
    override val subject = Subject.CHEMISTRY
    override val minGrade = 9
    override val topic = "相对分子质量"

    private val atomicMass = mapOf(
        "H" to 1.0, "C" to 12.0, "O" to 16.0, "Na" to 23.0,
        "Cl" to 35.5, "Ca" to 40.0, "S" to 32.0, "Cu" to 64.0,
        "N" to 14.0, "Mg" to 24.0,
    )

    /** 分子式 → 组成（元素, 原子个数） */
    private val formulas = listOf(
        "H₂O" to listOf("H" to 2, "O" to 1),
        "CO₂" to listOf("C" to 1, "O" to 2),
        "O₂" to listOf("O" to 2),
        "H₂" to listOf("H" to 2),
        "CO" to listOf("C" to 1, "O" to 1),
        "NaCl" to listOf("Na" to 1, "Cl" to 1),
        "CaCO₃" to listOf("Ca" to 1, "C" to 1, "O" to 3),
        "HCl" to listOf("H" to 1, "Cl" to 1),
        "NaOH" to listOf("Na" to 1, "O" to 1, "H" to 1),
        "H₂SO₄" to listOf("H" to 2, "S" to 1, "O" to 4),
        "CH₄" to listOf("C" to 1, "H" to 4),
        "NH₃" to listOf("N" to 1, "H" to 3),
        "MgO" to listOf("Mg" to 1, "O" to 1),
    )

    override fun make(ctx: GenContext): Pair<String, String> {
        val (formula, parts) = formulas[ctx.rng.nextInt(formulas.size)]
        val mr = parts.sumOf { (atomicMass[it.first] ?: 0.0) * it.second }
        val ans = if (mr % 1.0 == 0.0) "${mr.toInt()}" else mr.toString()
        val hint = parts.distinctBy { it.first }
            .joinToString("，") { "${it.first}=${atomicMass[it.first]}" }
        val stem = when (ctx.rng.nextInt(2)) {
            0 -> "计算 $formula 的相对分子质量（$hint）＝（　　）"
            else -> "$formula 的相对分子质量是多少？（$hint）（　　）"
        }
        return stem to ans
    }
}

private fun r(rng: java.util.Random, from: Int, to: Int): Int =
    if (to <= from) from else from + rng.nextInt(to - from + 1)
