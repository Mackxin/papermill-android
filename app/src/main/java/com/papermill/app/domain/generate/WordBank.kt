package com.papermill.app.domain.generate

import com.papermill.app.domain.content.RefBank

/**
 * 内置字词表（语文 / 英语）。
 *
 * 全部是通用常用词，不涉及任何教材原文；按 minGrade 过滤，
 * 低年级不会蹦出「磅礴」这种超纲词。词表放在 APK 里，完全离线。
 *
 * 每个年级能拿到的词 = 所有 minGrade <= 当前年级的词（从本年级起点轮换），
 * 这样高年级可用词更多，也不会因为词表太短导致「要 30 题实际只出 8 题」。
 */
data class CnWord(val word: String, val pinyin: String, val minGrade: Int)
data class EnWord(val en: String, val zh: String, val minGrade: Int)

object WordBank {

    /**
     * 手写核心表（80 个词 / 42 个单词）。**永远排在最前面**，下标不动。
     *
     * v0.28.0 起它不再是全部：真正的词源是 assets 里的 refs/cn_words.json
     * （3,652 个词，见 [com.papermill.app.domain.content.RefBank]）。
     * 之所以保留这批并固定在最前，是因为 [chineseFor] 的轮换偏移按「本年级第一个新词」
     * 在原表里的下标算 —— 一旦这批词的相对位置变了，同一份 spec + 同一个 seed
     * 出的就是另一张卷，跟网页版也对不上。
     */

    private val chinese = listOf(
        CnWord("太阳", "tài yáng", 1),
        CnWord("月亮", "yuè liang", 1),
        CnWord("花朵", "huā duǒ", 1),
        CnWord("小鸟", "xiǎo niǎo", 1),
        CnWord("白云", "bái yún", 1),
        CnWord("同学", "tóng xué", 1),
        CnWord("老师", "lǎo shī", 1),
        CnWord("书本", "shū běn", 1),
        CnWord("朋友", "péng you", 2),
        CnWord("春天", "chūn tiān", 2),
        CnWord("河流", "hé liú", 2),
        CnWord("森林", "sēn lín", 2),
        CnWord("城市", "chéng shì", 2),
        CnWord("天空", "tiān kōng", 2),
        CnWord("观察", "guān chá", 3),
        CnWord("记录", "jì lù", 3),
        CnWord("实验", "shí yàn", 3),
        CnWord("准备", "zhǔn bèi", 3),
        CnWord("骄傲", "jiāo ào", 4),
        CnWord("谦虚", "qiān xū", 4),
        CnWord("敏捷", "mǐn jié", 4),
        CnWord("灿烂", "càn làn", 4),
        CnWord("徘徊", "pái huái", 5),
        CnWord("慷慨", "kāng kǎi", 5),
        CnWord("究竟", "jiū jìng", 5),
        CnWord("蜿蜒", "wān yán", 6),
        CnWord("静谧", "jìng mì", 6),
        CnWord("憧憬", "chōng jǐng", 7),
        CnWord("彷徨", "páng huáng", 7),
        CnWord("睿智", "ruì zhì", 8),
        CnWord("磅礴", "páng bó", 8),
        CnWord("缜密", "zhěn mì", 9),
        CnWord("砥砺", "dǐ lì", 9),
        CnWord("清晨", "qīng chén", 2),
        CnWord("教室", "jiào shì", 2),
        CnWord("海洋", "hǎi yáng", 3),
        CnWord("温暖", "wēn nuǎn", 3),
        CnWord("勇敢", "yǒng gǎn", 4),
        CnWord("诚实", "chéng shí", 4),
        CnWord("辽阔", "liáo kuò", 5),
        CnWord("朴素", "pǔ sù", 5),
        CnWord("凝视", "níng shì", 6),
        CnWord("丰盛", "fēng shèng", 6),
        CnWord("浩瀚", "hào hàn", 7),
        CnWord("沉淀", "chén diàn", 8),
        CnWord("严谨", "yán jǐn", 9),
        // ---- 低年级补充词（v0.20.0）：拼音题按素材去重后一年级只有 8 个词不够出 ----
        // 追加在末尾，前面下标不变，与网页端 CN_WORDS 一一对应
        CnWord("大地", "dà dì", 1),
        CnWord("山水", "shān shuǐ", 1),
        CnWord("火车", "huǒ chē", 1),
        CnWord("汽车", "qì chē", 1),
        CnWord("水果", "shuǐ guǒ", 1),
        CnWord("苹果", "píng guǒ", 1),
        CnWord("西瓜", "xī guā", 1),
        CnWord("铅笔", "qiān bǐ", 1),
        CnWord("书包", "shū bāo", 1),
        CnWord("黑板", "hēi bǎn", 1),
        CnWord("爸爸", "bà ba", 1),
        CnWord("妈妈", "mā ma", 1),
        CnWord("眼睛", "yǎn jing", 1),
        CnWord("小鸡", "xiǎo jī", 1),
        CnWord("小鱼", "xiǎo yú", 1),
        CnWord("树叶", "shù yè", 1),
        CnWord("雨点", "yǔ diǎn", 1),
        CnWord("雪花", "xuě huā", 1),
        CnWord("早上", "zǎo shang", 1),
        CnWord("中午", "zhōng wǔ", 1),
        CnWord("种子", "zhǒng zi", 1),
        CnWord("田野", "tián yě", 1),
        CnWord("公园", "gōng yuán", 2),
        CnWord("校园", "xiào yuán", 2),
        CnWord("操场", "cāo chǎng", 2),
        CnWord("故事", "gù shi", 2),
        CnWord("图画", "tú huà", 2),
        CnWord("音乐", "yīn yuè", 2),
        CnWord("游戏", "yóu xì", 2),
        CnWord("家乡", "jiā xiāng", 2),
        CnWord("农村", "nóng cūn", 2),
        CnWord("麦苗", "mài miáo", 2),
        CnWord("稻谷", "dào gǔ", 2),
    )

    private val english = listOf(
        EnWord("apple", "苹果", 3),
        EnWord("book", "书", 3),
        EnWord("cat", "猫", 3),
        EnWord("dog", "狗", 3),
        EnWord("red", "红色", 3),
        EnWord("pen", "钢笔", 3),
        EnWord("school", "学校", 4),
        EnWord("teacher", "老师", 4),
        EnWord("friend", "朋友", 4),
        EnWord("water", "水", 4),
        EnWord("family", "家庭", 5),
        EnWord("weather", "天气", 5),
        EnWord("holiday", "假期", 5),
        EnWord("library", "图书馆", 5),
        EnWord("because", "因为", 6),
        EnWord("different", "不同的", 6),
        EnWord("important", "重要的", 6),
        EnWord("remember", "记得", 6),
        EnWord("environment", "环境", 7),
        EnWord("experience", "经历", 7),
        EnWord("knowledge", "知识", 7),
        EnWord("science", "科学", 7),
        EnWord("attention", "注意力", 8),
        EnWord("decision", "决定", 8),
        EnWord("encourage", "鼓励", 8),
        EnWord("progress", "进步", 8),
        EnWord("opportunity", "机会", 9),
        EnWord("responsible", "负责的", 9),
        EnWord("technology", "技术", 9),
        EnWord("challenge", "挑战", 9),
        EnWord("window", "窗户", 3),
        EnWord("orange", "橙子", 3),
        EnWord("student", "学生", 4),
        EnWord("morning", "早晨", 4),
        EnWord("village", "村庄", 5),
        EnWord("mountain", "山", 5),
        EnWord("exercise", "练习", 6),
        EnWord("question", "问题", 6),
        EnWord("culture", "文化", 7),
        EnWord("medicine", "药", 8),
        EnWord("industry", "工业", 9),
    )

    /** 核心词 + 参考资料词，按「核心在前、参考在后」拼 — 网页版 CN_WORDS 是同一个顺序 */
    private val chineseAll: List<CnWord>
        get() = if (RefBank.ready && RefBank.cnWords.isNotEmpty()) chinese + RefBank.cnWords else chinese

    private val englishAll: List<EnWord>
        get() = if (RefBank.ready && RefBank.enWords.isNotEmpty()) english + RefBank.enWords else english

    fun chineseFor(grade: Int): List<CnWord> {
        val all = chineseAll
        // 幼小衔接（g=0）没有独立词表：它的拼音识字内容就是一年级上册那批字。
        // 不 coerce 的话 pool 是空的（所有词的 minGrade 都 >= 1），语文卷会一道题都出不来。
        val g = grade.coerceAtLeast(1)
        val pool = all.filter { it.minGrade <= g }
        val firstNew = all.indexOfFirst { it.minGrade == g }
        val offset = if (firstNew > 0) firstNew % pool.size else 0
        return pool.drop(offset) + pool.take(offset)
    }

    fun englishFor(grade: Int): List<EnWord> {
        val all = englishAll
        val pool = all.filter { it.minGrade <= grade }
        val firstNew = all.indexOfFirst { it.minGrade == grade }
        val offset = if (firstNew > 0) firstNew % pool.size else 0
        return pool.drop(offset) + pool.take(offset)
    }

    /** 关于页实时统计用 */
    val chineseCount: Int get() = chineseAll.size
    val englishCount: Int get() = englishAll.size
}
