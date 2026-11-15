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
    SCIENCE("SCIENCE", "科学"),
    PHYSICS("PHYSICS", "物理"),
    CHEMISTRY("CHEMISTRY", "化学"),
    ;

    /** 哪些年级开这门课。UI 用它把不可选的科目置灰，不是靠写死 if-else。 */
    fun availableFor(grade: Int): Boolean = when (this) {
        MATH, CHINESE -> grade in 1..9
        ENGLISH -> grade in 3..9
        // 小学科学按课标是 1–6 年级（初中分成了物理 / 化学 / 生物 / 地理）
        SCIENCE -> grade in 1..6
        PHYSICS -> grade in 8..9
        CHEMISTRY -> grade == 9
    }
}

enum class QuestionType(
    val label: String,
    /** 推荐栏数：排版时与用户选的全局栏数取较大者；<= 1 表示题干长、必须整宽 */
    val columns: Int = 2,
    /** 每题下方留几条答题横线 */
    val answerLines: Int = 0,
    /**
     * 大题说明：印在大题标题下面、只印一次。
     *
     * 这类「做题指令」以前是拼在每道题题干前面的（「用竖式计算：2515+3568＝」），
     * 一张卷印 20 遍就是满纸重复。现在题干只留真正变化的部分，
     * 指令统一提到大题下说一次。
     */
    val instruction: String = "",
) {
    // 小学数学
    ORAL("口算", 2),
    // v0.8.0：求未知项 / 有余数除法（口算的两个「变式」，答案位在算式中间）
    ORAL_MISSING("求未知项", 3),
    ORAL_REMAINDER("有余数除法", 2),
    // 竖式是竖着写的：三栏并排刚好，一栏一题能把一页纸浪费掉一半。
    // 三道横线够写「两个加数 + 横线 + 得数」，四位数加法也不会挤。
    VERTICAL("竖式计算", 3, 3, "用竖式计算下面各题，把得数写在括号里。"),
    EXPRESSION("脱式计算", 1, 2, "脱式计算，写出每一步的过程。"),
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
    WORD_SCRAMBLE("单词拼装", 2, 0, "把打乱的字母重新排好，写出正确的单词，拼写要完整。"),
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

    // v0.11.0 小学科学（1–6 年级）：题库全自编，见 domain/content/ScienceBank.kt
    // 选择题四个选项一行放不下会折得乱七八糟，必须整行
    SCIENCE_CHOICE("科学选择", 1),
    SCIENCE_JUDGE("科学判断", 2),
    SCIENCE_BLANK("科学填空", 1),

    // v0.16.0 看图题：题干 + 矢量配图。四类都声明 1 栏 —— 图要占满一行才看得清，
    // 挤进半栏会缩成指甲盖大小。图全部 Canvas 自绘（圆 / 弧 / 路径），
    // 不打包任何图片资源，PDF 体积几乎不变。
    FIGURE_COUNT("看图数一数", 1, 0, "先数一数每种图形各有几个，把数填在括号里。"),
    FIGURE_EXPR("看图列式", 1, 0, "看图数一数一共有几个，把加法算式写在括号里。"),
    CLOCK_READ("认识钟面", 1, 0, "看钟面，写出钟面上的时刻。"),
    BAR_CHART("条形统计图", 1, 0, "看统计图回答问题，把答案填在括号里。"),
    ;

    /**
     * 题型大类。题型倾向（[Tilt]）靠它决定「哪些题型算选择题、哪些算计算题」。
     *
     * 写成集中式 when 而不是塞进枚举构造参数：49 个题型逐一加参数容易漏，
     * 这里一眼能看全，新加题型忘了归类会在编译期报「when 不完整」。
     */
    val family: QuestionFamily
        get() = when (this) {
            ORAL, ORAL_MISSING, ORAL_REMAINDER, VERTICAL, EXPRESSION, FRACTION, EQUATION,
            RATIONAL, ALGEBRA, RADICAL, MAKE_TEN, CHAIN_CALC, MIXED_CALC, TENS_CALC,
            TWO_DIGIT, COMPARE, COUNTING,
            -> QuestionFamily.CALC

            WORD_PROBLEM, PHYSICS_CALC, CHEMISTRY_CALC -> QuestionFamily.APPLY

            // 统计图是「读图取信息再答题」，归应用最贴切（它考的是看图表说话，不是算）
            BAR_CHART -> QuestionFamily.APPLY

            // 看图列式的落点是写算式，归计算
            FIGURE_EXPR -> QuestionFamily.CALC

            SENTENCE_ORDER, CLOZE -> QuestionFamily.READING

            SCIENCE_JUDGE -> QuestionFamily.JUDGE

            // 看图数一数、认钟面都是「看图填一个数」，归填空
            FIGURE_COUNT, CLOCK_READ -> QuestionFamily.BLANK

            KNOWLEDGE, THEME_SORT, PHONETIC_PICK, GRAMMAR, IDIOM_MEANING, POEM_MATCH,
            CURRIC_CHOICE, SCIENCE_CHOICE,
            -> QuestionFamily.CHOICE

            PINYIN, HANZI, DICTATION, ENGLISH_WORD, FILL_BLANK, UNIT_CONVERT,
            NUMBER_PATTERN, LETTER_ORDER, WORD_SCRAMBLE, THEME_PINYIN, THEME_GROUP,
            PHONETIC_WRITE, TEXTBOOK_HANZI, TEXTBOOK_WORD, IDIOM_FILL, POEM_FILL,
            CURRIC_BLANK, SCIENCE_BLANK,
            -> QuestionFamily.BLANK
        }
}

/**
 * 题型大类。只用来做「题型倾向」的加权，不参与排版。
 */
enum class QuestionFamily(val label: String) {
    CHOICE("选择"),
    BLANK("填空"),
    JUDGE("判断"),
    CALC("计算"),
    APPLY("应用"),
    READING("阅读"),
}

/**
 * 题型倾向：决定这份卷子以哪类题为主。
 *
 * 对应「均衡搭配 / 选择题偏多 / …」这类出题偏好 —— 它改的是**各大题的题型与题量**，
 * 不改难度、不改版式。
 */
enum class Tilt(val label: String, val hint: String) {
    BALANCED("均衡搭配", "按年级的常规配比，各类题型都来一点"),
    CHOICE("选择题偏多", "选择题占大头，批改快，适合快速过一遍知识点"),
    BLANK("填空题偏多", "填空占大头，练书写与记忆"),
    JUDGE("判断题偏多", "判断题占大头，专门练概念辨析"),
    CALC("计算解答偏多", "口算 / 竖式 / 脱式这类计算题占大头"),
    APPLY("应用综合偏多", "应用题与综合题占大头，练审题与列式"),
    READING("阅读理解偏多", "连词成句 / 选词填空这类语英阅读题占大头"),
    ;

    /** 这个倾向偏好的题型大类；[BALANCED] 没有偏好 */
    val prefer: QuestionFamily? get() = when (this) {
        BALANCED -> null
        CHOICE -> QuestionFamily.CHOICE
        BLANK -> QuestionFamily.BLANK
        JUDGE -> QuestionFamily.JUDGE
        CALC -> QuestionFamily.CALC
        APPLY -> QuestionFamily.APPLY
        READING -> QuestionFamily.READING
    }
}

/**
 * 卷面用途：决定这份卷子「铺多宽」。
 *
 * 日常巩固 = 一种题型大量练；单元检测 = 三四种题型均衡；综合复习 = 题型铺满、题量更大。
 */
enum class PaperPurpose(val label: String, val hint: String) {
    DAILY("日常巩固", "一两种题型大量练，一页盯住一个知识点"),
    UNIT("单元检测", "三四种题型均衡，覆盖一个单元"),
    REVIEW("综合复习", "题型铺满、题量更大，按整册复习"),
    ;

    /** 单科卷用几种题型 */
    val kinds: Int get() = when (this) {
        DAILY -> 1
        UNIT -> 3
        REVIEW -> 4
    }
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

/**
 * 配图的种类。绘制细节在 `core/pdf/figure/FigurePainter`，这里只留「是什么图」。
 */
enum class FigureKind {
    /** 按组排列的图形（每组一行），用来数数 / 分类计数 */
    COUNTING,

    /** 十格阵：上排 5 格、下排 5 格，涂黑若干格 —— 一年级「凑十」的标准教具 */
    TEN_FRAME,

    /** 钟面：时针 + 分针 */
    CLOCK,

    /** 条形统计图：分类 + 数值 */
    BAR_CHART,

    /** 两组物品中间加分隔（看图列式）：一眼看出「左边几个、右边几个」 */
    TWO_GROUPS,
}

/**
 * 一道看图题的配图**数据**。
 *
 * ============================================================
 * 为什么图形数据放在 domain、绘制放在 pdf 层
 * ============================================================
 * 本项目 domain 层有一条硬约定：纯 Kotlin，不碰 Android 也不碰 PDF
 * （见 Models.kt 顶部注释），这样出题逻辑将来能写 JVM 单测。
 * 但「画一条弧」必须用 `android.graphics`。
 *
 * 所以拆成两半：这里只描述「几个圆、几组、时针指到几」，
 * 真正落笔的 `FigurePainter` 在 pdf 层读这份数据去画。
 * 生成器负责算数和写题干，绘制器负责长什么样 —— 两边互不知道对方的实现。
 */
data class FigureSpec(
    val kind: FigureKind,
    /**
     * 各组数量。含义按 [kind] 变：
     *  - COUNTING：每组一行，第 i 项是第 i 行有几个
     *  - TWO_GROUPS：只用前两项（左边、右边）
     *  - TEN_FRAME：只用第一项（涂黑几格，0..10）
     *  - CLOCK：第一项 = 时，第二项 = 分
     */
    val groups: List<Int> = emptyList(),
    /** 每行用第几种图形（0 圆 1 三角 2 方块 3 五角星），长度不足时循环取 */
    val shapes: List<Int> = emptyList(),
    /** 统计图的分类名（4 项以内排得开） */
    val labels: List<String> = emptyList(),
    /** 统计图每类的数值 */
    val values: List<Int> = emptyList(),
    /** 统计图的纵轴单位，印在图右上角 */
    val unit: String = "",
)

data class GeneratedQuestion(
    val no: Int,
    val type: QuestionType,
    val stem: String,
    val answer: String,
    /** 知识点标签，错题本按它归类（都是通用知识点名，不用教材原文） */
    val topic: String = "",
    /** 看图题的配图；null = 纯文字题（绝大多数题型都是） */
    val figure: FigureSpec? = null,
)

data class GeneratedSection(
    val index: Int,
    val title: String,
    val type: QuestionType,
    val questions: List<GeneratedQuestion>,
    val columns: Int,
    val answerLines: Int,
    /** 印在大题标题下面、只印一次的做题说明（见 [QuestionType.instruction]） */
    val instruction: String = "",
)

/**
 * 题间行距档位（v0.17.0）。
 *
 * ============================================================
 * 为什么是「基础间距 + 撑开上限」两个数，而不是一个系数
 * ============================================================
 * 用户要的是「充分利用纸张，别留太多空白」，这句话其实包含两件相反的事：
 *  - 同样的内容要占更少的纸（→ 间距小、每页塞更多题、页数少）；
 *  - 纸面上不要有大块空白（→ 间距大、把内容铺满）。
 *
 * 只调一个系数必然两头不讨好。所以这里给两个值：排版先用 [base] 排，
 * 排完发现最后一页空着就把间距往 [max] 拉（不增页）。
 * 紧凑档 base 小、max 反而最大 —— 先塞满，再把剩下的缝摊开。
 *
 * [max] 在 v0.17.0 里从 13~20 提到 24~32：原来四档等差数列太粗，
 * 52 道口算最多只能填到 83%，纸尾白白空 110pt。现在放大到「把纸铺满」
 * 才够用的区间，具体落点由 PaperRenderer 二分试排决定，不是固定步长。
 */
enum class LineGap(val label: String, val base: Float, val max: Float) {
    COMPACT("紧凑", 2f, 32f),
    NORMAL("标准", 4f, 26f),
    LOOSE("宽松", 7f, 34f),
    ;

    companion object {
        fun of(name: String?): LineGap = values().firstOrNull { it.name == name } ?: NORMAL
    }
}

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
    /** 题型倾向：决定这份卷以哪类题为主 */
    val tilt: Tilt = Tilt.BALANCED,
    /** 卷面用途：日常巩固 / 单元检测 / 综合复习，决定铺多宽 */
    val purpose: PaperPurpose = PaperPurpose.DAILY,
    /** 纸张方向：横版时 PDF 页面是 842×595pt，系统打印框也按横版 A4 匹配 */
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    /**
     * 按单元出卷：非空时课标题库只从这些知识点里抽题。
     * 空集合 = 不限制（正常按年级出卷）。
     */
    val topicIds: Set<String> = emptySet(),

    // ---------------- v0.17.0 卷面定制 ----------------

    /**
     * 标题与「共 N 题 满分 M 分」排成一行。
     *
     * 两行时卷头占 26 + 17 = 43pt，一行只要 26pt —— 省下的 17pt 在 A4 竖版上
     * 差不多是一行口算的位置。可打印区域就这么高，卷头每省一点，正文就多一点。
     */
    val headerOneLine: Boolean = false,
    /**
     * 页脚文字。null = 用默认那句；空串 = **整条页脚都不印**（连分隔线一起去掉）。
     *
     * 用可空而不是空串表示默认，是因为「不印页脚」是真实需求：
     * 页脚占 26pt 高度，去掉之后正文能多排一行。
     */
    val footerText: String? = null,
    /** 页脚右侧印生成时间（家长要能看出这是哪天的卷子） */
    val showCreatedAt: Boolean = true,
    /** 题间行距档位 */
    val lineGap: LineGap = LineGap.NORMAL,
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
