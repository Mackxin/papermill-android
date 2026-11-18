#!/usr/bin/env python3
"""把下载下来的 docx 试卷切成一道道结构化的题。

输入：downloads/papers/<id>_<名字>.docx
输出：web/data/papers/questions.json（同时拷一份到 App assets）
      + app/src/main/assets/papers/questions.json

为什么要「切」而不是把整份卷子收进 App：
  用户要的是「自由组合出卷」—— 勾几个知识点、出几道题，就得能跨卷挑题。
  一整份 docx 塞进去只能整张打印，组合不了。

切题的依据（看完 5 份样本摸出来的共同结构）：
  · 大题标题：  一、填空题（每空1分，共10分）      → 决定题型
  · 小题题号：  1. xxx / 1.xxx / 1、xxx
  · 选项：      A. xxx  B. xxx（可能同一行，也可能各占一行）
  · 答案：      「参考答案」之后按大题重新列一遍，或数学卷题内直接「解：…答：…」

不硬切的东西（如实记账，不假装切出来了）：
  · 带「如图/下图/图1」而 docx 里没有图片的题 —— 切出来也是残题，跳过并计数
  · 纯图形操作题（画垂线、画梯形）—— 没有图就是废题，跳过
  · 一行多道的口算题 —— 拆成多道小题，题干就是算式本身
"""

import hashlib
import json
import re
import sys
from collections import Counter, OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))
from docx_text import OBJ, read_docx, to_lines  # noqa: E402

SRC = ROOT / 'downloads/papers'
LIB = ROOT / 'web/data/papers/library.json'
OUT_WEB = ROOT / 'web/data/papers/questions.json'
OUT_APP = ROOT / 'app/src/main/assets/papers/questions.json'

CN = {'一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '八': 8, '九': 9, '十': 10,
      '十一': 11, '十二': 12}

SEC_RE = re.compile(r'^([一二三四五六七八九十]{1,3})\s*[、.．]\s*(.{0,30})')
# 大题标题里基本都会出现这些词（或者「共20分」这种分值说明）。
# 用来把「四、五月份各用水多少吨？」这类题干残句挡在大题之外。
SEC_KW = re.compile(r'填空|选择|判断|计算|口算|竖式|脱式|简算|应用|解决|操作|连线|搭配|阅读'
                    r'|口语|习作|作文|写话|基础|积累|运用|综合|实践|探究|实验|简答|解答|问答'
                    r'|作图|统计|算一算|填一填|选一选|比一比|想一想|画一画|分\s*[）)]'
                    r'|共\s*\d+\s*分')
QNO_RE = re.compile(r'^(\d{1,3})\s*[.、．]\s*(.*)$')
# 语文卷（也有部分科学的探究题）不用「1.」而用「（1）」编号。
# 只在「这个大题里还没出现 1. 式题号」时才认它 —— 否则「解决问题」下面的
# 「（1）（2）」是同一道题的小问，切开会把一道应用题拆成三截。
QNO_SUB_RE = re.compile(r'^[（(]\s*(\d{1,2})\s*[）)]\s*(.*)$')
# 语文卷的选择题用 ①②③④ 编号（部编版很常见）。
# 难点是同一个符号也用来列选项（「①酿酒 ②酱油 ③醋」），所以只认
# 「行首一个圈号、且整行只有这一个圈号」的行 —— 一行里出现两个以上就是列表。
CIRCLE = '①②③④⑤⑥⑦⑧⑨⑩'
QNO_CIRCLE_RE = re.compile(r'^([①-⑩])\s*(.+)$')
OPT_RE = re.compile(r'^([A-D])\s*[.、．]\s*(.*)$')
ANS_HEAD_RE = re.compile(r'^\s*(参考答案|答案与解析|答案|【答案】)')
# 一行塞好几道：120×70= 360÷60= …（口算、直接写得数常见）
INLINE_CALC_RE = re.compile(r'(\d[\d.]*\s*[+\-×÷*\/]\s*\d[\d.]*\s*=\s*(?:（\s*）)?)')
FIGURE_RE = re.compile(r'如图|下图|上图|右图|左图|图中|见图|图示|图\s*\d|示意图|统计图|条形图'
                       r'|折线图|扇形图|图片|图案|看图|照片')


def sec_type(name):
    if '判断' in name:
        return 'JUDGE'
    if '选择' in name or '单选' in name or '多选' in name:
        return 'CHOICE'
    if '填空' in name:
        return 'FILL'
    if '计算' in name or '口算' in name or '竖式' in name or '脱式' in name or '简算' in name:
        return 'CALC'
    if '解决' in name or '应用' in name or '探究' in name or '实验' in name or '阅读' in name:
        return 'WORD'
    if '操作' in name or '画' in name:
        return 'DRAW'
    if '连线' in name or '搭配' in name:
        return 'MATCH'
    return 'OTHER'


def parse_answers(lines):
    """答案区 → {大题序号: {小题号: 答案文本}}。

    答案区有两个坑：
      1. 一行塞多道：`1. D 2. A 3. B`（选择题最常见）
      2. 多套卷的答案连在一起：第二个「一、」要另起一册
    """
    start = None
    for i, ln in enumerate(lines):
        if ANS_HEAD_RE.match(ln):
            start = i + 1
    if start is None:
        return [{}]

    books = [{}]
    cur_no = None
    for ln in lines[start:]:
        m = SEC_RE.match(ln)
        if m and len(ln) <= 34 and '？' not in ln and '?' not in ln:
            no = CN.get(m.group(1))
            if no == 1 and books[-1]:
                books.append({})
            cur_no = no
            books[-1].setdefault(no, {})
            continue
        if cur_no is None:
            continue
        # 一行多道：按 `数字.` 切开
        parts = re.split(r'(?=(?<!\d)\d{1,3}\s*[.、．]\s*)', ln)
        for p in parts:
            pm = QNO_RE.match(p.strip())
            if pm:
                books[-1][cur_no][int(pm.group(1))] = pm.group(2).strip()
    return books


def split_options(line):
    """`A. 单调枯燥 B. 丰富多彩 C. 紧张忙碌` → 四项。

    试卷排版常把四个选项塞一行，只按行首匹配的话只有 A 被认出来，
    B/C/D 会被并进 A 的正文里。
    """
    parts = re.split(r'(?=(?<![A-Za-z])[A-D]\s*[.．、]\s)', line)
    out = []
    for p in parts:
        p = p.strip()
        m = re.match(r'^([A-D])\s*[.．、]\s*(.+)$', p)
        if m:
            out.append(f'{m.group(1)}. {m.group(2).strip()}')
    return out


def split_inline_calc(text):
    """`120×70= 360÷60= 25×40=` → 三道。只在不含中文的纯算式行上动手。"""
    if re.search(r'[\u4e00-\u9fa5]', text):
        return [text]
    found = INLINE_CALC_RE.findall(text)
    if len(found) < 2:
        return [text]
    return [f.strip() for f in found]


def is_section(ln, m):
    """这一行是不是「一、填空题（共20分）」这种大题标题。

    踩过的坑：应用题里有一句「小明家三、四、五月份各用水多少吨？」被排版拆成
    「四、五月份各用水多少吨？」开头的一行，按旧规则直接被当成第「四」大题，
    卷面上于是多出一个叫「五月份各用水多少吨？」的大题。所以加两道闸：
    带问号的一律不是大题标题；标题里得有题型词或「共N分」。
    """
    if len(ln) > 34 or QNO_RE.match(ln):
        return False
    if '？' in ln or '?' in ln:
        return False
    rest = m.group(2).strip()
    if not rest:                      # 只有「一、」一行，也算标题
        return True
    return bool(SEC_KW.search(ln))


def parse_paper(lines):
    """→ [ {no, name, type, items:[{n, text, options}]} ]"""
    sections = []
    cur = None
    cur_item = None
    in_answer = False

    for ln in lines:
        if ANS_HEAD_RE.match(ln):
            in_answer = True
            continue
        if in_answer:
            continue

        m = SEC_RE.match(ln)
        if m and is_section(ln, m):
            no = CN.get(m.group(1), 0)
            name = re.sub(r'[（(].*?[）)]', '', m.group(2)).strip().rstrip('。：:，, 、')
            cur = {'no': no, 'name': (name or ln)[:16], 'type': sec_type(name or ln),
                   'items': [], 'num_style': None}
            sections.append(cur)
            cur_item = None
            continue

        if cur is None:
            continue

        qm = QNO_RE.match(ln)
        if qm:
            cur['num_style'] = 'dot'
            body = qm.group(2).strip()
            pieces = split_inline_calc(body)
            for k, piece in enumerate(pieces):
                cur_item = {'n': int(qm.group(1)) if k == 0 else None,
                            'text': piece, 'options': []}
                cur['items'].append(cur_item)
            continue

        sm = QNO_SUB_RE.match(ln)
        if sm and cur.get('num_style') != 'dot':
            body = sm.group(2).strip()
            if len(body) >= 4:
                cur['num_style'] = 'paren'
                cur_item = {'n': int(sm.group(1)), 'text': body, 'options': []}
                cur['items'].append(cur_item)
                continue

        cm = QNO_CIRCLE_RE.match(ln)
        if cm and len([c for c in ln if c in CIRCLE]) == 1 and len(cm.group(2).strip()) >= 6:
            cur_item = {'n': CIRCLE.index(cm.group(1)) + 1, 'text': cm.group(2).strip(),
                        'options': []}
            cur['items'].append(cur_item)
            continue

        if cur_item is not None:
            opts = split_options(ln)
            if len(opts) >= 2:
                cur_item['options'].extend(opts)
                continue

        om = OPT_RE.match(ln)
        if om and cur_item is not None:
            cur_item['options'].append(f'{om.group(1)}. {om.group(2).strip()}')
            continue

        # 续行：接在上一题后面（题干折行、选项折行都走这里）
        if cur_item is not None:
            if cur_item['options'] and len(ln) <= 30 and not re.search(r'[。？]', ln):
                cur_item['options'][-1] += ' ' + ln
            else:
                cur_item['text'] += ln
    return sections


def norm(text):
    return re.sub(r'[\s（）()　_＿]+', '', text)


def fingerprint(text):
    return hashlib.md5(norm(text).encode()).hexdigest()[:16]


# 卷子里混在题干里的非题目文字：页脚「第 3 页（共 4 页）」、密封线、
# 「学校： 姓名： 班级： 学号：」这种卷头表格、以及一排省略号。
# 16167 道题里只命中十来条，但印在卷面上特别刺眼，顺手洗掉。
PAGE_FOOT_RE = re.compile(r'第?\s*\d+\s*页\s*[（(]?\s*共\s*\d+\s*页\s*[）)]?')
FORM_RE = re.compile(r'学校[:：].{0,10}姓名[:：].{0,10}|密封线|评卷人[:：]?|得分栏|答题卡')


# 题干里出现「2.xx 3.xx 4.xx」这种连号，基本都是答案区的「2.② 3.√」被当成题收进来了。
# 前面加 (?<![.\d]) 是为了不误伤「圆周率 3.14」这种小数。
MULTI_NO_RE = re.compile(r'(?<![.\d])\d{1,2}\s*[.．]\s*(?=[^\d\s])')
# 一道计算题里混进了「解方程（8分）」这种下一个大题的小标题 —— 说明几个大题被粘在一起了
MIX_SEC_RE = re.compile(r'(解方程|脱式计算|竖式计算|直接写得数|化简比|求比值|能简算的要简算)'
                        r'\s*[（(]?\s*\d+\s*分')


def clean_text(t):
    t = PAGE_FOOT_RE.sub(' ', t)
    t = re.sub(r'[.…·]{6,}', ' ', t)      # …………………………
    t = FORM_RE.sub(' ', t)
    return re.sub(r'[ \t]{2,}', ' ', t).strip()


def build(max_files=None):
    lib = {x['i']: x for x in json.loads(LIB.read_text(encoding='utf-8'))['items']}
    files = sorted(SRC.glob('*.docx'))
    # 有的分享是「文件夹」，里面除了卷子还塞了答题卡和单独的答案文件。
    # 答题卡是空表格、单独答案文件只有答案没有题，切出来都是残渣，直接不进。
    files = [f for f in files
             if not re.search(r'答题卡|（答案）|\(答案\)|（答案与解析）', f.stem)]
    if max_files:
        files = files[:max_files]

    questions = []
    seen = set()
    stat = Counter()
    skipped_fig = 0

    for idx, fp in enumerate(files, 1):
        pid = fp.name.split('_')[0]
        meta = lib.get(pid, {})
        try:
            lines = to_lines(read_docx(fp))
        except Exception as e:
            print(f'  读不了 {fp.name}: {e}')
            stat['read_fail'] += 1
            continue

        sections = parse_paper(lines)
        books = parse_answers(lines)
        ans_book = books[0] if books else {}

        # 卷子可能是「两套」：第二个「一、」之后的题归到第二份答案
        sec_seen_one = 0
        for sec in sections:
            if sec['no'] == 1:
                sec_seen_one += 1
            book = books[min(sec_seen_one - 1, len(books) - 1)] if books else {}
            ans_sec = book.get(sec['no'], {})

            for it in sec['items']:
                text = clean_text(it['text'])
                # 带占位符 = 题干里原本有图片或 MathType 公式，文字抽不出来。
                # 这种题印出来就是「一汽车▯小时行了▯千米」，只能整题剔除。
                if OBJ in text or any(OBJ in o for o in it['options']):
                    stat['obj'] += 1
                    continue
                # 答案区漏进题目区：「① 2.② 3.②」「× 2.√ 3.×」这种连号行不是题
                if len(MULTI_NO_RE.findall(text)) >= 3:
                    stat['answer_line'] += 1
                    continue
                # 两个大题被粘成一题：「…×78 解方程（8分）…」
                if MIX_SEC_RE.search(text[8:]):
                    stat['mix'] += 1
                    continue
                if len(norm(text)) < 5:
                    stat['too_short'] += 1
                    continue
                # 只剩「选择题（10分）」这种纯题型名的小标题，不是题
                if re.match(r'^(选择题|填空题|判断题|计算题|操作题|解决问题|连线题|基础运用)'
                            r'[（(]?\d*分?[）)]?$', text):
                    stat['heading'] += 1
                    continue
                # 提到图/照片的题，图本身在 docx 里是图片，切不出来 —— 印在卷上就是
                # 「如图，点（　）表示冰山位置」然后下面什么都没有，家长一眼就知道是残题。
                # 带选项的选择题以前是留着的，实测卷面上全是这种空指代，索性一起剔。
                if FIGURE_RE.search(text):
                    skipped_fig += 1
                    stat['figure'] += 1
                    continue
                if sec['type'] == 'DRAW':
                    stat['draw'] += 1
                    continue
                # 超过 300 字的题干基本都是「阅读下面短文，回答问题」那段材料本身，
                # 没有材料在后头的题是残题，不进题库
                if len(text) > 300:
                    stat['material'] += 1
                    continue

                answer = ''
                if it['n'] and it['n'] in ans_sec:
                    answer = ans_sec[it['n']]
                elif it['options']:
                    answer = ''

                qtype = sec['type']
                if qtype == 'CHOICE' and not it['options']:
                    qtype = 'OTHER'
                # 语文卷把「选择题」写成普通题号（「4. 选择题（10分）」），
                # 大题名是「基础运用」，靠名字判不出题型 —— 有四个选项就是选择题
                if it['options'] and qtype in ('OTHER', 'FILL'):
                    qtype = 'CHOICE'
                # 判断题是「（　）」里打勾，不该有 A/B/C/D。带出来的选项是排版错位
                # 粘到下一条上的，直接丢掉别印在卷面上。
                if qtype == 'JUDGE':
                    it['options'] = []

                q = {
                    'q': text[:400],
                    't': qtype,
                    'g': meta.get('g', 0),
                    's': meta.get('s', ''),
                    'sec': sec['name'][:12],
                    'src': pid,
                }
                if it['options']:
                    q['o'] = it['options'][:6]
                if answer:
                    q['a'] = answer[:200]

                f = fingerprint(text) + '|' + ''.join(it['options'])[:60]
                if f in seen:
                    stat['dup'] += 1
                    continue
                seen.add(f)
                questions.append(q)
                stat[qtype] += 1
        print(f'[{idx}/{len(files)}] {fp.name[:46]}  已收 {len(questions)}', flush=True)

    doc = {
        'note': ('题目来自 downloads/papers 里下载的公开分享试卷，由 tools/build-question-bank.py '
                 '自动切分。题干保留原文，答案取卷内给出的参考答案；没有给出答案的题 a 字段为空。'),
        'count': len(questions),
        'items': questions,
    }
    OUT_WEB.parent.mkdir(parents=True, exist_ok=True)
    OUT_WEB.write_text(json.dumps(doc, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    OUT_APP.parent.mkdir(parents=True, exist_ok=True)
    OUT_APP.write_text(OUT_WEB.read_text(encoding='utf-8'), encoding='utf-8')

    print('\n=== 统计 ===')
    print('题数', len(questions))
    for k, v in stat.most_common():
        print(f'  {k:12s} {v}')
    print('输出', OUT_WEB.relative_to(ROOT), f'{OUT_WEB.stat().st_size // 1024} KB')


if __name__ == '__main__':
    n = int(sys.argv[sys.argv.index('--limit') + 1]) if '--limit' in sys.argv else None
    build(n)
