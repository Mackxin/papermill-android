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
    /**
     * 纸尾空一大块时，允不允许「多画几条答题横线」把它填满（v0.18.2）。
     *
     * 对竖式这类题型是好事：题干就一个算式，多一条线正好多写一道竖式。
     * 对应用题是坏事：题干占掉半行，底下再排 5 条空横线，整页看着像草稿本 ——
     * 用户的原话是「不用空那么多行」。所以应用题这里关掉，宁可纸尾留白。
     */
    val padWithLines: Boolean = true,
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
    // v0.18.2：应用题从「单栏 + 4 条答题线」改成「可两栏 + 3 条线」。
    // 用户的原话是「这个不用空那么多行」，配的图上 5 道应用题占满一整页、
    // 每题下面 4 条横线之间还拉得很开 —— 一行只印一道题，纸全浪费在空白上。
    // 小学应用题题干普遍 15~25 字，半栏宽（约 247pt）刚好一行放得下；
    // 3 条线够写「列式 + 答」，不够时孩子会自己往下写，比印 4 条空的更省纸。
    // padWithLines = false：纸尾有空白也不给它加线，那正是「空那么多行」的来源。
    WORD_PROBLEM("应用题", 2, 3, padWithLines = false),

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
    // v0.17.1：answerLines 0 → 1。原来答案位是题干末尾一对全角括号「（　　）」，
    // 一个 4~10 个字母的单词挤在两个空格里，孩子写出来是糊的；换成一条书写横线，
    // 顺带把这一大题的纸尾空白填掉一部分（15 道题原来空 380pt）
    WORD_SCRAMBLE("单词拼装", 2, 1, "把打乱的字母重新排好，写出正确的单词，拼写要完整。"),
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

    /**
     * 自编题（v0.19.0）：内容完全由用户自己给，见 [CustomSheet]。
     *
     * 这个题型**不出现在题型库里**（[com.papermill.app.domain.generate.GeneratorRegistry]
     * 没有它的生成器）—— 它没有「随机出题」这回事，题就是你写的那些行。
     * 声明在枚举里只是为了复用大题标题、分值、栏数、答题线这一整套排版规则，
     * 让自编出来的大题和内置题型长得一模一样。
     */
    CUSTOM("自编题", 2, 1),
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
            CURRIC_BLANK, SCIENCE_BLANK, CUSTOM,
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
    /**
     * 大题名（不含「一、」和「共 N 分」）。删题后要重印标题，只能靠它复原 ——
     * 自编题的名字是用户起的（「本周生词」），照 [type] 重算会变回「自编题」。
     */
    val name: String = "",
    /** 每题分值：自编题可以让用户自己定（抄写 5 分、口算 2 分） */
    val points: Int = 2,
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
    // v0.18.1：max（撑开上限）整体压下来。
    // 原来 26~34pt 意味着「为了填满一页，题与题之间能拉开一个半行」，
    // 用户看到的就是「行与行之间太宽」。压到 5~26 之后，
    // 纸没满时优先靠**多补几道题**去填，而不是把行距撑开。
    TIGHT("极窄", 0f, 5f),
    COMPACT("紧凑", 1.5f, 9f),
    NORMAL("标准", 3.5f, 15f),
    LOOSE("宽松", 7f, 26f),

    /**
     * 自定义：用 [PaperSpec.lineGapCustom] 里的具体 pt 值。
     *
     * base 与 max 取同一个数 —— 「自定义 = 我就要这么宽」，不该再被撑开算法
     * 偷偷拉大。想让它参与撑开的人用上面三档去调。
     */
    CUSTOM("自定义", 0f, 0f),
    ;

    companion object {
        fun of(name: String?): LineGap = values().firstOrNull { it.name == name } ?: NORMAL
    }
}

/**
 * 页脚高度档位（v0.18.1）。
 *
 * 用户说「高度可以自定义」—— 页脚那一条占多少纸面高度是可以调的：
 * 想要更紧凑就选「矮」，正文多吃 6pt；觉得那行小字看不清就选「高」，
 * 字号从 7.5 提到 9pt。**这个高度直接决定正文可用高度**，
 * 所以它是一个排版参数，而不只是外观。
 */
enum class FooterSize(val label: String, val height: Float, val fontSize: Float) {
    SHORT("矮", 20f, 6.5f),
    NORMAL("标准", 26f, 7.5f),
    TALL("高", 32f, 9f),
    ;

    companion object {
        fun of(name: String?): FooterSize = values().firstOrNull { it.name == name } ?: NORMAL
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
    /** 页脚右侧印「第 N / M 页」：每一页都印，包括第一页 */
    val showPageNo: Boolean = true,
    /** 页脚高度档位：直接决定正文可用高度 */
    val footerSize: FooterSize = FooterSize.NORMAL,
    /** 题间行距档位 */
    val lineGap: LineGap = LineGap.NORMAL,
    /** 行距选「自定义」时用的具体 pt 值（0~24） */
    val lineGapCustom: Float = 8f,

    // ---------------- v0.18.3 页数控制 ----------------

    /**
     * 正文页数上限。0 = 不限制，按题量自然分页。
     *
     * 用户的原话是「可以选择试卷生成几页，这样的话题目可以根据试卷自动压缩
     * 显示的内容大小」—— 意思是「我只准这张卷印两页，题多你自己压缩塞进去」。
     *
     * 语义是**上限**不是定值：题少时自然少页，不硬撑到 N 页（硬撑只能靠放大字号
     * 或拉行距，两种都会让卷子看起来像排版出错）。答案页不计入这个上限。
     *
     * 实现在 PaperRenderer：拿不到就二分缩小正文字号，下限 [MIN_TYPE_SCALE]。
     */
    val targetPages: Int = 0,

    // ---------------- v0.19.0 学生档案 / 学期 / 自编题 ----------------

    /**
     * 学期：1 上册 / 2 下册 / 0 不限。
     *
     * 只对**真有学期数据**的资源生效：课标题库的知识点带 `semester` 字段，
     * 课本目录带册次；口算、字词这类按年级分段的资源没有学期概念，
     * 选上册并不会把口算变少 —— 那种「选了没反应」的假开关比没有更糟，
     * 所以界面上写明「影响哪些资源」。
     */
    val term: Int = 1,
    /** 卷头姓名栏里直接印出来的学生名；空 = 留空线自己写 */
    val studentName: String = "",
    /** 自编题：内容由用户自己给，按顺序接在常规大题后面（v0.19.0） */
    val customSheets: List<CustomSheet> = emptyList(),
)

/** 学期文案：上册 / 下册 / 不限。集中一处，UI 和标题都读它 */
object Term {
    const val BOTH = 0
    const val FIRST = 1
    const val SECOND = 2

    fun label(term: Int): String = when (term) {
        FIRST -> "上册"
        SECOND -> "下册"
        else -> "全学年"
    }

    private val CN = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九")

    /** 卷标题 / 学生胶囊上的短后缀：「三上」「三下」。用汉字数字，幼儿园家长也一眼看懂 */
    fun short(grade: Int, term: Int): String {
        val g = CN.getOrElse(grade - 1) { grade.toString() }
        return when (term) {
            FIRST -> "${g}上"
            SECOND -> "${g}下"
            else -> "${g}年级"
        }
    }
}

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
