package com.papermill.app.domain.content

/**
 * 课本同步清单：语文生字、英语核心词、数学知识点，按年级分段。
 *
 * ============================================================
 * 来源与版权边界（重要）
 * ============================================================
 * **不收录任何教材课文原文。** 课文、例句、讲解文字都是受著作权保护的表达，
 * 照搬会侵权，所以这里一条课文都没有。
 *
 * 这里收的是**清单式的事实内容**：
 *  - 生字 + 读音：汉字与拼音是语言事实，任何字典都这么标；
 *  - 英语核心词 + 中文释义：基础词汇与词义是公共知识；
 *  - 数学知识点标题：知识点名称是事实，不含任何教辅的讲解文字或例题。
 *
 * 每个字/词/知识点都是按年级重新筛选录入的（低年级不会蹦出「磅礴」），
 * 没有对应任何一套教材的单元顺序 —— 这不是「第几单元第几课」，
 * 而是「这个年级该会的字词与知识点」。
 *
 * 用途：
 *  1. 「打印纸」页的课本同步表（按年级段打印，贴书桌 / 夹书里）；
 *  2. 「课本生字注音」「课本单词默写」两种题型（抽测清单里的内容）。
 */

/** 语文生字：一个字 + 它的读音 */
data class GradeHanzi(val hanzi: String, val pinyin: String, val grade: Int)

/** 英语核心词：单词 + 中文 + 起始年级 */
data class GradeEnWord(val en: String, val zh: String, val grade: Int)

/** 数学知识点：一条标题 + 所属年级 */
data class GradeMathPoint(val point: String, val grade: Int)

object Textbook {

    private fun h(grade: Int, vararg pairs: Pair<String, String>) =
        pairs.map { GradeHanzi(it.first, it.second, grade) }

    private fun w(grade: Int, vararg pairs: Pair<String, String>) =
        pairs.map { GradeEnWord(it.first, it.second, grade) }

    private fun m(grade: Int, vararg points: String) =
        points.map { GradeMathPoint(it, grade) }

    // ---------------------------------------------------------------- 语文生字

    val hanzi: List<GradeHanzi> = listOf(
        h(
            1,
            "一" to "yī", "二" to "èr", "三" to "sān", "四" to "sì", "五" to "wǔ",
            "六" to "liù", "七" to "qī", "八" to "bā", "九" to "jiǔ", "十" to "shí",
            "人" to "rén", "口" to "kǒu", "手" to "shǒu", "足" to "zú", "上" to "shàng",
            "下" to "xià", "大" to "dà", "小" to "xiǎo", "天" to "tiān", "地" to "dì",
        ),
        h(
            2,
            "春" to "chūn", "夏" to "xià", "秋" to "qiū", "冬" to "dōng", "风" to "fēng",
            "雨" to "yǔ", "雪" to "xuě", "云" to "yún", "山" to "shān", "水" to "shuǐ",
            "花" to "huā", "草" to "cǎo", "树" to "shù", "鸟" to "niǎo", "鱼" to "yú",
            "虫" to "chóng", "爸" to "bà", "妈" to "mā", "哥" to "gē", "姐" to "jiě",
        ),
        h(
            3,
            "观" to "guān", "察" to "chá", "实" to "shí", "验" to "yàn", "记" to "jì",
            "录" to "lù", "准" to "zhǔn", "备" to "bèi", "骄" to "jiāo", "傲" to "ào",
            "谦" to "qiān", "虚" to "xū", "勇" to "yǒng", "敢" to "gǎn", "诚" to "chéng",
            "信" to "xìn", "努" to "nǔ", "力" to "lì", "勤" to "qín", "奋" to "fèn",
        ),
        h(
            4,
            "灿" to "càn", "烂" to "làn", "敏" to "mǐn", "捷" to "jié", "温" to "wēn",
            "暖" to "nuǎn", "辽" to "liáo", "阔" to "kuò", "朴" to "pǔ", "素" to "sù",
            "雄" to "xióng", "伟" to "wěi", "精" to "jīng", "神" to "shén", "繁" to "fán",
            "荣" to "róng", "宁" to "níng", "静" to "jìng", "珍" to "zhēn", "惜" to "xī",
        ),
        h(
            5,
            "慷" to "kāng", "慨" to "kǎi", "徘" to "pái", "徊" to "huái", "凝" to "níng",
            "视" to "shì", "丰" to "fēng", "盛" to "shèng", "尊" to "zūn", "敬" to "jìng",
            "谨" to "jǐn", "慎" to "shèn", "坚" to "jiān", "韧" to "rèn", "沉" to "chén",
            "稳" to "wěn", "思" to "sī", "索" to "suǒ", "品" to "pǐn", "格" to "gé",
        ),
        h(
            6,
            "蜿" to "wān", "蜒" to "yán", "静" to "jìng", "谧" to "mì", "憧" to "chōng",
            "憬" to "jǐng", "磅" to "páng", "礴" to "bó", "苍" to "cāng", "茫" to "máng",
            "璀" to "cuǐ", "璨" to "càn", "缜" to "zhěn", "密" to "mì", "砥" to "dǐ",
            "砺" to "lì", "翱" to "áo", "翔" to "xiáng", "蕴" to "yùn", "含" to "hán",
        ),
        h(
            7,
            "酝" to "yùn", "酿" to "niàng", "朗" to "lǎng", "润" to "rùn", "嘹" to "liáo",
            "亮" to "liàng", "烘" to "hōng", "托" to "tuō", "镶" to "xiāng", "嵌" to "qiàn",
            "贮" to "zhù", "蓄" to "xù", "澄" to "chéng", "清" to "qīng", "枯" to "kū",
            "涸" to "hé", "高" to "gāo", "邈" to "miǎo", "傲" to "ào", "然" to "rán",
        ),
        h(
            8,
            "溃" to "kuì", "退" to "tuì", "督" to "dū", "战" to "zhàn", "锐" to "ruì",
            "缭" to "liáo", "乱" to "luàn", "屏" to "bǐng", "息" to "xī", "敛" to "liǎn",
            "声" to "shēng", "娴" to "xián", "熟" to "shú", "浩" to "hào", "瀚" to "hàn",
            "澎" to "péng", "湃" to "pài", "凛" to "lǐn", "冽" to "liè", "萧" to "xiāo",
        ),
        h(
            9,
            "隧" to "suì", "道" to "dào", "陶" to "táo", "冶" to "yě", "枉" to "wǎng",
            "然" to "rán", "谋" to "móu", "取" to "qǔ", "补" to "bǔ", "拙" to "zhuō",
            "精" to "jīng", "益" to "yì", "求" to "qiú", "虚" to "xū", "怀" to "huái",
            "若" to "ruò", "谷" to "gǔ", "豁" to "huò", "达" to "dá", "悟" to "wù",
        ),
    ).flatten()

    // ---------------------------------------------------------------- 英语核心词

    val words: List<GradeEnWord> = listOf(
        w(
            3,
            "apple" to "苹果", "banana" to "香蕉", "cat" to "猫", "dog" to "狗",
            "book" to "书", "pen" to "钢笔", "desk" to "课桌", "chair" to "椅子",
            "red" to "红色", "blue" to "蓝色", "mother" to "妈妈", "father" to "爸爸",
            "school" to "学校", "teacher" to "老师", "friend" to "朋友", "water" to "水",
            "milk" to "牛奶", "bread" to "面包", "happy" to "高兴的", "big" to "大的",
        ),
        w(
            4,
            "classroom" to "教室", "playground" to "操场", "library" to "图书馆",
            "breakfast" to "早餐", "dinner" to "晚餐", "evening" to "傍晚",
            "homework" to "家庭作业", "beautiful" to "美丽的", "careful" to "仔细的",
            "difficult" to "困难的", "holiday" to "假期", "hospital" to "医院",
            "restaurant" to "餐馆", "vegetable" to "蔬菜", "weather" to "天气",
            "sunny" to "晴朗的", "cloudy" to "多云的", "favourite" to "最喜爱的",
            "together" to "一起", "always" to "总是",
        ),
        w(
            5,
            "computer" to "电脑", "message" to "消息", "question" to "问题",
            "answer" to "答案", "important" to "重要的", "interesting" to "有趣的",
            "different" to "不同的", "dangerous" to "危险的", "delicious" to "美味的",
            "exciting" to "令人兴奋的", "exercise" to "锻炼", "hobby" to "爱好",
            "future" to "未来", "machine" to "机器", "mountain" to "山",
            "village" to "村庄", "season" to "季节", "autumn" to "秋天",
            "winter" to "冬天", "because" to "因为",
        ),
        w(
            6,
            "chemistry" to "化学", "geography" to "地理", "history" to "历史",
            "science" to "科学", "information" to "信息", "environment" to "环境",
            "pollution" to "污染", "protect" to "保护", "recycle" to "回收",
            "energy" to "能源", "invent" to "发明", "invention" to "发明物",
            "tradition" to "传统", "festival" to "节日", "celebrate" to "庆祝",
            "courage" to "勇气", "promise" to "承诺", "success" to "成功",
            "experience" to "经验", "physics" to "物理",
        ),
        w(
            7,
            "achieve" to "实现", "attitude" to "态度", "benefit" to "益处",
            "challenge" to "挑战", "communicate" to "交流", "compare" to "比较",
            "confident" to "自信的", "consider" to "考虑", "continue" to "继续",
            "describe" to "描述", "develop" to "发展", "encourage" to "鼓励",
            "explain" to "解释", "imagine" to "想象", "improve" to "改进",
            "manage" to "设法完成", "prepare" to "准备", "realize" to "意识到",
            "suggest" to "建议", "support" to "支持",
        ),
        w(
            8,
            "ability" to "能力", "advantage" to "优势", "apologize" to "道歉",
            "appreciate" to "感激", "argument" to "争论", "available" to "可获得的",
            "balance" to "平衡", "behaviour" to "行为", "community" to "社区",
            "competition" to "竞赛", "decision" to "决定", "direction" to "方向",
            "discover" to "发现", "education" to "教育", "electric" to "电的",
            "familiar" to "熟悉的", "influence" to "影响", "knowledge" to "知识",
            "neighbour" to "邻居", "pressure" to "压力",
        ),
        w(
            9,
            "accomplish" to "完成", "achievement" to "成就", "admire" to "钦佩",
            "announce" to "宣布", "approach" to "接近", "assume" to "假定",
            "attempt" to "尝试", "aware" to "意识到的", "capable" to "有能力的",
            "circumstance" to "情况", "consequence" to "后果", "contribute" to "贡献",
            "convince" to "说服", "creative" to "有创造力的", "demonstrate" to "证明",
            "efficient" to "高效的", "emphasize" to "强调", "establish" to "建立",
            "evaluate" to "评估", "appropriate" to "恰当的",
        ),
    ).flatten()

    // ---------------------------------------------------------------- 数学知识点

    val mathPoints: List<GradeMathPoint> = listOf(
        m(
            1, "20 以内的加减法", "100 以内数的认识", "认识长方形 / 正方形 / 三角形 / 圆",
            "认识人民币", "认识整时", "10 以内数的分与合", "比较多少与长短", "分类与整理",
        ),
        m(
            2, "表内乘法（九九乘法表）", "表内除法", "100 以内加减法竖式", "认识角",
            "长度单位：米与厘米", "认识时间：分", "数据的收集与整理", "同级运算的顺序",
        ),
        m(
            3, "万以内的加减法", "多位数乘一位数", "分数的初步认识", "长方形与正方形的周长",
            "面积与面积单位", "时 / 分 / 秒", "毫米 / 分米 / 千米 / 吨", "简单的小数",
        ),
        m(
            4, "大数的认识", "三位数乘两位数", "除数是两位数的除法", "四则运算与运算定律",
            "小数的意义与加减法", "角的度量", "平行四边形与梯形", "条形统计图",
        ),
        m(
            5, "小数乘法与除法", "简易方程", "因数与倍数", "长方体和正方体",
            "分数的意义与性质", "分数的加减法", "图形的平移与旋转", "折线统计图",
        ),
        m(
            6, "分数乘法与除法", "比与比例", "圆的周长与面积", "百分数",
            "圆柱与圆锥", "负数", "扇形统计图", "比例尺",
        ),
        m(
            7, "有理数及其运算", "整式的加减", "一元一次方程", "几何图形初步",
            "相交线与平行线", "实数：平方根与立方根", "平面直角坐标系", "二元一次方程组",
        ),
        m(
            8, "三角形与全等三角形", "轴对称", "整式的乘法与因式分解", "分式",
            "二次根式", "勾股定理", "平行四边形", "一次函数",
        ),
        m(
            9, "一元二次方程", "二次函数", "反比例函数", "图形的旋转",
            "圆", "相似三角形", "锐角三角函数", "概率初步",
        ),
    ).flatten()

    // ---------------------------------------------------------------- 查询

    fun hanziFor(grade: Int): List<GradeHanzi> =
        hanzi.filter { it.grade <= grade }.ifEmpty { hanzi }

    fun wordsFor(grade: Int): List<GradeEnWord> =
        words.filter { it.grade <= grade }.ifEmpty { words }
}
