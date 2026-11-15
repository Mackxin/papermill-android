package com.papermill.app.domain.generate

/**
 * v0.16.0 看图题生成器：题干 + 矢量配图。
 *
 * ============================================================
 * 为什么这几类题型非有不可
 * ============================================================
 * 一二年级的数学课本里，有相当一部分题是「图 + 空」而不是「字 + 空」：
 * 数图形、看钟面、读统计图、看图列式。这些题型的考点恰恰是**看图取信息**，
 * 换成一句文字描述就没法考了 ——「钟面上时针在 8 和 9 中间、分针指 6，
 * 现在是几时几分」这句描述本身就等于把答案说了。
 *
 * 所以这一批生成器一律走 [BaseGenerator.makeFigure]：随机数算出来之后，
 * 数字同时进题干和图形数据，「图上画 8 个、答案写 6」这种错在结构上就不会发生。
 *
 * 配图全部用 Canvas 现画（见 `core/pdf/figure/FigurePainter`），
 * 不打包任何图片资源，也不搬任何教材插图。
 */

/** 闭区间随机整数。`java.util.Random` 只有单参数 nextInt，Kotlin 的那套扩展在这儿用不上 */
private fun rand(rng: java.util.Random, from: Int, to: Int): Int =
    if (to <= from) from else from + rng.nextInt(to - from + 1)

/** 图形代号：0 圆、1 三角、2 方块、3 五角星。下标就是 [FigureSpec.shapes] 里的值 */
private val SHAPE_SYMBOLS = arrayOf("○", "△", "□", "☆")

/**
 * 看图数一数（一、二年级）。
 *
 * 三行不同图形各若干个，问各有几个。看上去简单，实际考的是
 * 「分类计数」—— 一年级最典型的失分点是把别的图形也数进去。
 */
object FigureCountGenerator : BaseGenerator() {
    override val type = QuestionType.FIGURE_COUNT
    override val subject = Subject.MATH
    override val maxGrade = 2
    override val topic = "数数与分类"

    override fun makeFigure(ctx: GenContext): FigureQuestion {
        val rng = ctx.rng
        // 三种图形的出场顺序也打乱：总按「○△□」排，学生会背位置而不是数数
        val order = listOf(0, 1, 2, 3).shuffled(rng).take(3)
        val counts = order.map { rand(rng, 3, 9) }
        val parts = order.mapIndexed { i, kind -> "${SHAPE_SYMBOLS[kind]}（　　）个" }
        return FigureQuestion(
            // 只说「哪几个图形、各几个空」——「数一数把结果填在括号里」这句
            // 已经印在大题标题下面了（QuestionType.instruction），再印一遍就是重复
            stem = parts.joinToString("　"),
            // 答案按题干的顺序给，家长一眼能对上；不带分隔符看不清是哪一行的数
            answer = order.mapIndexed { i, kind -> "${SHAPE_SYMBOLS[kind]}${counts[i]}" }.joinToString("　"),
            figure = FigureSpec(
                kind = FigureKind.COUNTING,
                groups = counts,
                shapes = order,
            ),
        )
    }
}

/**
 * 看图列式（一、二年级）。
 *
 * 左边一堆、右边一堆，中间一条虚线，求一共多少 —— 这是「加法是两个部分合并」
 * 这个概念最原始的样子。只给两个数、写一句「小明有 5 个苹果，妈妈又给他 3 个」
 * 是抽象化之后的版本，一二年级要先看图。
 */
object FigureExprGenerator : BaseGenerator() {
    override val type = QuestionType.FIGURE_EXPR
    override val subject = Subject.MATH
    override val maxGrade = 2
    override val topic = "加法的意义"

    override fun makeFigure(ctx: GenContext): FigureQuestion {
        val rng = ctx.rng
        val left = rand(rng, 3, 7)
        val right = rand(rng, 2, 5)
        val kind = rand(rng, 0, 3)
        return FigureQuestion(
            stem = "（　　　　　）",
            answer = "$left+${right}=${left + right}",
            figure = FigureSpec(
                kind = FigureKind.TWO_GROUPS,
                groups = listOf(left, right),
                shapes = listOf(kind),
            ),
        )
    }
}

/**
 * 认识钟面（一~三年级）。
 *
 * 一年级只出整时和半时；二三年级出 5 分一格。时针按**真实角度**画
 * （跟着分钟走一点点），否则 8:30 的时针正对 8，学生读成「8 时」也算对，
 * 这道题就失去了区分度。
 */
object ClockReadGenerator : BaseGenerator() {
    override val type = QuestionType.CLOCK_READ
    override val subject = Subject.MATH
    override val maxGrade = 3
    override val topic = "认识钟表"

    override fun makeFigure(ctx: GenContext): FigureQuestion {
        val rng = ctx.rng
        val hour = rand(rng, 1, 12)
        // 一年级只有整时 / 半时；高年级按 5 分一格
        val minute = if (ctx.grade <= 1) {
            if (rng.nextBoolean()) 0 else 30
        } else {
            rand(rng, 0, 11) * 5
        }
        // 题干只留空的形状：整句「看钟面写出时刻」印一次就够了
        val stem = if (minute == 0) "（　　　）时" else "（　　　）时（　　　）分"
        val answer = if (minute == 0) "${hour}时" else "${hour}时${minute}分"
        return FigureQuestion(
            stem = stem,
            answer = answer,
            figure = FigureSpec(kind = FigureKind.CLOCK, groups = listOf(hour, minute)),
        )
    }
}

/**
 * 条形统计图（二~四年级）。
 *
 * 四类数据、柱顶标数、纵轴给整齐刻度。两小问：最多的是哪一类、一共多少 ——
 * 「最多」考的是比较，「一共」考的是求和，这两件事在统计图里是一体的。
 */
object BarChartGenerator : BaseGenerator() {
    override val type = QuestionType.BAR_CHART
    override val subject = Subject.MATH
    override val minGrade = 2
    override val maxGrade = 4
    override val topic = "统计图表"

    /** 四组固定题材：都是一年级能读懂的名词，不涉及任何教材原文 */
    private val TOPIC_LABELS = listOf(
        listOf("苹果", "香蕉", "橘子", "梨"),
        listOf("故事书", "漫画书", "科普书", "绘本"),
        listOf("跳绳", "踢球", "跑步", "拍球"),
        listOf("晴天", "多云", "雨天", "阴天"),
    )

    override fun makeFigure(ctx: GenContext): FigureQuestion {
        val rng = ctx.rng
        val labels = TOPIC_LABELS[rng.nextInt(TOPIC_LABELS.size)]
        // 四个数必须互不相同：否则「最多的是哪个」有两个答案，题目本身就错了
        val values = ArrayList<Int>(4)
        while (values.size < 4) {
            val v = rand(rng, 2, 10)
            if (v !in values) values.add(v)
        }
        val maxIdx = values.indices.maxBy { values[it] }
        return FigureQuestion(
            stem = "①（　　　　　）最多；② 一共有（　　　）个。",
            answer = "${labels[maxIdx]}；${values.sum()}",
            figure = FigureSpec(
                kind = FigureKind.BAR_CHART,
                labels = labels,
                values = values,
                unit = "个",
            ),
        )
    }
}
