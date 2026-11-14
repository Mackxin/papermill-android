package com.papermill.app.domain.generate

/**
 * v0.5.0 主题识字词库：6 大生活场景 · 约 110 个常用词。
 *
 * ============================================================
 * 来源与版权边界
 * ============================================================
 * 只借鉴「按生活主题把字词分成人物 / 物品 / 设施」这个**组织思路**，
 * 以及「拼音在上、汉字在下」的通用识字卡版式。
 *
 * 词语是日常名词、拼音是读音事实，都不构成受著作权保护的表达；
 * 这里的每一条都是**重新录入并逐条校对**的，没有复制 child-names 的词表。
 * 那个仓库的原表有多处拼音错误（黑板写成 bái bái hēi、跷跷板写成
 * qiāo qiāo qiāo、隧道写成 shān dìng、天桥写成 tiān tiān、
 * 草坪写成 căo píng、交警写成 jiāo jíng 等），直接抄会把错读音印给孩子。
 *
 * 该仓库 README 声称 MIT 却没有 LICENSE 文件（GitHub license=null），
 * 且依赖付费图像 API，因此其代码与提示词文本一律不碰。
 */

enum class ThemeCategory(val label: String) {
    PERSON("人物"),
    ITEM("物品"),
    PLACE("设施"),
}

data class ThemeWord(val pinyin: String, val word: String, val category: ThemeCategory)

/** person / item / place 每项都是「拼音 to 词语」 */
data class Theme(
    val id: String,
    val name: String,
    val person: List<Pair<String, String>>,
    val item: List<Pair<String, String>>,
    val place: List<Pair<String, String>>,
) {
    val words: List<ThemeWord> by lazy {
        person.map { ThemeWord(it.first, it.second, ThemeCategory.PERSON) } +
            item.map { ThemeWord(it.first, it.second, ThemeCategory.ITEM) } +
            place.map { ThemeWord(it.first, it.second, ThemeCategory.PLACE) }
    }
}

private fun p(vararg pairs: Pair<String, String>): List<Pair<String, String>> = pairs.toList()

object ThemeBank {

    val themes: List<Theme> = listOf(
        Theme(
            "market", "超市",
            person = p(
                "shōu yín yuán" to "收银员",
                "lǐ huò yuán" to "理货员",
                "gù kè" to "顾客",
                "bǎo ān" to "保安",
            ),
            item = p(
                "huò jià" to "货架",
                "gòu wù chē" to "购物车",
                "píng guǒ" to "苹果",
                "niú nǎi" to "牛奶",
                "miàn bāo" to "面包",
                "shū cài" to "蔬菜",
                "shuǐ guǒ" to "水果",
                "guǒ zhī" to "果汁",
            ),
            place = p(
                "chū kǒu" to "出口",
                "rù kǒu" to "入口",
                "shōu yín tái" to "收银台",
                "jià gé pái" to "价格牌",
                "bō li" to "玻璃",
            ),
        ),
        Theme(
            "hospital", "医院",
            person = p(
                "yī shēng" to "医生",
                "hù shi" to "护士",
                "huàn zhě" to "患者",
                "bìng rén" to "病人",
            ),
            item = p(
                "tīng zhěn qì" to "听诊器",
                "tǐ wēn biǎo" to "体温表",
                "yào piàn" to "药片",
                "kǒu zhào" to "口罩",
                "bēng dài" to "绷带",
                "yào shuǐ" to "药水",
                "zhēn tóu" to "针头",
            ),
            place = p(
                "guà hào chù" to "挂号处",
                "yào fáng" to "药房",
                "shǒu shù shì" to "手术室",
                "zǒu láng" to "走廊",
                "bìng chuáng" to "病床",
            ),
        ),
        Theme(
            "park", "公园",
            person = p(
                "xiǎo péng yǒu" to "小朋友",
                "lǎo yé ye" to "老爷爷",
                "lǎo nǎi nai" to "老奶奶",
                "yuán dīng" to "园丁",
            ),
            item = p(
                "huā duǒ" to "花朵",
                "dà shù" to "大树",
                "xiǎo cǎo" to "小草",
                "hú dié" to "蝴蝶",
                "qiū qiān" to "秋千",
                "huá tī" to "滑梯",
                "qiāo qiāo bǎn" to "跷跷板",
            ),
            place = p(
                "cháng yǐ" to "长椅",
                "shí qiáo" to "石桥",
                "shuǐ chí" to "水池",
                "tíng zi" to "亭子",
                "cǎo píng" to "草坪",
                "lā jī tǒng" to "垃圾桶",
            ),
        ),
        Theme(
            "school", "学校",
            person = p(
                "lǎo shī" to "老师",
                "xué shēng" to "学生",
                "xiào zhǎng" to "校长",
                "tóng xué" to "同学",
            ),
            item = p(
                "shū běn" to "书本",
                "qiān bǐ" to "铅笔",
                "běn zi" to "本子",
                "kè zhuō" to "课桌",
                "yǐ zi" to "椅子",
                "hēi bǎn" to "黑板",
                "shū bāo" to "书包",
            ),
            place = p(
                "jiào shì" to "教室",
                "cāo chǎng" to "操场",
                "tú shū guǎn" to "图书馆",
                "shí táng" to "食堂",
                "lóu tī" to "楼梯",
            ),
        ),
        Theme(
            "zoo", "动物园",
            person = p(
                "sì yǎng yuán" to "饲养员",
                "xiàng dǎo" to "向导",
                "yóu kè" to "游客",
            ),
            item = p(
                "hóu zi" to "猴子",
                "dà xiàng" to "大象",
                "lǎo hǔ" to "老虎",
                "shī zi" to "狮子",
                "xióng māo" to "熊猫",
                "cháng jǐng lù" to "长颈鹿",
                "hǎi tún" to "海豚",
                "kǒng què" to "孔雀",
            ),
            place = p(
                "lóng zi" to "笼子",
                "shuǐ chí" to "水池",
                "zhǐ shì pái" to "指示牌",
                "wéi lán" to "围栏",
                "shí qiáo" to "石桥",
            ),
        ),
        Theme(
            "traffic", "交通",
            person = p(
                "sī jī" to "司机",
                "chéng kè" to "乘客",
                "jiāo jǐng" to "交警",
                "xíng rén" to "行人",
            ),
            item = p(
                "qì chē" to "汽车",
                "zì xíng chē" to "自行车",
                "gōng gòng qì chē" to "公共汽车",
                "chū zū chē" to "出租车",
                "dì tiě" to "地铁",
                "huǒ chē" to "火车",
                "fēi jī" to "飞机",
                "lún chuán" to "轮船",
            ),
            place = p(
                "mǎ lù" to "马路",
                "rén xíng dào" to "人行道",
                "hóng lǜ dēng" to "红绿灯",
                "suì dào" to "隧道",
                "tiān qiáo" to "天桥",
                "zhàn pái" to "站牌",
            ),
        ),
    )

    val allWords: List<ThemeWord> by lazy { themes.flatMap { it.words } }

    fun byId(id: String): Theme? = themes.firstOrNull { it.id == id }

    /** 练习纸用：拼音 to 词语，按主题顺序铺格子 */
    fun cardsOf(themeId: String): List<Pair<String, String>> =
        byId(themeId)?.words?.map { it.pinyin to it.word } ?: emptyList()
}
