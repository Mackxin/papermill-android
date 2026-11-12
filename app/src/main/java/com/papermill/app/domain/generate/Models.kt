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
}

enum class AnswerMode(val label: String) {
    NONE("不带答案"),
    LAST_PAGE("附答案页"),
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
    /** 错题重练卷：题号右上角/标题上要标明，且默认附答案 */
    val isMistakePaper: Boolean = false,
)

data class GeneratedPaper(
    val spec: PaperSpec,
    val sections: List<GeneratedSection>,
    val questions: List<GeneratedQuestion>,
    val createdAt: Long,
)

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
