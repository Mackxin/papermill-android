package com.papermill.app.domain.content

/**
 * 英语语法内置库。
 *
 * ============================================================
 * 来源与版权边界
 * ============================================================
 * 语法规则本身是**语言事实**（第三人称单数加 -s、be 动词三形），
 * 不受著作权保护；这里每条规则都是自己重写的说明文字，
 * 例句全部自编（用最常见的基础词），不取自任何教材或教辅的原句。
 *
 * 一份数据两处用：
 *  1. 「打印纸」页的英语语法速查表（按小学 / 初中分段打印）；
 *  2. 「语法选择」题型：从下面 [items] 里抽题，选项打乱后生成选择题。
 */

/** 语法点：速查表里的一行，包含规则 + 例句 */
data class GrammarPoint(
    val name: String,
    val rule: String,
    val example: String,
    /** 从几年级开始学（速查表按这个分段） */
    val gradeFrom: Int,
)

/**
 * 一道语法题的素材。
 *
 * 刻意不用「选项下标」而用 [answer] 文本 —— 选项要打乱，
 * 存下标一旦洗牌就错位，存答案文本则永远不会算错。
 */
data class GrammarItem(
    val point: String,
    val gradeFrom: Int,
    /** 题干，缺的地方统一用 ___ 占位 */
    val stem: String,
    val options: List<String>,
    val answer: String,
    /** 一句话讲清为什么，直接印在答案页 */
    val explain: String,
)

object Grammar {

    val points: List<GrammarPoint> = listOf(
        GrammarPoint(
            "be 动词",
            "I 用 am，he / she / it 和单数名词用 is，you / we / they 和复数名词用 are。",
            "I am a student. / She is my sister. / They are in the classroom.",
            3,
        ),
        GrammarPoint(
            "人称代词主格",
            "做主语用主格：I / you / he / she / it / we / they。",
            "He is my friend. / We are good at English.",
            3,
        ),
        GrammarPoint(
            "形容词性物主代词",
            "表示「谁的」，后面必须跟名词：my / your / his / her / its / our / their。",
            "This is my book. / Their teacher is very kind.",
            4,
        ),
        GrammarPoint(
            "名词复数",
            "一般加 -s；以 s、x、ch、sh 结尾加 -es；辅音字母 + y 结尾变 y 为 i 再加 -es；foot→feet、child→children 等为不规则变化。",
            "three boxes / two feet / many children",
            3,
        ),
        GrammarPoint(
            "冠词 a / an / the",
            "辅音音素开头用 a，元音音素开头用 an，特指或独一无二的事物用 the。",
            "an apple / a book / the sun",
            3,
        ),
        GrammarPoint(
            "一般现在时（第三人称单数）",
            "主语是 he / she / it 或单数名词时，动词加 -s 或 -es；其余人称用原形。",
            "He goes to school by bus. / My mother cooks dinner at six.",
            4,
        ),
        GrammarPoint(
            "现在进行时",
            "be（am / is / are）+ 动词 -ing，表示此刻正在发生。",
            "The boys are playing football. / I am doing my homework now.",
            4,
        ),
        GrammarPoint(
            "there be 句型",
            "表示「某处有某物」，be 动词与**紧跟其后的第一个名词**保持一致（就近原则）。",
            "There is a book and two pens on the desk. / There are two pens and a book.",
            4,
        ),
        GrammarPoint(
            "一般疑问句",
            "实义动词用 Do / Does 提问，be 动词或情态动词直接提到句首。",
            "Do you like apples? / Does he play basketball?",
            4,
        ),
        GrammarPoint(
            "特殊疑问词",
            "what 问什么、when 问时间、where 问地点、who 问人、how 问方式、why 问原因。",
            "When is your birthday? / How do you go to school?",
            4,
        ),
        GrammarPoint(
            "情态动词 can / must / may",
            "后面一律接动词原形，没有人称变化；must 表必须，may 表允许或请求。",
            "You must finish your homework first. / May I use your pen?",
            4,
        ),
        GrammarPoint(
            "介词 in / on / at",
            "in 用于大范围（月、年、城市），on 用于具体某天或表面，at 用于具体时刻或小地点。",
            "The book is on the desk. / I get up at seven o'clock.",
            4,
        ),
        GrammarPoint(
            "some / any",
            "some 多用于肯定句，any 多用于否定句和疑问句。",
            "I have some water. / Do you have any questions?",
            5,
        ),
        GrammarPoint(
            "可数与不可数名词",
            "不可数名词（bread / milk / water / news）没有复数，也不能直接加 a / an，要用 some 或量词。",
            "There is some bread on the table.",
            5,
        ),
        GrammarPoint(
            "频度副词",
            "always > usually > often > sometimes > never，放在实义动词前、be 动词后。",
            "He is never late for school.",
            5,
        ),
        GrammarPoint(
            "形容词比较级与最高级",
            "两者比较用 -er / more，三者及以上用 -est / most；good→better→best、bad→worse→worst。",
            "Tom is taller than his brother. / This is the best book I have read.",
            6,
        ),
        GrammarPoint(
            "一般过去时",
            "规则动词加 -ed，不规则动词要单独记（go→went、do→did）；时间状语多为 yesterday、last night。",
            "I went to the park yesterday. / She did her homework last night.",
            6,
        ),
        GrammarPoint(
            "一般将来时",
            "will + 动词原形表示将来；be going to 表示有计划或已有迹象。",
            "We will visit the museum tomorrow. / Look at the clouds. It is going to rain.",
            6,
        ),
        GrammarPoint(
            "现在完成时",
            "have / has + 过去分词，表示过去发生且对现在有影响，或从过去持续到现在（常配 since / for）。",
            "I have seen this film twice. / He has lived in Beijing since 2019.",
            8,
        ),
        GrammarPoint(
            "被动语态",
            "be + 过去分词；一般现在时用 am / is / are + 过去分词，一般过去时用 was / were + 过去分词。",
            "The window was broken by Tom yesterday. / English is spoken in many countries.",
            8,
        ),
        GrammarPoint(
            "动词不定式",
            "want / hope / ask / decide 等动词后接 to + 动词原形。",
            "I want to be a doctor. / He asked me to help him.",
            8,
        ),
        GrammarPoint(
            "宾语从句",
            "从句一律用陈述语序（连接词 + 主语 + 谓语），不能用疑问语序；「是否」用 if 或 whether。",
            "Could you tell me where the bank is? / I don't know whether he will come.",
            9,
        ),
    )

    /** 语法题素材：按 gradeFrom 覆盖 3–9 年级 */
    val items: List<GrammarItem> = listOf(
        GrammarItem(
            "be 动词", 3, "I ___ a student.", listOf("am", "is", "are", "be"), "am",
            "主语是 I，be 动词用 am。",
        ),
        GrammarItem(
            "be 动词", 3, "She ___ my sister.", listOf("am", "is", "are", "be"), "is",
            "主语 she 是第三人称单数，用 is。",
        ),
        GrammarItem(
            "be 动词", 3, "They ___ in the classroom.", listOf("am", "is", "are", "be"), "are",
            "主语 they 是复数，用 are。",
        ),
        GrammarItem(
            "人称代词主格", 3, "___ is my friend. （Tom）", listOf("He", "She", "It", "They"), "He",
            "Tom 是男性，做主语用 he，句首大写。",
        ),
        GrammarItem(
            "人称代词主格", 3, "___ are good at English. （Lily and I）", listOf("He", "She", "It", "We"), "We",
            "Lily and I 合起来是「我们」，用 we。",
        ),
        GrammarItem(
            "形容词性物主代词", 4, "This is ___ book. （I）", listOf("my", "me", "I", "mine"), "my",
            "后面跟名词 book，要用形容词性物主代词 my。",
        ),
        GrammarItem(
            "形容词性物主代词", 4, "The bag is ___ . （she）", listOf("her", "hers", "she", "she's"), "hers",
            "句末没有名词，要用名词性物主代词 hers。",
        ),
        GrammarItem(
            "名词复数", 3, "There are three ___ on the desk.", listOf("box", "boxs", "boxes", "boxies"), "boxes",
            "以 x 结尾的名词变复数加 -es。",
        ),
        GrammarItem(
            "名词复数", 3, "I have two ___ .", listOf("foot", "foots", "feet", "feets"), "feet",
            "foot 的复数是不规则变化 feet。",
        ),
        GrammarItem(
            "冠词 a / an / the", 3, "This is ___ apple.", listOf("a", "an", "the", "/"), "an",
            "apple 以元音音素开头，用 an。",
        ),
        GrammarItem(
            "冠词 a / an / the", 3, "Look at ___ sun. It is bright today.", listOf("a", "an", "the", "/"), "the",
            "太阳是独一无二的事物，用 the。",
        ),
        // 三年级可用的语法点只有 be 动词 / 人称代词 / 名词复数 / 冠词，
        // 素材太少会导致「要 12 道题却只能出 9 道」，这里把这四类补充到 19 道。
        GrammarItem(
            "be 动词", 3, "My father ___ a doctor.", listOf("am", "is", "are", "be"), "is",
            "主语 my father 是第三人称单数，用 is。",
        ),
        GrammarItem(
            "be 动词", 3, "We ___ students.", listOf("am", "is", "are", "be"), "are",
            "主语 we 是复数，用 are。",
        ),
        GrammarItem(
            "be 动词", 3, "It ___ a cat.", listOf("am", "is", "are", "be"), "is",
            "主语 it 是第三人称单数，用 is。",
        ),
        GrammarItem(
            "be 动词", 3, "The children ___ happy today.", listOf("am", "is", "are", "be"), "are",
            "the children 是复数，用 are。",
        ),
        GrammarItem(
            "人称代词主格", 3, "___ am a girl.", listOf("I", "You", "He", "They"), "I",
            "只有 I 能和 am 搭配，所以主语是 I。",
        ),
        GrammarItem(
            "人称代词主格", 3, "___ is my teacher. （Ms Wang）", listOf("He", "She", "It", "They"), "She",
            "Ms Wang 是女性，做主语用 she。",
        ),
        GrammarItem(
            "名词复数", 3, "I have three ___ .", listOf("book", "books", "bookes", "bookies"), "books",
            "book 是规则名词，复数直接加 -s。",
        ),
        GrammarItem(
            "名词复数", 3, "Look! Two ___ are flying.", listOf("bird", "birds", "birdes", "birdies"), "birds",
            "bird 是规则名词，复数直接加 -s。",
        ),
        GrammarItem(
            "冠词 a / an / the", 3, "This is ___ orange.", listOf("a", "an", "the", "/"), "an",
            "orange 以元音音素开头，用 an。",
        ),
        GrammarItem(
            "冠词 a / an / the", 3, "I have ___ new pencil.", listOf("a", "an", "the", "/"), "a",
            "new 以辅音音素开头，用 a。",
        ),
        GrammarItem(
            "一般现在时（第三人称单数）", 4, "He ___ to school by bus every day.",
            listOf("go", "goes", "going", "went"), "goes",
            "主语 he 是第三人称单数，一般现在时动词加 -es。",
        ),
        GrammarItem(
            "一般现在时（第三人称单数）", 4, "My mother ___ dinner at six.",
            listOf("cook", "cooks", "cooking", "cooked"), "cooks",
            "主语 my mother 是第三人称单数，动词加 -s。",
        ),
        GrammarItem(
            "现在进行时", 4, "Look! The boys ___ football.", listOf("play", "plays", "are playing", "played"), "are playing",
            "Look! 提示动作正在发生，用 be + 动词 -ing，主语复数用 are。",
        ),
        GrammarItem(
            "现在进行时", 4, "I ___ my homework now.", listOf("do", "does", "am doing", "did"), "am doing",
            "now 表示此刻正在做，主语 I 用 am doing。",
        ),
        GrammarItem(
            "there be 句型", 4, "There ___ a book and two pens on the desk.",
            listOf("is", "are", "have", "has"), "is",
            "就近原则：紧跟 be 的是 a book（单数），所以用 is。",
        ),
        GrammarItem(
            "there be 句型", 4, "There ___ some milk in the glass.", listOf("is", "are", "have", "has"), "is",
            "milk 是不可数名词，按单数处理，用 is。",
        ),
        GrammarItem(
            "一般疑问句", 4, "___ you like apples?", listOf("Do", "Does", "Are", "Is"), "Do",
            "主语 you，实义动词 like，用 Do 提问。",
        ),
        GrammarItem(
            "一般疑问句", 4, "___ he play basketball?", listOf("Do", "Does", "Is", "Are"), "Does",
            "主语 he 是第三人称单数，用 Does，后面动词还原。",
        ),
        GrammarItem(
            "特殊疑问词", 4, "___ is your birthday?", listOf("What", "When", "Who", "Where"), "When",
            "问生日是问时间，用 when。",
        ),
        GrammarItem(
            "特殊疑问词", 4, "___ do you go to school? — By bike.",
            listOf("What", "How", "Where", "Who"), "How",
            "答语 By bike 说的是方式，用 how 提问。",
        ),
        GrammarItem(
            "情态动词 can / must / may", 4, "You ___ finish your homework first.",
            listOf("can", "must", "may", "need"), "must",
            "表示「必须」用 must，后面接动词原形。",
        ),
        GrammarItem(
            "情态动词 can / must / may", 4, "___ I use your pen?", listOf("Must", "May", "Need", "Am"), "May",
            "礼貌地请求允许用 May I…。",
        ),
        GrammarItem(
            "介词 in / on / at", 4, "The book is ___ the desk. （在桌面上）",
            listOf("in", "on", "at", "under"), "on",
            "在物体表面上用 on。",
        ),
        GrammarItem(
            "介词 in / on / at", 4, "I get up ___ seven o'clock.", listOf("in", "on", "at", "for"), "at",
            "具体时刻前用 at。",
        ),
        GrammarItem(
            "some / any", 5, "I have ___ water in my bottle.", listOf("some", "any", "a", "an"), "some",
            "肯定句里用 some。",
        ),
        GrammarItem(
            "some / any", 5, "Do you have ___ questions?", listOf("some", "any", "a", "an"), "any",
            "疑问句里一般用 any。",
        ),
        GrammarItem(
            "可数与不可数名词", 5, "There is some ___ on the table.",
            listOf("apple", "apples", "bread", "breads"), "bread",
            "bread 是不可数名词，没有复数形式。",
        ),
        GrammarItem(
            "频度副词", 5, "He is ___ late for school. （从不）",
            listOf("always", "usually", "never", "sometimes"), "never",
            "「从不」是 never，放在 be 动词之后。",
        ),
        GrammarItem(
            "形容词比较级与最高级", 6, "Tom is ___ than his brother.",
            listOf("tall", "taller", "tallest", "more tall"), "taller",
            "两者比较用比较级 taller，后面跟 than。",
        ),
        GrammarItem(
            "形容词比较级与最高级", 6, "This is the ___ book I have ever read.",
            listOf("good", "better", "best", "well"), "best",
            "三者以上比较用最高级，good 的最高级是 best。",
        ),
        GrammarItem(
            "一般过去时", 6, "I ___ to the park yesterday.", listOf("go", "goes", "went", "going"), "went",
            "yesterday 表示过去，go 的过去式是 went。",
        ),
        GrammarItem(
            "一般过去时", 6, "She ___ her homework last night.", listOf("do", "does", "did", "done"), "did",
            "last night 表示过去，do 的过去式是 did。",
        ),
        GrammarItem(
            "一般将来时", 6, "We ___ visit the museum tomorrow.",
            listOf("will", "are", "were", "did"), "will",
            "tomorrow 表示将来，用 will + 动词原形。",
        ),
        GrammarItem(
            "一般将来时", 6, "Look at the clouds. It ___ rain.",
            listOf("is going to", "will be", "was", "does"), "is going to",
            "已有明显迹象（乌云）时用 be going to。",
        ),
        GrammarItem(
            "现在完成时", 8, "I ___ this film twice.",
            listOf("see", "saw", "have seen", "will see"), "have seen",
            "twice 强调到现在为止的经历，用 have + 过去分词。",
        ),
        GrammarItem(
            "现在完成时", 8, "He ___ in Beijing since 2019.",
            listOf("live", "lived", "has lived", "is living"), "has lived",
            "since + 时间点表示持续到现在，主语 he 用 has lived。",
        ),
        GrammarItem(
            "被动语态", 8, "The window ___ by Tom yesterday.",
            listOf("broke", "was broken", "is broken", "breaks"), "was broken",
            "窗户是被打破的，过去时被动语态 was + 过去分词。",
        ),
        GrammarItem(
            "被动语态", 8, "English ___ in many countries.",
            listOf("speaks", "is spoken", "spoke", "speaking"), "is spoken",
            "英语是被说的，一般现在时被动语态 is + 过去分词。",
        ),
        GrammarItem(
            "动词不定式", 8, "I want ___ a doctor.",
            listOf("be", "to be", "being", "been"), "to be",
            "want 后面接 to + 动词原形。",
        ),
        GrammarItem(
            "动词不定式", 8, "He asked me ___ him.",
            listOf("help", "to help", "helping", "helped"), "to help",
            "ask sb. to do sth. 是固定搭配。",
        ),
        GrammarItem(
            "宾语从句", 9, "Could you tell me ___ ?",
            listOf("where is the bank", "where the bank is", "where the bank was", "the bank where is"),
            "where the bank is",
            "宾语从句必须用陈述语序：连接词 + 主语 + 谓语。",
        ),
        GrammarItem(
            "宾语从句", 9, "I don't know ___ he will come or not.",
            listOf("that", "if", "what", "whether"), "whether",
            "后面有 or not 时只能用 whether。",
        ),
    )

    fun itemsFor(grade: Int): List<GrammarItem> = items.filter { it.gradeFrom <= grade }

    /** 速查表按年级段切，小学 / 初中各一张 */
    fun pointsFor(minGrade: Int, maxGrade: Int): List<GrammarPoint> = points.filter {
        it.gradeFrom in minGrade..maxGrade
    }
}
