package com.papermill.app.domain.generate

import java.util.Random

/**
 * v0.5.0 主题识字题型：看拼音写词语 / 找不同类 / 归类填空。
 *
 * 题目全部由 [ThemeBank] 现场组装，答案在组装过程中同步算出
 * （选项重排后正确字母跟着换算），不会印出「选项换了、答案没换」的错题。
 */

private fun <T> shuffled(src: List<T>, rng: Random): MutableList<T> {
    val a = ArrayList(src)
    for (i in a.size - 1 downTo 1) {
        val j = rng.nextInt(i + 1)
        val t = a[i]
        a[i] = a[j]
        a[j] = t
    }
    return a
}

private fun letterOf(index: Int): String = ('A'.code + index).toChar().toString()

/** 只保留归属唯一的词，避免「水池」同时属于公园和动物园这种歧义题 */
private val uniqueThemeWords: List<Pair<ThemeWord, Theme>> by lazy {
    ThemeBank.themes.flatMap { theme -> theme.words.map { it to theme } }
        .groupBy { it.first.word }
        .values
        .filter { group -> group.distinctBy { it.second.id }.size == 1 }
        .map { it.first() }
}

// ------------------------------------------------------------------ 看拼音写词语

object ThemePinyinGenerator : BaseGenerator() {
    override val type = QuestionType.THEME_PINYIN
    override val subject = Subject.CHINESE
    override val maxGrade = 6
    override val topic = "主题识字"

    override fun make(ctx: GenContext): Pair<String, String> {
        val group = ThemeBank.themes[ctx.rng.nextInt(ThemeBank.themes.size)]
        val list = group.words
        val w = list[ctx.rng.nextInt(list.size)]
        return when (ctx.rng.nextInt(3)) {
            0 -> "【${group.name}】看拼音写词语：${w.pinyin} →（　　）" to w.word
            1 -> "【${group.name}】${w.pinyin}，写出词语（　　）" to w.word
            else -> "读拼音写词语（${group.name}）：${w.pinyin}（　　）" to w.word
        }
    }
}

// ------------------------------------------------------------------ 找不同类

object ThemeSortGenerator : BaseGenerator() {
    override val type = QuestionType.THEME_SORT
    override val subject = Subject.CHINESE
    override val maxGrade = 6
    override val topic = "词语归类"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val group = ThemeBank.themes[rng.nextInt(ThemeBank.themes.size)]
        val same = shuffled(group.words.map { it.word }, rng).take(2)
        val groupWords = group.words.map { it.word }.toSet()

        val others = ThemeBank.themes
            .filter { it.id != group.id }
            .flatMap { it.words.map { w -> w.word } }
            .filter { it !in same && it !in groupWords }
            .distinct()
        if (others.isEmpty()) return "" to ""
        val odd = others[rng.nextInt(others.size)]

        // 选项洗牌 + 正确答案字母同步换算
        val items = shuffled(listOf(same[0] to false, same[1] to false, odd to true), rng)
        val options = items.mapIndexed { i, (word, _) -> "${letterOf(i)}. $word" }
        val answer = letterOf(items.indexOfFirst { it.second })
        val stem = "【${group.name}】下面哪个词不属于这里？　${options.joinToString("　")}（　　）"
        return stem to answer
    }
}

// ------------------------------------------------------------------ 归类填空

object ThemeGroupGenerator : BaseGenerator() {
    override val type = QuestionType.THEME_GROUP
    override val subject = Subject.CHINESE
    override val maxGrade = 6
    override val topic = "场景归类"

    override fun make(ctx: GenContext): Pair<String, String> {
        val rng = ctx.rng
        val (word, correctTheme) = uniqueThemeWords[rng.nextInt(uniqueThemeWords.size)]
        val wrong = shuffled(
            ThemeBank.themes.filter { it.id != correctTheme.id }.map { it.name },
            rng,
        ).take(2)

        val items = shuffled(listOf(correctTheme.name to true) + wrong.map { it to false }, rng)
        val options = items.mapIndexed { i, (name, _) -> "${letterOf(i)}. $name" }
        val answer = letterOf(items.indexOfFirst { it.second })
        val stem = "「${word.word}」最可能出现在哪里？　${options.joinToString("　")}（　　）"
        return stem to answer
    }
}
