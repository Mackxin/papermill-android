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
  ref_antonym       18,764 对反义词                → 先存着，等新题型那一版再用
  ref_synonym       6,556 组近义词                 → 结构是「一组词用空格隔开」，不是成对的，
                                                   直接做成选择题容易出「两个都对」，暂不取
  ref_xiehouyu      14,031 条歇后语                → 暂不取（体积大、生成器没有对应题型）
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
    drop = Counter()
    seen: set[str] = set()
    out: list[dict] = []
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
        if zh in seen:
            drop['重复（同一释义）'] += 1
            continue
        seen.add(zh)
        out.append({'en': en, 'zh': zh, 'grade': as_grade(grade)})
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


def clean_classifiers(con: sqlite3.Connection) -> tuple[list[dict], Counter, list]:
    drop = Counter()
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
        k = (n, c)
        if k in seen:
            drop['重复'] += 1
            continue
        seen.add(k)
        out.append({'noun': n, 'classifier': c})
    out.sort(key=lambda x: (len(x['noun']), x['noun']))
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
        pre = text[:text.index(BEGIN)]
        post = text[text.index(END) + len(END):]
        text = pre + block + post
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
    classifiers, clf_drop, _ = clean_classifiers(con)

    def report(name: str, items: list, drop: Counter) -> None:
        print(f'{name:12s} 留 {len(items):6d}  丢 {sum(drop.values()):6d}  {dict(drop)}')
        for s in items[:args.sample]:
            print('     ·', json.dumps(s, ensure_ascii=False)[:120])

    report('中文字词', cn_words, cn_drop)
    report('英语单词', en_words, en_drop)
    report('古诗（新增）', poems, poem_drop)
    report('量词', classifiers, clf_drop)
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
    }
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
