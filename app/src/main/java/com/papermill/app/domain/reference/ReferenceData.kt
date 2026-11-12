package com.papermill.app.domain.reference

import com.papermill.app.core.pdf.render.PaperTable

data class ReferenceSheet(
    val id: String,
    val title: String,
    val subtitle: String,
    val table: PaperTable,
)

/**
 * 学生必备速查表 —— 可一键生成 A4 打印、贴墙/夹书里。
 *
 * 内容为基础事实与公式（不受著作权保护的事实性内容），自行整理编排，
 * 不复制任何教辅的版式或讲解文字。
 */
object ReferenceData {

    private fun multiplyTable(): PaperTable {
        val headers = listOf("×") + (1..9).map { it.toString() }
        val rows = (1..9).map { i ->
            listOf("$i") + (1..9).map { j -> if (j <= i) "${j}×${i}=${i * j}" else "" }
        }
        return PaperTable(
            headers = headers,
            weights = listOf(0.7f) + List(9) { 1f },
            rows = rows,
            rowsPerBlock = 9,
        )
    }

    private fun pinyinTable(): PaperTable {
        val shengmu = listOf(
            "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j",
            "q", "x", "zh", "ch", "sh", "r", "z", "c", "s", "y", "w",
        )
        val yunmu = listOf(
            "a", "o", "e", "i", "u", "ü", "ai", "ei", "ui", "ao", "ou", "iu",
            "ie", "üe", "er", "an", "en", "in", "un", "ün", "ang", "eng", "ing", "ong",
        )
        val zhengti = listOf(
            "zhi", "chi", "shi", "ri", "zi", "ci", "si", "yi", "wu", "yu",
            "ye", "yue", "yuan", "yin", "yun", "ying",
        )
        fun chunk(items: List<String>, label: String): List<List<String>> =
            items.chunked(6).mapIndexed { i, part ->
                listOf(if (i == 0) label else "") + part + List(6 - part.size) { "" }
            }
        val rows = ArrayList<List<String>>()
        rows.addAll(chunk(shengmu, "声母 23"))
        rows.add(listOf("", "", "", "", "", "", ""))
        rows.addAll(chunk(yunmu, "韵母 24"))
        rows.add(listOf("", "", "", "", "", "", ""))
        rows.addAll(chunk(zhengti, "整体认读 16"))
        return PaperTable(
            headers = listOf("类别", "1", "2", "3", "4", "5", "6"),
            weights = listOf(1.6f) + List(6) { 1f },
            rows = rows,
            rowsPerBlock = 18,
        )
    }

    private fun primaryMath(): PaperTable = PaperTable(
        headers = listOf("知识点", "公式 / 法则"),
        weights = listOf(1f, 3.2f),
        rows = listOf(
            listOf("加法交换律", "a + b = b + a"),
            listOf("乘法交换律", "a × b = b × a"),
            listOf("乘法分配律", "(a + b) × c = a×c + b×c"),
            listOf("长方形周长", "C = (长 + 宽) × 2"),
            listOf("长方形面积", "S = 长 × 宽"),
            listOf("正方形面积", "S = 边长 × 边长"),
            listOf("三角形面积", "S = 底 × 高 ÷ 2"),
            listOf("平行四边形面积", "S = 底 × 高"),
            listOf("梯形面积", "S = (上底 + 下底) × 高 ÷ 2"),
            listOf("圆的周长", "C = πd = 2πr"),
            listOf("圆的面积", "S = πr²"),
            listOf("长方体体积", "V = 长 × 宽 × 高"),
            listOf("正方体体积", "V = 棱长³"),
            listOf("速度", "速度 = 路程 ÷ 时间"),
            listOf("单价", "总价 = 单价 × 数量"),
            listOf("百分数", "百分数 = 部分 ÷ 整体 × 100%"),
        ),
        rowsPerBlock = 18,
    )

    private fun juniorMath(): PaperTable = PaperTable(
        headers = listOf("知识点", "公式 / 法则"),
        weights = listOf(1f, 3.2f),
        rows = listOf(
            listOf("绝对值", "|a| = a (a≥0)；|a| = -a (a<0)"),
            listOf("同底数幂相乘", "aᵐ · aⁿ = aᵐ⁺ⁿ"),
            listOf("幂的乘方", "(aᵐ)ⁿ = aᵐⁿ"),
            listOf("平方差", "(a+b)(a-b) = a² - b²"),
            listOf("完全平方", "(a±b)² = a² ± 2ab + b²"),
            listOf("二次根式", "√a · √b = √(ab)（a≥0，b≥0）"),
            listOf("一元二次方程", "ax²+bx+c=0，x = [-b±√(b²-4ac)] / 2a"),
            listOf("判别式", "Δ = b² - 4ac；Δ>0 两不等实根"),
            listOf("勾股定理", "a² + b² = c²（直角三角形）"),
            listOf("一次函数", "y = kx + b（k≠0）"),
            listOf("反比例函数", "y = k/x（k≠0）"),
            listOf("三角形内角和", "180°"),
            listOf("n 边形内角和", "(n - 2) × 180°"),
            listOf("平均数", "x̄ = (x₁+x₂+…+xₙ) / n"),
        ),
        rowsPerBlock = 18,
    )

    private fun physics(): PaperTable = PaperTable(
        headers = listOf("物理量", "公式", "常用单位"),
        weights = listOf(1.2f, 2.4f, 1.2f),
        rows = listOf(
            listOf("速度", "v = s / t", "m/s、km/h"),
            listOf("密度", "ρ = m / V", "kg/m³、g/cm³"),
            listOf("重力", "G = mg（g≈9.8 N/kg）", "N"),
            listOf("压强", "p = F / S", "Pa"),
            listOf("液体压强", "p = ρgh", "Pa"),
            listOf("浮力", "F浮 = ρ液 g V排", "N"),
            listOf("杠杆平衡", "F₁l₁ = F₂l₂", "—"),
            listOf("功", "W = Fs", "J"),
            listOf("功率", "P = W / t", "W"),
            listOf("机械效率", "η = W有 / W总 × 100%", "—"),
            listOf("热量", "Q = cmΔt", "J"),
            listOf("电流", "I = U / R", "A"),
            listOf("电功", "W = UIt", "J、kW·h"),
            listOf("电功率", "P = UI = I²R", "W"),
            listOf("焦耳定律", "Q = I²Rt", "J"),
        ),
        rowsPerBlock = 18,
    )

    private fun chemistry(): PaperTable = PaperTable(
        headers = listOf("类别", "内容"),
        weights = listOf(1f, 3.2f),
        rows = listOf(
            listOf("常见化合价", "K Na Ag H 为 +1；Ca Mg Ba Zn 为 +2；Al 为 +3"),
            listOf("常见化合价", "O 为 -2；Cl 为 -1；SO₄ 为 -2；NO₃ 为 -1；OH 为 -1"),
            listOf("金属活动性", "K Ca Na Mg Al Zn Fe Sn Pb (H) Cu Hg Ag Pt Au"),
            listOf("质量守恒", "参加反应各物质质量总和 = 生成各物质质量总和"),
            listOf("氧气制取", "2H₂O₂ --MnO₂--> 2H₂O + O₂↑"),
            listOf("二氧化碳制取", "CaCO₃ + 2HCl = CaCl₂ + H₂O + CO₂↑"),
            listOf("氢气燃烧", "2H₂ + O₂ --点燃--> 2H₂O"),
            listOf("碳燃烧", "C + O₂ --点燃--> CO₂（充分燃烧）"),
            listOf("中和反应", "HCl + NaOH = NaCl + H₂O"),
            listOf("铁与硫酸铜", "Fe + CuSO₄ = FeSO₄ + Cu"),
            listOf("溶液酸碱度", "pH<7 酸性；pH=7 中性；pH>7 碱性"),
            listOf("相对原子质量", "H 1　C 12　N 14　O 16　Na 23　Mg 24　S 32　Cl 35.5　Ca 40　Cu 64"),
        ),
        rowsPerBlock = 18,
    )

    private fun irregularVerbs(): PaperTable = PaperTable(
        headers = listOf("原形", "过去式", "过去分词", "中文"),
        weights = listOf(1f, 1f, 1f, 1.2f),
        rows = listOf(
            listOf("be", "was/were", "been", "是"),
            listOf("become", "became", "become", "变成"),
            listOf("begin", "began", "begun", "开始"),
            listOf("bring", "brought", "brought", "带来"),
            listOf("build", "built", "built", "建造"),
            listOf("buy", "bought", "bought", "买"),
            listOf("catch", "caught", "caught", "抓住"),
            listOf("come", "came", "come", "来"),
            listOf("do", "did", "done", "做"),
            listOf("draw", "drew", "drawn", "画"),
            listOf("drink", "drank", "drunk", "喝"),
            listOf("eat", "ate", "eaten", "吃"),
            listOf("feel", "felt", "felt", "感觉"),
            listOf("find", "found", "found", "找到"),
            listOf("get", "got", "got", "得到"),
            listOf("give", "gave", "given", "给"),
            listOf("go", "went", "gone", "去"),
            listOf("have", "had", "had", "有"),
            listOf("know", "knew", "known", "知道"),
            listOf("make", "made", "made", "制作"),
            listOf("read", "read", "read", "读"),
            listOf("see", "saw", "seen", "看见"),
            listOf("take", "took", "taken", "拿"),
            listOf("tell", "told", "told", "告诉"),
            listOf("write", "wrote", "written", "写"),
        ),
        rowsPerBlock = 20,
    )

    val sheets: List<ReferenceSheet> = listOf(
        ReferenceSheet("mul99", "九九乘法表", "45 条乘法口诀 · 建议打印张贴", multiplyTable()),
        ReferenceSheet("pinyin", "汉语拼音音节表", "声母 23 · 韵母 24 · 整体认读 16", pinyinTable()),
        ReferenceSheet("math_primary", "小学数学公式表", "图形 / 运算 / 数量关系", primaryMath()),
        ReferenceSheet("math_junior", "初中数学公式表", "代数 / 几何 / 函数", juniorMath()),
        ReferenceSheet("physics", "初中物理公式表", "力学 / 热学 / 电学", physics()),
        ReferenceSheet("chemistry", "初中化学必备表", "化合价 / 方程式 / 活动性顺序", chemistry()),
        ReferenceSheet("verbs", "英语不规则动词表", "原形 / 过去式 / 过去分词", irregularVerbs()),
    )

    fun byId(id: String): ReferenceSheet? = sheets.firstOrNull { it.id == id }
}
