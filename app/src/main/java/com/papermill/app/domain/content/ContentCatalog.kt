package com.papermill.app.domain.content

import com.papermill.app.domain.generate.GeneratorRegistry
import com.papermill.app.domain.generate.ThemeBank
import com.papermill.app.domain.generate.WordBank
import com.papermill.app.domain.reference.PracticeData
import com.papermill.app.domain.reference.ReferenceData

/**
 * 内置内容总览。
 *
 * 「关于」页不再手写一串会过期的数字 —— 这里的每一项都是**当场从数据源算出来的**，
 * 题库扩容、题型新增、词表加词之后，页面上的数字自动跟着变，不需要人去改文案。
 *
 * 这也是「内置了什么」的说明书：用户能一眼看清 App 里到底带了哪些内容、
 * 各自多少条、来源边界在哪。
 */
data class CatalogEntry(
    /** 这一栏叫什么 */
    val label: String,
    /** 数量 / 规模，如「48 个」 */
    val size: String,
    /** 一句人话说明，讲清这批内容是什么、从哪来 */
    val note: String,
)

object ContentCatalog {

    /**
     * @param bankSize 内置自编题库条数（需要 Context 读 assets，由 AppState 传进来）
     */
    fun entries(bankSize: Int): List<CatalogEntry> = listOf(
        CatalogEntry(
            "出卷题型",
            "${GeneratorRegistry.count} 种",
            "口算、竖式、应用题、拼音、单词、音标、语法……每个题型都是独立生成器，可任意组合题量。",
        ),
        CatalogEntry(
            "自编题库",
            "$bankSize 道",
            "按知识点自编的选择 / 判断题，选项洗牌后出题；不是任何教辅或题库的原题。",
        ),
        CatalogEntry(
            "英语音标",
            "${Phonics.all.size} 个",
            "${Phonics.vowels.size} 元音 + ${Phonics.consonants.size} 辅音，国际通用记音符号（IPA），" +
                "每个音素配 3 个基础例词与中文发音提示。",
        ),
        CatalogEntry(
            "英语语法",
            "${Grammar.points.size} 个语法点",
            "be 动词、三单、时态、比较级、被动语态、宾语从句……" +
                "规则说明重写，${Grammar.items.size} 道配套选择题，答案页带一句「为什么」。",
        ),
        CatalogEntry(
            "课本同步·生字",
            "${Textbook.hanzi.size} 字",
            "1–9 年级常用字，逐字配拼音。不含任何教材课文原文，只是「这个年级该会这些字」。",
        ),
        CatalogEntry(
            "课本同步·单词",
            "${Textbook.words.size} 词",
            "3–9 年级英语核心词，配中文释义；可抽测（给中文写单词），也可整表打印。",
        ),
        CatalogEntry(
            "课本同步·知识点",
            "${Textbook.mathPoints.size} 条",
            "1–9 年级数学知识点标题（如「分数的意义与性质」），不含讲解文字与例题。",
        ),
        CatalogEntry(
            "语文词库",
            "${WordBank.chineseCount} 词 + ${ThemeBank.allWords.size} 主题词",
            "常用词带拼音，另加 6 大生活场景的主题识字词（超市 / 医院 / 公园 / 学校 / 动物园 / 交通）。",
        ),
        CatalogEntry(
            "成语库",
            "${Idioms.items.size} 条",
            "常用四字成语，释义全部用自己的一句话重写；成语填空与释义选择题从这里随机抽，库越大题越多。",
        ),
        CatalogEntry(
            "古诗库",
            "${Poems.items.size} 首 · ${Poems.items.sumOf { it.lines.size }} 句",
            "小学常见背诵篇目原文（唐宋明清为主，均属公有领域），只收原文、作者、朝代，" +
                "不收录今人注释与译文；接句与常识题从这里随机抽。",
        ),
        CatalogEntry(
            "英语词库",
            "${WordBank.englishCount} 词",
            "按年级分级的基础词，用于单词默写与拼装题型。",
        ),
        CatalogEntry(
            "课标题库",
            "${CurriculumBank.totalQuestions()} 道 · ${CurriculumBank.totalTopics()} 个知识点",
            "1–9 年级五科知识点配套题（数学 / 语文 / 英语 / 物理 / 化学），" +
                "来自开源项目 cn-math-taxonomy，许可是 CC BY-NC 4.0（署名 + 不得商用）。" +
                "已裁到 1–9 年级，剔除了依赖配图、印出来没法做的题；" +
                "出题时选项会洗牌并同步换算答案。注意：许可是非商业，本 App 一旦商业化必须删掉这批题。",
        ),
        CatalogEntry(
            "课本单元",
            "${CurriculumBank.totalUnits()} 个单元 · ${TextbookCatalog.size} 册教材",
            "按教材章节把知识点归成单元，可在「课本」页按单元直接出卷（孩子学到哪一课就练哪一课）。" +
                "教材原文只放链接：原仓库没有开源许可、教材版权属出版社，" +
                "App 不打包也不转发任何一页 PDF，点了用浏览器去原站看。",
        ),
        CatalogEntry(
            "速查表",
            "${ReferenceData.sheets.size} 张",
            "乘法表、拼音表、音标表、语法表、公式表、动词表、课本同步表，全部 Canvas 自绘排版。",
        ),
        CatalogEntry(
            "练习纸",
            "${PracticeData.sheets.size + PracticeData.themeSheets.size} 张",
            "田字格 / 米字格 / 四线格 / 方格 / 横条 / 点阵 / 坐标纸，" +
                "外加 ${PracticeData.themeSheets.size} 张主题识字卡（描红 / 默写两种）。",
        ),
    )

    /** 头部三个大数字，首页与关于页共用 */
    fun headline(bankSize: Int): Triple<String, String, String> = Triple(
        "${GeneratorRegistry.count}",
        "${ReferenceData.sheets.size + PracticeData.sheets.size + PracticeData.themeSheets.size}",
        "${Textbook.hanzi.size + Textbook.words.size + Phonics.all.size}",
    )
}
