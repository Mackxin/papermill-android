#!/usr/bin/env python3
"""把 zx-study 的**参考表**（ref_*）导成本项目能直接用的 JSON / Kotlin 片段。

用法：
    python3 tools/import-zx-refs.py /tmp/zx/bank.db --schema    # 只看表结构
    python3 tools/import-zx-refs.py /tmp/zx/bank.db --dry       # 只统计 + 抽样
    python3 tools/import-zx-refs.py /tmp/zx/bank.db             # 真写盘

源仓库：https://github.com/132465798a/zx-study （MIT，build/bank.db 约 37 MB）
题目本身由 import-zx-study.py 负责，这个脚本只碰 ref_* 参考表。

------------------------------------------------------------------------
取了哪几张表、为什么取 / 为什么不取
------------------------------------------------------------------------
  ref_word          3,656 词（教材词表，带年级）  → **取**。本项目原来只有 80 个词，
                                                   一年级要出 30 道拼音题其实只有 8 个词可用
  ref_english_word  1,116 词（带年级、中文释义）   → **取**。原来只有 42 个
  ref_poem          109 首（带年级、原文）         → **取**。原来 44 首，一并追加进 Poems.kt
  ref_classifier    1,189 条「名词→量词」          → **取**（919 条落在小学字表内），做量词速查表
  ref_antonym       18,764 对反义词                → **取**（v0.29.0 写反义词题）。只留「一词一反」
                                                   的 5,000 来个，多义的（高→低/矮）整批不要，
                                                   否则填空题的答案不唯一
  ref_synonym       6,556 组近义词                 → **不取**：结构是「人 士 人物 人士 人氏」这样
                                                   一组词用空格隔开，不是成对的；做成选择题容
                                                   易出「两个都对」
  ref_xiehouyu      14,031 条歇后语                → **取**（v0.29.0 歇后语题），过三道闸门后
                                                   约 3,000 条。做成**选择题**不做填空 ——
                                                   答案天生不唯一（做贼的说梦话——想偷／做贼心虚）
  ref_idiom         30,895 条成语                  → **不取**，理由见下
  ref_hanzi         2,469 个生字                   → **不取**：只有字和年级、没有拼音；
                                                   而生字题本来就取「词的首字」，词表扩了它就跟着够了

为什么成语不取：zx 的 explanation 是词典原文（「阿鼻梵语的译音，意译为无间"…」这类），
既有引号残缺，也大量是「阿鼻地狱」「阿党比周」这种小学根本不会考的生僻条目。
本项目 Idioms.kt 里的释义一直是自己重写的、控制在 12~24 字 —— 直接灌 3 万条词典原文
会一次性把那条规矩废掉，还会把选择题撑成三行。等有精力做「成语补全（不靠释义）」再说。

------------------------------------------------------------------------
拼音从哪来
------------------------------------------------------------------------
ref_word 只有词、没有拼音，可本项目三道语文题（看拼音写词 / 生字注音 / 补全词语）
全都要拼音。所以这里用 pypinyin 生成，并且做两件事：
  1. **一、不 一律标原调**（yī / bù），不按变调写 —— 教材就是这么印的，
     pypinyin 默认会给 yì diǎn、bú jiàn，印在卷子上孩子会照着念错；
  2. 拿 zx 自己 720 道「看拼音写词语」题里带的**逐字拼音**做对照，
     把不一致的条目写进 tools/out/pinyin-mismatch.tsv 供人工抽查。
     实测 101/720 不一致，逐条看过：zx 那批才是错的（采用 cài yòng、音乐 yīn lè、
     一种 yī zhòng…），所以最终以 pypinyin 为准，只留日志不留覆盖表。

------------------------------------------------------------------------
一条硬规矩
------------------------------------------------------------------------
**两端必须字节一致**。所以只生成一次文本，再往 web/ 与 app/ 两个目录各写一份。
"""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIRS = [
    ROOT / 'web' / 'data' / 'refs',
    ROOT / 'app' / 'src' / 'main' / 'assets' / 'refs',
]
OUT_LOG = ROOT / 'tools' / 'out'

CJK = re.compile(r'^[\u4e00-\u9fff]+$')
PUNCT_END = re.compile(r'[，。；？！,;?!]$')
TONE = 'āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜêńňǹ'

# ------------------------------------------------------------------ 人工核过的黑名单
#
# zx 的 ref_antonym / ref_classifier 是**词典级的对义表**，不是小学语文的反义词表 /
# 量词表。直接抽题会印出这种东西：
#     「写出反义词：春天——（秋天）」  春天没有反义词，这是同一维度的对义
#     「写出反义词：渐渐——（逐渐）」  这是近义词，纯粹是库错了
#     「一（首）花儿」               花儿的量词是「朵」，库配错了
# 所以两张表都要过一份逐条核过的黑名单。**只列确定错的**，模糊的宁可留着，
# 因为少一条只是少一道题，错一条是印在卷子上丢人。

# 单字反义词里不是反义的（对义 / 并列 / 近义 / 词内字）。
# 写法：`'左|右'`，读取时转成 frozenset，所以顺序不用管。
ANTONYM_BLOCK = {
    '万|一', '千|一', '中|西', '春|秋', '日|月', '点|面', '乡|城', '作|息',
    '净|毛', '团|尖', '土|洋', '应|呼', '归|去', '次|主', '洋|中', '红|绿',
    '领|发', '交|接', '光|麻', '情|仇', '板|活', '边|内', '递|接', '侧|正',
    '占|领', '我|敌', '招|挥', '流|源', '腹|背', '质|量', '逐|迎', '声|韵',
    '孙|祖', '扶|抑', '捡|洒', '支|塌', '晓|夜', '盾|矛', '惯|管', '络|经',
    '避|扬', '休|戚', '提|推', '圆|方', '巨|小', '底|顶', '扔|接', '返|往',
    '傻|精', '墨|朱', '警|匪', '赤|黑', '平|皱', '君|臣', '买|卖', '夏|冬',
    # 第二遍通读 167 条成品时又抓出来的：并列、词内字、只在特定语境里才反义
    '脚|头',   # 并列，不是反义
    '舍|入',   # 「舍入」是一个词的两个字
    '谢|开',   # 只在「花谢花开」这种语境里成立
    '定|退',   # 不是反义
    '皂|白',   # 皂 = 黑，勉强算对，但这个字小学不学
    '盖|开',   # 盖盖儿／开盖儿，勉强
    '纳|出',   # 「出纳」是词
    '落|添',   # 锦上添花／落井下石，靠语境
}

# 量词配错的（名词与量词不搭，或者名词根本不是名词）
CLASSIFIER_BLOCK = {
    '牛|条',    # 一头牛
    '象|只',    # 一头大象
    '蝇|只',    # 一只苍蝇
    '器|台',    # 「器」单字不成词
    '堂|间',
    '牌|片',
    '词|首', '词|组',
    '课|堂',
    '骂|通',    # 骂是动词
    '紧张|阵',  # 紧张是形容词
    '保护|种', '享受|种', '彩色|种',
    '任命|纸',
    '掩护|面',
    '结构|座',
    '解放|次', '劳动|次',
    '劝告|席',
    '玻璃|张',  # 一块玻璃
    '身体|具',  # 一副身体
    '音乐|张',  # 一首音乐
    '历史|门',
    '地|片',    # 「一地」不成词，得说「一地水」
    '色|种',
    '道|条',    # 「道」本身就是量词
    '坝|条',    # 一道坝 / 一座坝
    '漆|道',
    '秀|场',
    '血|滴',    # 不适合小学卷面
    '麻|缕',
    '墨|块',
    '觉|场',    # 「一觉」是词，「一场觉」不成立
    '根|条',    # 「根」本身就是量词
    '楼|层',    # 一层楼，不是「一楼层」
}


def _key(*parts: str) -> frozenset[str]:
    """黑名单查表用：把「左|右」「牛|条」这样的写法转成无序集合，方向不用管。"""
    return frozenset(''.join(parts))


ANTONYM_BLOCK = {frozenset(p.split('|')) for p in ANTONYM_BLOCK if '|' in p}
CLASSIFIER_BLOCK = {frozenset(p.split('|')) for p in CLASSIFIER_BLOCK if '|' in p}

# 歇后语的内容闸门（v0.29.0）。ref_xiehouyu 是「民间歇后语大全」，
# 里面混着大量不适合印在小学生卷子上的条目：「沸水煮孩子——熟人」
# 「脏水罐子掉到茅坑里——越闹越臭」「屁股坐在鸡蛋上——一塌糊涂」。
# 这些不是排版问题、也不是难度问题，是内容不能进校园，所以单独一张黑名单。
XH_BAD = re.compile(
    r'屎|尿|屁|茅坑|茅房|厕所|粪|尸|棺材|坟|婊|嫖|娼|妓|窑子|寡妇|光棍'
    r'|光膀|屁股|大腿|奶|偷汉|上吊|鸦片|大烟|赌|脱裤|裤裆|床上|媳妇|老公'
    r'|老婆|汉子|娘们|情郎|色鬼|喝尿|吃屎'
)
PINYIN = re.compile(rf'^[a-zü{TONE}]+$')

# ---------------------------------------------------------------- 轻声修正
#
# pypinyin 对轻声的处理时好时坏：房子→fáng zi、日子→rì zi 是对的，
# 可妈妈→mā mā、朋友→péng yǒu、衣服→yī fú、时候→shí hòu 都带着调号。
# 教材上这些一律标轻声，印在拼音题里家长一眼就看出不对。
#
# 两条修正，宁可少改不乱改：
#  1. **叠字词**（妈妈 / 爸爸 / 星星）第二个字读轻声 —— 但「常常 / 悄悄 / 个个」
#     这类叠字不读轻声，列进 [AA_KEEP_TONE] 排除；
#  2. 一小张手工轻声表，只收小学低年级必考、且各家词典都标轻声的词。
AA_KEEP_TONE = {
    '常常', '刚刚', '渐渐', '慢慢', '深深', '高高', '远远', '多多', '好好',
    '人人', '家家', '处处', '时时', '天天', '年年', '月月', '种种', '件件',
    '个个', '条条', '片片', '朵朵', '阵阵', '声声', '步步', '层层', '面面',
    '匆匆', '纷纷', '默默', '悄悄', '静静', '轻轻', '暗暗', '连连', '屡屡',
    '写写', '看看', '读读', '想想', '说说', '做做',
}
NEUTRAL_TAIL = {
    # 词 → 从第几个字起读轻声
    '朋友': 1, '衣服': 1, '漂亮': 1, '时候': 1, '眼睛': 1, '明白': 1,
    '知道': 1, '认识': 1, '东西': 1, '事情': 1, '咱们': 1, '什么': 1,
    '怎么': 1, '这么': 1, '那么': 1, '多么': 1, '先生': 1, '姑娘': 1,
    '萝卜': 1, '玻璃': 1, '聪明': 1, '暖和': 1, '舒服': 1, '喜欢': 1,
    '招呼': 1, '故事': 1, '告诉': 1, '休息': 1, '消息': 1, '名字': 1,
    '盘子': 1, '杯子': 1, '桌子': 1, '椅子': 1, '鞋子': 1, '袜子': 1,
    '帽子': 1, '兔子': 1, '猴子': 1, '鸭子': 1, '力气': 1, '客气': 1,
    '忘记': 1, '照顾': 1, '商量': 1, '打扮': 1, '收拾': 1, '热闹': 1,
}

# ---------------------------------------------------------------- 人工核过的读音
#
# 下面这些词的读音**不等于**逐字单念的标准读音（多音字靠词组才念对），
# 自动闸门本来会把它们当「可疑」挡掉。这份表是把被挡下的 63 个词逐条看过之后
# 留下的 —— 读音都对，所以放行，并且把拼音写死在这里：即使 pypinyin 以后换版本、
# 改了词组词典，印出来的还是这几个读音。
#
# 被挡下的一共 63 个，其中 4 个没进这张表：古朴 / 简朴 / 质朴 / 纯朴 ——
# pypinyin 把「朴」注成 piáo（只有作姓才这么念），正确的是 pǔ，这四个词直接不要。
APPROVED_READINGS = {
    '音乐': 'yīn yuè', '角色': 'jué sè', '沉着': 'chén zhuó', '长城': 'cháng chéng',
    '相似': 'xiāng sì', '仔细': 'zǐ xì', '睡觉': 'shuì jiào', '堵塞': 'dǔ sè',
    '恐吓': 'kǒng hè', '改行': 'gǎi háng', '投降': 'tóu xiáng', '重叠': 'chóng dié',
    '专长': 'zhuān cháng', '闭塞': 'bì sè', '阻塞': 'zǔ sè', '长征': 'cháng zhēng',
    '淹没': 'yān mò', '剥皮': 'bāo pí', '表率': 'biǎo shuài', '草率': 'cǎo shuài',
    '轻率': 'qīng shuài', '率领': 'shuài lǐng', '直率': 'zhí shuài', '埋没': 'mái mò',
    '漫长': 'màn cháng', '称职': 'chèn zhí', '了解': 'liǎo jiě', '调和': 'tiáo hé',
    '调整': 'tiáo zhěng', '供给': 'gōng jǐ', '刹那': 'chà nà', '重逢': 'chóng féng',
    '劲敌': 'jìng dí', '着慌': 'zháo huāng', '差遣': 'chāi qiǎn', '便宜': 'pián yi',
    '协调': 'xié tiáo', '传略': 'zhuàn lüè', '统率': 'tǒng shuài', '狭长': 'xiá cháng',
    '重申': 'chóng shēn', '重庆': 'chóng qìng', '微薄': 'wēi bó', '薄弱': 'bó ruò',
    '单薄': 'dān bó', '淡薄': 'dàn bó', '刻薄': 'kè bó', '浅薄': 'qiǎn bó',
    '轻薄': 'qīng bó', '恰似': 'qià sì', '埋怨': 'mán yuàn', '坦率': 'tǎn shuài',
    '乐章': 'yuè zhāng', '覆没': 'fù mò', '宝藏': 'bǎo zàng', '西藏': 'xī zàng',
    '可恶': 'kě wù', '厌恶': 'yàn wù', '憎恶': 'zēng wù',
}


def norm(s) -> str:
    if s is None:
        return ''
    return re.sub(r'\s+', ' ', str(s)).strip()


def as_grade(v, default: int = 3) -> int:
    try:
        g = int(float(v))
    except (TypeError, ValueError):
        return default
    return g if 1 <= g <= 9 else default


# ---------------------------------------------------------------- 拼音


def build_gold(con: sqlite3.Connection) -> dict[str, str]:
    """从 zx 的「看拼音写词语」题里把 (词 → 拼音) 抽出来当对照金标。

    这批题的 options 长这样：
        {"words":[{"pinyin":["yuè","liàng"],"chars":["月","亮"]}]}
    逐字对应，正好拼回一个词与它的拼音。
    """
    gold: dict[str, str] = {}
    try:
        rows = con.execute(
            "SELECT options FROM question WHERE type='pinyin_word'").fetchall()
    except sqlite3.DatabaseError:
        return gold
    for (raw,) in rows:
        try:
            o = json.loads(raw)
        except (TypeError, ValueError):
            continue
        for w in (o.get('words') if isinstance(o, dict) else None) or []:
            chars, py = w.get('chars') or [], w.get('pinyin') or []
            if chars and len(chars) == len(py):
                gold.setdefault(''.join(chars), ' '.join(py))
    return gold


def make_pinyin():
    """返回 (函数(词)→拼音) 或 None（没装 pypinyin）。

    两层处理：
      1. **一、不 一律标原调**（yī / bù），不按变调写 —— 教材就是这么印的，
         pypinyin 默认会给 yì diǎn、bú jiàn，印在卷子上孩子会照着念错；
      2. 每个音节再过一道 [syllable_ok] 校验，读音可疑的词直接不要（见那里的注释）。
    """
    try:
        from pypinyin import Style, pinyin  # noqa: PLC0415
    except ImportError:
        print('! 没装 pypinyin，拼音一个都生成不了（pip install pypinyin）', file=sys.stderr)
        return None

    def raw(word: str) -> list[str]:
        return [res[0] for res in pinyin(word, style=Style.TONE)]

    TONE_MAP = str.maketrans('āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ', 'aaaaeeeeiiiioooouuuuüüüü')
    _char_cache: dict[str, str] = {}

    def base_of(syl: str) -> str:
        """去声调只留字母，用来判「是不是只差在轻声」。"""
        return syl.translate(TONE_MAP)

    def raw_of_char(ch: str) -> str:
        """这个字单独念时的标准读音（缓存：3,656 个词里同一个字会碰很多次）。"""
        if ch not in _char_cache:
            r = pinyin(ch, style=Style.TONE)
            _char_cache[ch] = r[0][0] if r and r[0] else ''
        return _char_cache[ch]

    def to_pinyin(word: str) -> str:
        out = []
        for ch, syl in zip(word, raw(word)):
            if ch == '一':
                syl = 'yī'
            elif ch == '不':
                syl = 'bù'
            out.append(syl)
        # 轻声：叠字词与手工表，从第 n 个字起去调号
        start = None
        if len(word) == 2 and word[0] == word[1] and word not in AA_KEEP_TONE:
            start = 1
        elif word in NEUTRAL_TAIL:
            start = NEUTRAL_TAIL[word]
        if start:
            for i in range(start, len(out)):
                out[i] = base_of(out[i])
        return ' '.join(out)

    def syllable_ok(word: str) -> tuple[bool, str]:
        """读音是否可信。

        规矩：**每个字的读音必须等于它单独念时的标准读音**，只允许差在轻声
        （眼睛 → yǎn jing，睛单念 jīng，这种是对的，教材也这么印）。

        为什么这么严：pypinyin 的词组词典有真错。它把「古朴」注成 gǔ piáo、
        「简朴」注成 jiǎn piáo（朴只有作姓才念 piáo），这类错一旦印到拼音题上，
        孩子照着念就是错的。而「读音 == 单字标准读音」这个条件恰好把它们全滤掉 ——
        代价是「音乐 yīn yuè」「长辈 zhǎng bèi」这类必须靠词组才念对的词也一起不要，
        3,656 个词里放弃 63 个，换来剩下 3,593 个一个不错，值。
        """
        syls = raw(word)
        for ch, syl in zip(word, syls):
            if ch in '一不':
                continue
            # 词组里被念成轻声（无调号）→ 放行
            if base_of(syl) == base_of(raw_of_char(ch)):
                continue
            return False, f'{ch}:{syl}≠{raw_of_char(ch)}'
        return True, ''

    to_pinyin.syllable_ok = syllable_ok  # type: ignore[attr-defined]
    return to_pinyin


# ---------------------------------------------------------------- 各表清洗


def clean_cn_words(con: sqlite3.Connection, to_pinyin) -> tuple[list[dict], Counter, list]:
    drop = Counter()
    seen: set[str] = set()
    out: list[dict] = []
    gold = build_gold(con)
    mismatch: list[tuple[str, str, str]] = []
    risky: list[tuple[str, str]] = []
    check = getattr(to_pinyin, 'syllable_ok', None)

    for word, grade, freq in con.execute('SELECT word, grade, freq FROM ref_word'):
        w = norm(word)
        if not w:
            drop['空词'] += 1
            continue
        if not CJK.match(w) or not (2 <= len(w) <= 4):
            drop['不是 2~4 字纯汉字'] += 1
            continue
        if w in seen:
            drop['重复'] += 1
            continue
        seen.add(w)
        py = to_pinyin(w)
        syl = py.split(' ')
        if len(syl) != len(w) or not all(PINYIN.match(s) for s in syl):
            drop['拼音不干净'] += 1
            continue
        if check:
            ok, why = check(w)
            if not ok:
                approved = APPROVED_READINGS.get(w)
                if not approved:
                    drop['读音不是单字标准读音（多音字靠词组才念对）'] += 1
                    risky.append((w, why))
                    continue
                py = approved
        if w in gold and py != gold[w]:
            mismatch.append((w, gold[w], py))
        out.append({'word': w, 'pinyin': py, 'grade': as_grade(grade)})

    return out, drop, mismatch, risky


def clean_en_words(con: sqlite3.Connection) -> tuple[list[dict], Counter, list]:
    """英语单词表。

    两道去重，都是为了「答案唯一」：
      · **同一个中文释义只留一个**（看见 → see 与 watch 都算对，印成汉译英就是错的）；
      · **同一个英文单词只留一个义项**（drink 既是「饮料」又是「喝」，同理）。
    两个都留、都去重之后，一条词对应一个唯一答案，英译汉 / 汉译英都能批改。
    """
    drop = Counter()
    rows: list[dict] = []
    for word, grade, meaning in con.execute(
            'SELECT word, grade, meaning FROM ref_english_word'):
        en = norm(word).lower()
        zh = norm(meaning)
        if not en or not re.match(r"^[a-z][a-z\- ']*$", en):
            drop['不是英语单词'] += 1
            continue
        if not zh or not CJK.search(zh):
            drop['缺中文释义'] += 1
            continue
        if len(zh) > 12:
            drop['释义太长'] += 1
            continue
        rows.append({'en': en, 'zh': zh, 'grade': as_grade(grade)})

    # 按年级升序后去重：同一个词的多个义项，留最早出现的那个（最基础的那个义项）
    rows.sort(key=lambda x: (x['grade'], x['en']))
    seen_en: set[str] = set()
    seen_zh: set[str] = set()
    out: list[dict] = []
    for r in rows:
        if r['en'] in seen_en:
            drop['同一单词的另一个义项'] += 1
            continue
        if r['zh'] in seen_zh:
            drop['同一释义的另一个词'] += 1
            continue
        seen_en.add(r['en'])
        seen_zh.add(r['zh'])
        out.append(r)
    return out, drop, []


def split_lines(raw: str) -> list[str]:
    """把诗正文切成「一句一行」，每句带标点 —— 和 Poems.kt 里的写法一致。"""
    text = str(raw or '').replace('\r', '').replace('\\n', '\n')
    lines: list[str] = []
    for para in text.split('\n'):
        para = para.strip()
        if not para:
            continue
        # 按标点断句，标点留在句尾
        parts = re.findall(r'[^，。；？！,;?!]*[，。；？！,;?!]?', para)
        lines.extend(p.strip() for p in parts if p.strip())
    return lines


def clean_poems(con: sqlite3.Connection, have: tuple[set[str], set[str]])\
        -> tuple[list[dict], Counter, list]:
    drop = Counter()
    seen_titles, seen_firsts = set(have[0]), set(have[1])
    out: list[dict] = []
    for title, author, dynasty, grade, lines_raw in con.execute(
            'SELECT title, author, dynasty, grade, lines FROM ref_poem'):
        t = norm(title)
        if not t:
            drop['缺标题'] += 1
            continue
        if t in seen_titles:
            drop['已有/重复'] += 1
            continue
        try:
            raw = json.loads(lines_raw) if isinstance(lines_raw, str) else lines_raw
        except (TypeError, ValueError):
            raw = lines_raw
        if isinstance(raw, list):
            body = '\n'.join(str(x) for x in raw)
        else:
            body = str(raw or '')
        lines = split_lines(body)
        if len(lines) < 4:
            drop['句数不够（<4 句凑不出接句题）'] += 1
            continue
        if lines[0] in seen_firsts:
            drop['同一首诗换个标题'] += 1
            continue
        seen_titles.add(t)
        seen_firsts.add(lines[0])
        out.append({'title': t, 'author': norm(author),
                    'dynasty': norm(dynasty) or '佚名',
                    'grade': as_grade(grade), 'lines': lines})
    return out, drop, []


def clean_classifiers(con: sqlite3.Connection,
                      words: set[str]) -> tuple[list[dict], Counter, list]:
    """量词搭配（v0.28.0 速查表 / v0.29.0 量词填空题）。

    grade = 名词与量词两侧里**最难的那个字**。量词题学生要同时认名词、写出量词，
    所以三年级才认得出的名词不能出现在一年级的卷子上。「一（　）书」一年级会写，
    「一（　）绺头发」六年级也未必会写。

    名词还必须在**教材词表**里（或者本身是单字）：ref_classifier 是全词典级的搭配，
    「一（场）掩护」「一（纸）任命」这种配错的和超纲词一起被这一条挡掉。
    """
    drop = Counter()
    hanzi_grade = {ch: (g if isinstance(g, int) and 1 <= g <= 9 else 6)
                   for ch, g in con.execute('SELECT char, grade FROM ref_hanzi')}
    seen = set()
    out: list[dict] = []
    for noun, clf in con.execute('SELECT noun, classifier FROM ref_classifier'):
        n, c = norm(noun), norm(clf)
        if not (CJK.match(n) and CJK.match(c)):
            drop['不是纯汉字'] += 1
            continue
        if len(n) > 4 or len(c) > 2:
            drop['太长'] += 1
            continue
        if any(ch not in hanzi_grade for ch in n + c):
            drop['有超出 1~6 年级生字表的字'] += 1
            continue
        if len(n) > 1 and n not in words:
            drop['名词不在教材词表（超纲或不是名词）'] += 1
            continue
        if frozenset((n, c)) in CLASSIFIER_BLOCK:
            drop['人工核过：搭配错了'] += 1
            continue
        k = (n, c)
        if k in seen:
            drop['重复'] += 1
            continue
        seen.add(k)
        out.append({'noun': n, 'classifier': c,
                    'grade': max(hanzi_grade[ch] for ch in n + c)})
    out.sort(key=lambda x: (x['grade'], len(x['noun']), x['noun']))
    return out, drop, []


def clean_antonyms(con: sqlite3.Connection) -> tuple[list[dict], Counter, list]:
    """反义词（v0.29.0 写反义词题）。

    只收**单字对**，这是被数据逼出来的结论。上真数据跑了一遍抽样：

        反义词：东门——（西门）      对义，不是反义
        反义词：春天——（秋天）      春天没有反义词
        反义词：渐渐——（逐渐）      近义词，库直接错了
        反义词：远处——（远方）      近义词

    双字对里这类错误占到两成以上 —— ref_antonym 是词典级的**对义表**，
    把「南北」「春秋」「买卖」这类同一维度的并列概念也算了进去。
    单字对干净得多（上／下、大／小、进／退），也正是低年级反义词题的样子，
    所以只留单字，再把逐条核出来的错对用 [ANTONYM_BLOCK] 挡掉。

    其余闸门与原先相同：
      · 一字只能有一个反义词（高→低/矮 这种整批不要，填空答案必须唯一）；
      · 反向也不唯一时不要（「亮→暗」收下了，可「暗」的反义还有「明」）。
    """
    drop = Counter()
    hanzi_grade = {ch: (g if isinstance(g, int) and 1 <= g <= 9 else 6)
                   for ch, g in con.execute('SELECT char, grade FROM ref_hanzi')}
    by_left: dict[str, set[str]] = {}
    for a, b in con.execute('SELECT left, right FROM ref_antonym'):
        la, lb = norm(a), norm(b)
        if not (la and lb):
            drop['空'] += 1
            continue
        if not (CJK.match(la) and CJK.match(lb)):
            drop['不是纯汉字'] += 1
            continue
        if len(la) != 1 or len(lb) != 1:
            drop['不是单字对（双字对错误率太高，见函数注释）'] += 1
            continue
        if la == lb:
            drop['左右同一个字'] += 1
            continue
        if frozenset((la, lb)) in ANTONYM_BLOCK:
            drop['人工核过：不是反义（对义/并列/近义）'] += 1
            continue
        missing = [ch for ch in la + lb if ch not in hanzi_grade]
        if missing:
            drop['有超出 1~6 年级生字表的字'] += 1
            continue
        by_left.setdefault(la, set()).add(lb)

    out: list[dict] = []
    emitted: set[frozenset[str]] = set()
    for a, rights in by_left.items():
        if len(rights) != 1:
            drop['一字多反义（填空答案不唯一）'] += 1
            continue
        b = next(iter(rights))
        if len(by_left.get(b, ())) > 1:
            drop['反向不唯一'] += 1
            continue
        # 库里两个方向都记了（上→下、下→上），只留一条，否则同一张卷子里
        # 「写出反义词：上——（　）」和「写出反义词：下——（　）」会同时出现
        pair = frozenset((a, b))
        if pair in emitted:
            drop['同一对的正反两条只留一条'] += 1
            continue
        emitted.add(pair)
        g = max(hanzi_grade[ch] for ch in a + b)
        out.append({'a': a, 'b': b, 'grade': g})
    out.sort(key=lambda x: (x['grade'], x['a']))
    return out, drop, []


def clean_xiehouyu(con: sqlite3.Connection) -> tuple[list[dict], Counter, list]:
    """歇后语（v0.29.0）。

    **做成选择题，不做填空** —— 这是这张表能不能用的关键：

      · 答案天生不唯一。「做贼的说梦话」可以是「想偷」，也可以是「做贼心虚」；
      · 库里 14,031 条大半是方言俗语（「做砖的坯子、插刀的鞘子——框框套套」），
        填空题印出来学生一个字都对不上。

    选择题只要「4 个选项里只有一个搭得上」就成立，顺带把这些风险压下去。
    剩下三道闸门：
      1. 答案 2~8 字、不含分号（分号 = 一题两答）、不含括号（那是注释）；
      2. 答案每个字都在 1~6 年级生字表里 —— 写不出来的字不能当选项；
      3. 谜面 4~14 字，生字覆盖率 >= 85%（偶尔一两个生字能靠上下文猜）。
    """
    drop = Counter()
    hanzi_grade = {ch: (g if isinstance(g, int) and 1 <= g <= 9 else 6)
                   for ch, g in con.execute('SELECT char, grade FROM ref_hanzi')}
    seen: set[str] = set()
    out: list[dict] = []
    for riddle, answer in con.execute('SELECT riddle, answer FROM ref_xiehouyu'):
        r, a = norm(riddle), norm(answer)
        if not (r and a):
            drop['空'] += 1
            continue
        if '（' in a or '(' in a or '）' in a or ')' in a or '；' in a or ';' in a:
            drop['答案带注释或一题两答'] += 1
            continue
        if not (2 <= len(a) <= 8):
            drop['答案长度不在 2~8 字'] += 1
            continue
        if XH_BAD.search(r) or XH_BAD.search(a):
            drop['内容不宜进校园（见 XH_BAD）'] += 1
            continue
        if not (4 <= len(r) <= 14):
            drop['谜面长度不在 4~14 字'] += 1
            continue
        if any(ch not in hanzi_grade for ch in a):
            drop['答案有超出 1~6 年级生字表的字'] += 1
            continue
        hit = sum(1 for ch in r if ch in hanzi_grade)
        if hit / len(r) < 0.85:
            drop['谜面生字太多（认不全）'] += 1
            continue
        if r in seen:
            drop['同一谜面重复'] += 1
            continue
        seen.add(r)
        # 年级：谜面里最难的那个字决定「几年级才读得懂」。
        # 谜面允许 15% 的字在表外，那些按 6 年级算（认不全的字只会让题更难，不会更简单）
        out.append({'r': r, 'a': a,
                    'grade': max(hanzi_grade.get(ch, 6) for ch in r)})
    out.sort(key=lambda x: (x['grade'], len(x['r']), x['r']))
    return out, drop, []


# ---------------------------------------------------------------- 读已有古诗


def existing_poem_titles() -> tuple[set[str], set[str]]:
    """Poems.kt 里**手写**的那些篇目：返回 (标题集合, 首句集合)。

    首句也要比 —— 同一首诗在不同选本里标题不一样（手写的是「悯农」，
    zx 叫「悯农其二」），只比标题会把它收成两首，卷子上就印两遍同一首诗。

    先把自己上次导入的标记区切掉再数 —— 不然第二次运行会把导入的全看成「已有」，
    源数据更新了也刷不进来。
    """
    path = ROOT / 'app' / 'src' / 'main' / 'java' / 'com' / 'papermill' / 'app' / \
        'domain' / 'content' / 'Poems.kt'
    if not path.exists():
        return set(), set()
    text = path.read_text(encoding='utf-8')
    if BEGIN in text and END in text:
        text = text[:text.index(BEGIN)] + text[text.index(END) + len(END):]
    titles = set(re.findall(r'p\(\s*"([^"]+)"', text))
    raw = re.findall(r'p\((.*?)\),\n', text, re.S)
    firsts: set[str] = set()
    for r in raw:
        parts = re.findall(r'"([^"]*)"', r)
        # 一行里「标题 / 作者 / 朝代」是字符串，年级是裸数字，之后才是诗句。
        # 所以不能写死第几个下标 —— 直接找第一段以中文标点收尾的字符串。
        first = next((p for p in parts if PUNCT_END.search(p)), '')
        if first:
            firsts.add(first)
    return titles, firsts


# ---------------------------------------------------------------- 写盘


def kq(s: str) -> str:
    """Kotlin 字符串字面量（Poems.kt 里要嵌中文与全角标点，只转义引号和反斜杠）。"""
    return '"' + str(s).replace('\\', '\\\\').replace('"', '\\"') + '"'


def poems_kt_block(items: list[dict]) -> str:
    head = (
        '\n        // ---- 以下由 tools/import-zx-refs.py 从 zx-study（MIT）导入，勿手改 ----\n'
        '        // 只收能确认原文无误的篇目；注释、译文、赏析一律不收录（那些有版权，\n'
        '        // 且本项目只用「原文 + 作者 + 朝代」这三样客观信息）。\n'
    )
    rows = []
    for it in items:
        args = ', '.join([kq(it['title']), kq(it['author']), kq(it['dynasty']),
                          str(it['grade'])] + [kq(x) for x in it['lines']])
        rows.append(f'        p({args}),')
    return head + '\n'.join(rows) + '\n'


BEGIN = '// >>> zx-refs:poems 导入区（脚本维护，勿手改）'
END = '// <<< zx-refs:poems 导入区'


def write_poems(items: list[dict]) -> bool:
    """把新诗追加进 Poems.kt 的标记区；重复运行只替换标记区，不会写两遍。"""
    if not items:
        return False
    path = ROOT / 'app' / 'src' / 'main' / 'java' / 'com' / 'papermill' / 'app' / \
        'domain' / 'content' / 'Poems.kt'
    text = path.read_text(encoding='utf-8')
    # 标记行也缩进 8 格，跟列表里其它条目对齐，不然整段贴在最左边很难读
    block = f'        {BEGIN}\n{poems_kt_block(items)}        {END}'

    if BEGIN in text:
        # pre 要 rstrip：标记行前面的缩进已经含在 pre 末尾了，若直接拼上
        # block 自带的 8 格，每跑一次缩进就加长 8 格（踩过：跑几遍以后
        # 缩进变成 64 格，满屏都是空白）
        pre = text[:text.index(BEGIN)].rstrip()
        post = text[text.index(END) + len(END):]
        text = pre + '\n' + block + post
    else:
        # 第一次运行：插到 items 列表的收尾处。
        # 锚点取「收尾的 `)` + 后面的 pool 注释」，比找最后一个 `)` 稳 ——
        # 文件末尾还有好几个函数体，最后一个 `)` 不一定是列表的收尾。
        anchor = '\n    )\n\n    /** 某年级可以背的诗'
        if anchor not in text:
            raise SystemExit('Poems.kt 里找不到 items 列表的结尾，放弃追加')
        text = text.replace(anchor, '\n' + block + '\n    )\n\n    /** 某年级可以背的诗', 1)
    path.write_text(text, encoding='utf-8')
    return True


def blob(obj) -> str:
    s = json.dumps(obj, ensure_ascii=False, separators=(',', ':'))
    # 单文件版会把 JSON 内联进 <script>，不转义 < 会被浏览器提前闭合
    return s.replace('<', '\\u003c')


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('db', type=Path)
    ap.add_argument('--dry', action='store_true', help='只统计，不写文件')
    ap.add_argument('--schema', action='store_true', help='只打印表结构')
    ap.add_argument('--sample', type=int, default=4)
    args = ap.parse_args()

    con = sqlite3.connect(f'file:{args.db}?mode=ro', uri=True)

    if args.schema:
        for name, in con.execute(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"):
            cs = [r[1] for r in con.execute(f'PRAGMA table_info("{name}")')]
            try:
                cnt = con.execute(f'SELECT COUNT(*) FROM "{name}"').fetchone()[0]
            except sqlite3.DatabaseError:
                cnt = -1
            print(f'{name:22s} {cnt:7d}  [{", ".join(cs)}]')
        return 0

    to_pinyin = make_pinyin()

    cn_words, cn_drop, mismatch, cn_risky = (
        clean_cn_words(con, to_pinyin) if to_pinyin
        else ([], Counter({'没有 pypinyin': 1}), [], []))
    en_words, en_drop, _ = clean_en_words(con)
    poems, poem_drop, _ = clean_poems(con, existing_poem_titles())
    classifiers, clf_drop, _ = clean_classifiers(con, {w['word'] for w in cn_words})
    antonyms, ant_drop, _ = clean_antonyms(con)
    xiehouyu, xh_drop, _ = clean_xiehouyu(con)

    def report(name: str, items: list, drop: Counter) -> None:
        print(f'{name:12s} 留 {len(items):6d}  丢 {sum(drop.values()):6d}  {dict(drop)}')
        for s in items[:args.sample]:
            print('     ·', json.dumps(s, ensure_ascii=False)[:120])

    report('中文字词', cn_words, cn_drop)
    report('英语单词', en_words, en_drop)
    report('古诗（新增）', poems, poem_drop)
    report('量词', classifiers, clf_drop)
    report('反义词', antonyms, ant_drop)
    report('歇后语', xiehouyu, xh_drop)
    if mismatch:
        print(f'拼音与 zx 金标不一致 {len(mismatch)} 条（以 pypinyin 为准，写进日志供抽查）：')
        for w, g, m in mismatch[:8]:
            print(f'     · {w}  zx={g}  我们={m}')
    if cn_risky:
        print(f'因读音可疑被挡下的词 {len(cn_risky)} 条：')
        for w, why in cn_risky[:8]:
            print(f'     · {w}  {why}')

    if args.dry:
        print('\n--dry：没有写盘')
        return 0

    payloads = {
        'cn_words': cn_words,
        'en_words': en_words,
        'classifiers': classifiers,
        'antonyms': antonyms,
    }
    # 歇后语**暂时不写盘**（v0.29.0 决定）：
    # clean_xiehouyu 过完闸门还剩 4,268 条，但抽样一看，错别字（「厚簿」应为「厚薄」、
    # 「摸到藉」应为「摸到藕」）与不雅内容（已挡掉 552 条）之外，还有大量民间俗语
    # 本来就不该进小学卷子。这张表要么人工精选几百条，要么不印 —— 先不印。
    # 哪天有精选清单，把它加进 payloads 就能接上，生成器那边已经留好位置。
    manifest = {}
    for name, items in payloads.items():
        text = blob(items)
        for d in OUT_DIRS:
            d.mkdir(parents=True, exist_ok=True)
            (d / f'{name}.json').write_text(text, encoding='utf-8')
        manifest[name] = len(items)
    mt = blob(dict(sorted(manifest.items())))
    for d in OUT_DIRS:
        (d / 'manifest.json').write_text(mt, encoding='utf-8')

    if write_poems(poems):
        manifest['poems_added'] = len(poems)

    OUT_LOG.mkdir(parents=True, exist_ok=True)
    if mismatch:
        (OUT_LOG / 'pinyin-mismatch.tsv').write_text(
            '词\tzx金标\t我们生成\n' + ''.join(f'{w}\t{g}\t{m}\n' for w, g, m in mismatch),
            encoding='utf-8')
    if cn_risky:
        (OUT_LOG / 'pinyin-risky.tsv').write_text(
            '词\t原因\n' + ''.join(f'{w}\t{why}\n' for w, why in cn_risky),
            encoding='utf-8')

    print(f"\n写盘完成：词 {len(cn_words)} / 英语 {len(en_words)} / "
          f"量词 {len(classifiers)} / 古诗追加 {len(poems)}")
    print('  目录：' + ' 与 '.join(str(d.relative_to(ROOT)) for d in OUT_DIRS))
    return 0


if __name__ == '__main__':
    sys.exit(main())
