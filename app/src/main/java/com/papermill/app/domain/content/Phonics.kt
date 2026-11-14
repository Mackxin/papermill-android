package com.papermill.app.domain.content

/**
 * 英语国际音标（DJ / 英式 48 音素）。
 *
 * ============================================================
 * 来源与版权边界
 * ============================================================
 * 音标符号本身是国际语音学界的**通用记音符号**（IPA），不是任何教材的表达；
 * 48 音素的分类（20 元音 + 28 辅音）是英语语音学的基础事实。
 * 例词全部选用最常见的基础词汇（book / cat / tree 这类），
 * 不取自任何教材的单词表，也不复制任何教辅的例词编排。
 *
 * 这份数据同时供三处使用：
 *  1. 「打印纸」页的国际音标速查表（整表 A4 打印）；
 *  2. 「音标辨认」题型：给一个单词，从四个音标里选出它含有的那个；
 *  3. 「看音标写单词」题型：给音标 + 中文，写出单词。
 *
 * ============================================================
 * 字形覆盖：已逐字符验证，用标准 IPA 记法
 * ============================================================
 * 音标不是普通英文，ː ɪ ʊ ɑ ɜ ɒ ʃ ʒ θ ð ŋ 这些符号在部分字体里会缺字形，
 * 印出来变成方框或干脆消失 —— 一张「长短元音不分」的音标表比没有还糟。
 *
 * 所以做完这张表专门验证过一次：从模拟器拉出 /system/fonts/Roboto-Regular.ttf
 * （PDF 用的就是 sans-serif），用 fontTools 逐个查 cmap —— 上列符号**全部有字形**。
 * 再用 PDFKit 把导出的 PDF 以 1800px 渲染回来看，ː 清楚可辨、/iː/ 与 /ɪ/ 不会混淆。
 *
 * 结论：可以放心用标准 IPA（国内教材也是这么印的），不需要退化成 /i:/ 这种冒号写法。
 * 注意：预览页的位图分辨率低，放大看截图容易误判成「符号丢了」——
 * 要判断印刷效果，得直接渲染导出的 PDF，而不是看 App 内预览截图。
 */

/** 音素大类，决定速查表怎么分组、题目怎么出 */
enum class PhonemeKind(val label: String) {
    SHORT_VOWEL("短元音"),
    LONG_VOWEL("长元音"),
    DIPHTHONG("双元音"),
    PLOSIVE("爆破音"),
    FRICATIVE("摩擦音"),
    AFFRICATE("破擦音"),
    NASAL("鼻音"),
    LATERAL("舌侧音"),
    SEMI_VOWEL("半元音"),
    ;
    val isVowel: Boolean
        get() = this == SHORT_VOWEL || this == LONG_VOWEL || this == DIPHTHONG
}

/**
 * @param ipa 音标符号，带斜杠，形如 `/iː/`
 * @param examples 含该音素的例词（全部为最基础的高频词）
 * @param zh 首个例词的中文意思。「看音标写单词」要靠它把答案锁死，
 *           只给音标的话 /iː/ 对应 see / tea / green 三个词都算对，没法自动判分
 * @param hint 一两句发音提示，用中文讲清口型，零基础也能对上
 */
data class Phoneme(
    val ipa: String,
    val kind: PhonemeKind,
    val examples: List<String>,
    val zh: String,
    val hint: String,
)

object Phonics {

    private fun p(ipa: String, kind: PhonemeKind, zh: String, hint: String, vararg ex: String) =
        Phoneme(ipa, kind, ex.toList(), zh, hint)

    /** 20 个元音：7 短 + 5 长 + 8 双 */
    val vowels: List<Phoneme> = listOf(
        p("/iː/", PhonemeKind.LONG_VOWEL, "看见", "嘴角向两边拉开，拖长读「衣」", "see", "tea", "green"),
        p("/ɪ/", PhonemeKind.SHORT_VOWEL, "坐", "短促的「衣」，嘴比 /iː/ 松", "sit", "big", "fish"),
        p("/e/", PhonemeKind.SHORT_VOWEL, "床", "短「诶」，嘴张到一半", "bed", "pen", "red"),
        p("/æ/", PhonemeKind.SHORT_VOWEL, "猫", "嘴张大到能塞一根手指", "cat", "bag", "hand"),
        p("/ɑː/", PhonemeKind.LONG_VOWEL, "汽车", "嘴张大，舌后缩，拖长「啊」", "car", "star", "farm"),
        p("/ɒ/", PhonemeKind.SHORT_VOWEL, "热的", "短「哦」，嘴唇略圆", "hot", "dog", "box"),
        p("/ɔː/", PhonemeKind.LONG_VOWEL, "门", "圆唇拖长「奥」", "door", "ball", "four"),
        p("/ʊ/", PhonemeKind.SHORT_VOWEL, "书", "短「乌」，唇圆但不前突", "book", "look", "good"),
        p("/uː/", PhonemeKind.LONG_VOWEL, "食物", "圆唇前突，拖长「乌」", "food", "moon", "blue"),
        p("/ʌ/", PhonemeKind.SHORT_VOWEL, "杯子", "短促「阿」，口型放松", "cup", "bus", "sun"),
        p("/ɜː/", PhonemeKind.LONG_VOWEL, "鸟", "舌位居中，拖长「鹅」", "bird", "girl", "nurse"),
        p("/ə/", PhonemeKind.SHORT_VOWEL, "关于", "最轻的「额」，非重读音节里出现", "about", "sofa", "teacher"),
        p("/eɪ/", PhonemeKind.DIPHTHONG, "天", "从 /e/ 滑到 /ɪ/，「诶衣」连读", "day", "name", "cake"),
        p("/aɪ/", PhonemeKind.DIPHTHONG, "自行车", "从 /a/ 滑到 /ɪ/，「啊衣」连读", "bike", "five", "sky"),
        p("/ɔɪ/", PhonemeKind.DIPHTHONG, "男孩", "从 /ɔ/ 滑到 /ɪ/，「奥衣」连读", "boy", "toy", "coin"),
        p("/aʊ/", PhonemeKind.DIPHTHONG, "房子", "从 /a/ 滑到 /ʊ/，「啊乌」连读", "house", "cow", "mouth"),
        p("/əʊ/", PhonemeKind.DIPHTHONG, "去", "从 /ə/ 滑到 /ʊ/，「额乌」连读", "go", "home", "nose"),
        p("/ɪə/", PhonemeKind.DIPHTHONG, "耳朵", "从 /ɪ/ 滑到 /ə/，「衣额」连读", "ear", "near", "hear"),
        p("/eə/", PhonemeKind.DIPHTHONG, "头发", "从 /e/ 滑到 /ə/，「诶额」连读", "hair", "bear", "chair"),
        p("/ʊə/", PhonemeKind.DIPHTHONG, "旅行", "从 /ʊ/ 滑到 /ə/，「乌额」连读", "tour", "poor", "sure"),
    )

    /** 28 个辅音：6 爆破 + 9 摩擦 + 6 破擦 + 3 鼻 + 1 舌侧 + 3 半元音 */
    val consonants: List<Phoneme> = listOf(
        p("/p/", PhonemeKind.PLOSIVE, "钢笔", "双唇紧闭后突然放开，不振动声带", "pen", "pig", "cup"),
        p("/b/", PhonemeKind.PLOSIVE, "包", "口型同 /p/，但要振动声带", "bag", "book", "big"),
        p("/t/", PhonemeKind.PLOSIVE, "十", "舌尖抵上齿龈后弹开，不振动声带", "ten", "cat", "sit"),
        p("/d/", PhonemeKind.PLOSIVE, "狗", "口型同 /t/，但要振动声带", "dog", "desk", "red"),
        p("/k/", PhonemeKind.PLOSIVE, "猫", "舌根抵软腭后放开，不振动声带", "cat", "key", "book"),
        p("/ɡ/", PhonemeKind.PLOSIVE, "去", "口型同 /k/，但要振动声带", "go", "girl", "bag"),
        p("/f/", PhonemeKind.FRICATIVE, "鱼", "上齿轻触下唇吹气，不振动声带", "fish", "five", "coffee"),
        p("/v/", PhonemeKind.FRICATIVE, "非常", "口型同 /f/，但要振动声带", "very", "seven", "love"),
        p("/θ/", PhonemeKind.FRICATIVE, "三", "舌尖轻放上下齿之间吹气，不振动声带", "three", "think", "mouth"),
        p("/ð/", PhonemeKind.FRICATIVE, "这个", "口型同 /θ/，但要振动声带", "this", "mother", "they"),
        p("/s/", PhonemeKind.FRICATIVE, "太阳", "舌尖靠近上齿龈留细缝吹气", "sun", "six", "bus"),
        p("/z/", PhonemeKind.FRICATIVE, "动物园", "口型同 /s/，但要振动声带", "zoo", "zero", "nose"),
        p("/ʃ/", PhonemeKind.FRICATIVE, "她", "双唇略前突，读「嘘」", "she", "ship", "fish"),
        p("/ʒ/", PhonemeKind.FRICATIVE, "测量", "口型同 /ʃ/，但要振动声带", "measure", "usual", "pleasure"),
        p("/h/", PhonemeKind.FRICATIVE, "帽子", "口张开送气，声带不振动", "hat", "hand", "hello"),
        p("/tʃ/", PhonemeKind.AFFRICATE, "椅子", "/t/ 与 /ʃ/ 连读，读「吃」", "chair", "teacher", "watch"),
        p("/dʒ/", PhonemeKind.AFFRICATE, "工作", "/d/ 与 /ʒ/ 连读，读「知」", "job", "juice", "bridge"),
        p("/tr/", PhonemeKind.AFFRICATE, "树", "/t/ 与 /r/ 连读，读「戳」", "tree", "train", "try"),
        p("/dr/", PhonemeKind.AFFRICATE, "连衣裙", "/d/ 与 /r/ 连读，读「捉」", "dress", "drive", "dream"),
        p("/ts/", PhonemeKind.AFFRICATE, "猫（复数）", "/t/ 与 /s/ 连读，读「次」", "cats", "hats", "sits"),
        p("/dz/", PhonemeKind.AFFRICATE, "床（复数）", "/d/ 与 /z/ 连读，读「资」", "beds", "hands", "reads"),
        p("/m/", PhonemeKind.NASAL, "男人", "双唇闭合，气流从鼻腔出", "man", "milk", "mum"),
        p("/n/", PhonemeKind.NASAL, "不", "舌尖抵上齿龈，气流从鼻腔出", "no", "nine", "sun"),
        p("/ŋ/", PhonemeKind.NASAL, "唱歌", "舌根抵软腭，气流从鼻腔出，读「嗯」", "sing", "long", "morning"),
        p("/l/", PhonemeKind.LATERAL, "腿", "舌尖抵上齿龈，气流从舌两侧出", "leg", "look", "ball"),
        p("/r/", PhonemeKind.SEMI_VOWEL, "红色的", "舌尖卷起但不碰上腭", "red", "run", "car"),
        p("/j/", PhonemeKind.SEMI_VOWEL, "是", "半元音，同「耶」的起头", "yes", "you", "yellow"),
        p("/w/", PhonemeKind.SEMI_VOWEL, "我们", "半元音，双唇收圆后放开", "we", "water", "window"),
    )

    val all: List<Phoneme> = vowels + consonants

    /** 速查表与分类展示的顺序：元音在前，辅音在后 */
    val kindOrder: List<PhonemeKind> = listOf(
        PhonemeKind.SHORT_VOWEL,
        PhonemeKind.LONG_VOWEL,
        PhonemeKind.DIPHTHONG,
        PhonemeKind.PLOSIVE,
        PhonemeKind.FRICATIVE,
        PhonemeKind.AFFRICATE,
        PhonemeKind.NASAL,
        PhonemeKind.LATERAL,
        PhonemeKind.SEMI_VOWEL,
    )
}
