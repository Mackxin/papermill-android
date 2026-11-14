package com.papermill.app.domain.generate

/**
 * 出卷引擎的业务模型。
 *
 * 这一层不碰 Android、不碰 PDF —— 纯 Kotlin 数据，方便将来写 JVM 单测校验答案。
 */

enum class Subject(val code: String, val label: String) {
    MATH("MATH", "数学"),
    CHINESE("CHINESE", "语文"),
    ENGLISH("ENGLISH", "英语"),
    PHYSICS("PHYSICS", "物理"),
    CHEMISTRY("CHEMISTRY", "化学"),
    ;

    /** 哪些年级开这门课。UI 用它把不可选的科目置灰，不是靠写死 if-else。 */
    fun availableFor(grade: Int): Boolean = when (this) {
        MATH, CHINESE -> grade in 1..9
        ENGLISH -> grade in 3..9
        PHYSICS -> grade in 8..9
        CHEMISTRY -> grade == 9
    }
}

enum class QuestionType(
    val label: String,
    /** 默认栏数；有答题横线的题型强制单栏 */
    val columns: Int = 2,
    /** 每题下方留几条答题横线 */
    val answerLines: Int = 0,
) {
    // 小学数学
    ORAL("口算", 2),
    // v0.8.0：求未知项 / 有余数除法（口算的两个「变式」，答案位在算式中间）
    ORAL_MISSING("求未知项", 3),
    ORAL_REMAINDER("有余数除法", 2),
    VERTICAL("竖式计算", 1, 2),
    EXPRESSION("脱式计算", 1, 2),
    FRACTION("分数运算", 2),
    EQUATION("解方程", 2, 1),
    WORD_PROBLEM("应用题", 1, 4),

    // 初中数学
    RATIONAL("有理数运算", 2),
    ALGEBRA("整式与方程", 2),
    RADICAL("二次根式", 2),

    // 语文
    PINYIN("看拼音写词语", 2),
    HANZI("生字注音", 4),
    DICTATION("补全词语", 4),

    // 英语
    ENGLISH_WORD("单词默写", 2),

    // 理化
    PHYSICS_CALC("物理计算", 1, 3),
    CHEMISTRY_CALC("化学计算", 2),

    // 内置自编题库（概念 / 选择 / 判断）
    KNOWLEDGE("自编题库", 1, 1),

    // v0.4.0：把外部资源站的「题型规则」转成自编程序化生成器
    // 只借鉴题型模式（填括号、凑十、连加连减、单位换算……），不收录任何第三方原题
    FILL_BLANK("填括号", 3),
    MAKE_TEN("凑十练习", 3),
    CHAIN_CALC("连加连减", 2),
    MIXED_CALC("加减混合", 2),
    TENS_CALC("整十数运算", 3),
    TWO_DIGIT("两位数加减", 2),
    UNIT_CONVERT("单位换算", 2),
    COMPARE("比大小", 3),
    NUMBER_PATTERN("找规律填数", 2),
    COUNTING("数数与认数", 2),
    LETTER_ORDER("字母顺序", 3),

    // v0.5.0 英语题型：只借鉴「单词拼装 / 句子排序 / 选词填空」的题型模式，
    // 词表与句子全部自编，不取自任何第三方仓库
    WORD_SCRAMBLE("单词拼装", 2),
    SENTENCE_ORDER("连词成句", 1),
    CLOZE("选词填空", 1),

    // v0.5.0 主题识字：6 大生活主题的自编词库（拼音已逐条校对）
    THEME_PINYIN("主题·看拼音写词语", 2),
    // 带 A/B/C 三个选项的长题干必须占整行：半栏会把选项折得七零八落
    THEME_SORT("主题·找不同类", 1),
    THEME_GROUP("主题·归类填空", 1),

    // v0.7.0 英语音标：48 个国际音标（通用记音符号），例词全为基础高频词
    // 四个选项都是音标符号，半栏会把「A. /ɒ/」折成两行，必须整行
    PHONETIC_PICK("音标辨认", 1),
    PHONETIC_WRITE("看音标写单词", 2),

    // v0.7.0 英语语法：22 个语法点、43 道自编语法题，选项打乱后出选择题
    GRAMMAR("语法选择", 1),

    // v0.7.0 课本同步：按年级分段的生字表 / 核心词表，抽测用
    TEXTBOOK_HANZI("课本生字注音", 4),
    TEXTBOOK_WORD("课本单词默写", 4),

    // v0.8.0 成语与古诗：题库型语文题型，出题时从内置库里随机抽
    // 选项是整句释义 / 整句诗，必须占整行，半栏会折得七零八落
    IDIOM_FILL("成语填空", 3),
    IDIOM_MEANING("成语释义", 1),
    POEM_FILL("古诗接句", 1),
    POEM_MATCH("古诗常识", 1),

    // v0.9.0 课标题库（cn-math-taxonomy，CC BY-NC 4.0）：五科知识点配套题，跨科通用
    // 题干普遍是整句（数学中位 33 字、英语最长 136 字），半栏会折成竖条，只能整行
    CURRIC_BLANK("知识点填空", 1),
    CURRIC_CHOICE("知识点选择", 1),
}

enum class AnswerMode(val label: String) {
    NONE("不带答案"),
    LAST_PAGE("附答案页"),
}

/**
 * 卷面方向：竖版 / 横版 A4。
 * 放在 domain 层是因为它属于「用户对卷子的要求」，PDF 层只是照着执行。
 * 打印时会同步到 PrintAttributes.MediaSize，避免驱动把横版内容再缩放一次。
 */
enum class PageOrientation(val label: String, val shortLabel: String) {
    PORTRAIT("A4 竖版", "竖版"),
    LANDSCAPE("A4 横版", "横版"),
}

data class GeneratedQuestion(
    val no: Int,
    val type: QuestionType,
    val stem: String,
    val answer: String,
    /** 知识点标签，错题本按它归类（都是通用知识点名，不用教材原文） */
    val topic: String = "",
)

data class GeneratedSection(
    val index: Int,
    val title: String,
    val type: QuestionType,
    val questions: List<GeneratedQuestion>,
    val columns: Int,
    val answerLines: Int,
)

data class PaperSpec(
    val grade: Int,
    val subject: Subject,
    val title: String,
    val subtitle: String,
    /** 题型 → 题量；只保留 > 0 的项 */
    val counts: Map<QuestionType, Int>,
    val difficulty: Int,
    /** 客观题栏数（主观题强制单栏） */
    val columns: Int,
    val answerMode: AnswerMode,
    val showNameRow: Boolean,
    val fontSize: Float,
    val seed: Long,
    /** 错题重练卷：标题上要标明，且默认附答案 */
    val isMistakePaper: Boolean = false,
    /** 交叉学科：一份卷里可以同时出数学 + 语文 + 英语等；为空时按 [subject] 单科处理 */
    val subjects: List<Subject> = emptyList(),
    /** 纸张方向：横版时 PDF 页面是 842×595pt，系统打印框也按横版 A4 匹配 */
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
)

data class GeneratedPaper(
    val spec: PaperSpec,
    val sections: List<GeneratedSection>,
    val questions: List<GeneratedQuestion>,
    val createdAt: Long,
)

/** 兼容单科旧数据：没勾选多学科时按主科目处理 */
fun PaperSpec.effectiveSubjects(): List<Subject> =
    if (subjects.isEmpty()) listOf(subject) else subjects.distinct()

/** 年级画像：数域上限、是否允许进位/退位、难度基准。数据表驱动，避免 if-else 地狱。 */
data class GradeProfile(
    val grade: Int,
    val addMax: Int,
    val mulMax: Int,
    val divisorMax: Int,
    val allowCarry: Boolean,
    val allowBorrow: Boolean,
    val fractionMaxDen: Int,
    val baseDifficulty: Int,
)

object GradeProfiles {
    private val table = listOf(
        GradeProfile(1, 20, 0, 0, false, false, 0, 1),
        GradeProfile(2, 100, 9, 9, true, true, 0, 1),
        GradeProfile(3, 1000, 9, 9, true, true, 10, 2),
        GradeProfile(4, 10000, 12, 12, true, true, 12, 2),
        GradeProfile(5, 100000, 12, 12, true, true, 16, 3),
        GradeProfile(6, 1000000, 12, 12, true, true, 20, 3),
        GradeProfile(7, 1000, 12, 12, true, true, 20, 3),
        GradeProfile(8, 10000, 15, 15, true, true, 24, 4),
        GradeProfile(9, 100000, 15, 15, true, true, 24, 4),
    )

    fun of(grade: Int): GradeProfile = table.firstOrNull { it.grade == grade } ?: table[2]

    fun gradeLabel(grade: Int): String = when (grade) {
        1 -> "一年级"; 2 -> "二年级"; 3 -> "三年级"; 4 -> "四年级"
        5 -> "五年级"; 6 -> "六年级"; 7 -> "初一"; 8 -> "初二"; 9 -> "初三"
        else -> "${grade}年级"
    }

    fun stageLabel(grade: Int): String = if (grade <= 6) "小学" else "初中"
}
