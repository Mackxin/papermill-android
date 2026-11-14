package com.papermill.app.domain.generate

import java.util.Random

/**
 * v0.5.0 英语题型：单词拼装 / 连词成句 / 选词填空。
 *
 * ============================================================
 * 来源与版权边界
 * ============================================================
 * 只借鉴「有哪些题型」这一层方法思路（把字母打乱重排、把词序打乱重排、
 * 给一句话挖空让选词填入），这些属于练习形式，不构成受保护的表达。
 *
 * 本文件里的**每一个单词、每一个句子、每一个选项都是自编**的，
 * 单词复用 App 早就有的 [WordBank]，句子和选项写在下面的 [SentenceBank] /
 * [ClozeBank]。没有复制 magic-english-buddy 的任何代码、课文、词表或提示词文本
 * —— 那个项目是 CC BY-NC-SA 4.0，禁止商用且有传染性。
 *
 * ============================================================
 * 为什么这三种题型适合「打印」
 * ============================================================
 * 同一个项目里的「听音辨图」被刻意跳过了：纸质卷子播不了声音。
 * 这三种都能写成一行题干 + 一条答题位，答案页自动附上，正好排满一页纸。
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

// ------------------------------------------------------------------ 自编分级句库

data class EnSentence(val text: String, val zh: String, val minGrade: Int)

object SentenceBank {
    private val all = listOf(
        EnSentence("This is a cat.", "这是一只猫。", 3),
        EnSentence("I like apples.", "我喜欢苹果。", 3),
        EnSentence("The dog is big.", "这只狗很大。", 3),
        EnSentence("She has a pen.", "她有一支钢笔。", 3),
        EnSentence("We go to school.", "我们去上学。", 3),
        EnSentence("It is a red book.", "这是一本红色的书。", 3),
        EnSentence("My name is Tom.", "我叫汤姆。", 3),
        EnSentence("The cat is on the desk.", "猫在桌子上。", 4),
        EnSentence("My friend is at home.", "我的朋友在家。", 4),
        EnSentence("We play football after school.", "放学后我们踢足球。", 4),
        EnSentence("The weather is fine today.", "今天天气很好。", 4),
        EnSentence("There are two books on the table.", "桌上有两本书。", 5),
        EnSentence("I went to the library yesterday.", "我昨天去了图书馆。", 5),
        EnSentence("She is reading an interesting story.", "她正在读一个有趣的故事。", 5),
        EnSentence("My family will visit the village.", "我们一家要去参观那个村庄。", 6),
        EnSentence("Because it rained, we stayed home.", "因为下雨，我们待在家里。", 6),
        EnSentence("Please remember to bring your homework.", "请记得带上作业。", 6),
        EnSentence("I have finished my homework already.", "我已经完成作业了。", 7),
        EnSentence("The science lesson was very interesting.", "那节科学课非常有意思。", 7),
        EnSentence("Knowledge comes from practice.", "知识来自实践。", 7),
        EnSentence("If you work hard, you will make progress.", "努力就会进步。", 8),
        EnSentence("The teacher encouraged us to ask questions.", "老师鼓励我们提问。", 8),
        EnSentence("We should pay attention to the environment.", "我们应该关注环境。", 8),
        EnSentence("Technology has changed the way we learn.", "科技改变了我们的学习方式。", 9),
        EnSentence("It is important to make a careful decision.", "谨慎做决定很重要。", 9),
    )

    fun forGrade(grade: Int): List<EnSentence> = all.filter { it.minGrade <= grade }
}

// ------------------------------------------------------------------ 自编分级语法题库

data class ClozeItem(val stem: String, val options: List<String>, val answer: String, val minGrade: Int)

object ClozeBank {
    private val all = listOf(
        ClozeItem("___ is my book.", listOf("A. This", "B. These", "C. Those"), "A", 3),
        ClozeItem("I ___ a student.", listOf("A. am", "B. is", "C. are"), "A", 3),
        ClozeItem("She ___ a new pen.", listOf("A. have", "B. has", "C. having"), "B", 3),
        ClozeItem("The cat is ___ the box.", listOf("A. in", "B. of", "C. at"), "A", 3),
        ClozeItem("___ you like oranges?", listOf("A. Do", "B. Does", "C. Are"), "A", 4),
        ClozeItem("There ___ two birds in the tree.", listOf("A. is", "B. are", "C. be"), "B", 4),
        ClozeItem("He ___ to school every day.", listOf("A. go", "B. goes", "C. going"), "B", 4),
        ClozeItem("This book is ___ than that one.", listOf("A. good", "B. better", "C. best"), "B", 4),
        ClozeItem("We ___ football last Sunday.", listOf("A. play", "B. plays", "C. played"), "C", 5),
        ClozeItem("She is ___ than her brother.", listOf("A. tall", "B. taller", "C. tallest"), "B", 5),
        ClozeItem("I have ___ finished my homework.", listOf("A. yet", "B. already", "C. still"), "B", 6),
        ClozeItem("___ it rained, we stayed at home.", listOf("A. Because", "B. Although", "C. Unless"), "A", 6),
        ClozeItem("He asked me ___ I liked the story.", listOf("A. that", "B. if", "C. what"), "B", 6),
        ClozeItem("The room ___ by the students yesterday.", listOf("A. cleans", "B. cleaned", "C. was cleaned"), "C", 7),
        ClozeItem("This is the book ___ I borrowed.", listOf("A. who", "B. which", "C. whom"), "B", 7),
        ClozeItem("If it ___ tomorrow, we will stay home.", listOf("A. rain", "B. rains", "C. rained"), "B", 8),
        ClozeItem("We should pay attention ___ the details.", listOf("A. to", "B. on", "C. for"), "A", 8),
        ClozeItem("She speaks English ___ than I do.", listOf("A. fluent", "B. fluently", "C. more fluently"), "C", 8),
        ClozeItem("Hard work leads ___ progress.", listOf("A. to", "B. in", "C. at"), "A", 9),
        ClozeItem("Not only the students ___ the teacher was surprised.", listOf("A. and", "B. but also", "C. or"), "B", 9),
    )

    fun forGrade(grade: Int): List<ClozeItem> = all.filter { it.minGrade <= grade }
}

// ------------------------------------------------------------------ 单词拼装

object WordScrambleGenerator : BaseGenerator() {
    override val type = QuestionType.WORD_SCRAMBLE
    override val subject = Subject.ENGLISH
    override val minGrade = 3
    override val topic = "字母拼装"

    override fun make(ctx: GenContext): Pair<String, String> {
        val pool = WordBank.englishFor(ctx.grade)
            .filter { it.en.length in 4..10 && it.en.all { ch -> ch.isLetter() } }
        if (pool.isEmpty()) return "" to ""
        val w = pool[ctx.rng.nextInt(pool.size)]
        val letters = shuffled(w.en.toList(), ctx.rng)
        if (letters.joinToString("") == w.en && letters.size > 1) {
            val t = letters[0]
            letters[0] = letters[1]
            letters[1] = t
        }
        val display = letters.joinToString(" ")
        return when (ctx.rng.nextInt(3)) {
            0 -> "重排字母组成单词：$display →（　　）" to w.en
            1 -> "字母被打乱了：$display，写出原词（　　）" to w.en
            else -> "重排字母写出「${w.zh}」：$display（　　）" to w.en
        }
    }
}

// ------------------------------------------------------------------ 连词成句

object SentenceOrderGenerator : BaseGenerator() {
    override val type = QuestionType.SENTENCE_ORDER
    override val subject = Subject.ENGLISH
    override val minGrade = 3
    override val topic = "连词成句"

    override fun make(ctx: GenContext): Pair<String, String> {
        val pool = SentenceBank.forGrade(ctx.grade)
        val s = pool[ctx.rng.nextInt(pool.size)]
        val body = s.text.removeSuffix(".").split(" ")
        val words = shuffled(body, ctx.rng)
        if (words == body && words.size > 1) {
            val t = words[0]
            words[0] = words[1]
            words[1] = t
        }
        val display = words.joinToString(" / ") + " ."
        return when (ctx.rng.nextInt(2)) {
            0 -> "连词成句：$display（　　）" to s.text
            else -> "连词成句（${s.zh}）：$display（　　）" to s.text
        }
    }
}

// ------------------------------------------------------------------ 选词填空

object ClozeGenerator : BaseGenerator() {
    override val type = QuestionType.CLOZE
    override val subject = Subject.ENGLISH
    override val minGrade = 3
    override val topic = "语法选择"

    override fun make(ctx: GenContext): Pair<String, String> {
        val pool = ClozeBank.forGrade(ctx.grade)
        val item = pool[ctx.rng.nextInt(pool.size)]
        val options = item.options.joinToString("　")
        return when (ctx.rng.nextInt(2)) {
            0 -> "选择正确答案：${item.stem}　$options（　　）" to item.answer
            else -> "单项选择：${item.stem}　$options（　　）" to item.answer
        }
    }
}
