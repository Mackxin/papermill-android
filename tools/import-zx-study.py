#!/usr/bin/env python3
"""把 zx-study（正贤学习平台，MIT）的 bank.db 合并进本项目的真题库。

用法：
    python3 tools/import-zx-study.py /tmp/zx-bank.db          # 合并并写盘
    python3 tools/import-zx-study.py /tmp/zx-bank.db --dry     # 只统计不写

源仓库：https://github.com/132465798a/zx-study （MIT，build/bank.db 约 37 MB）
题库内容版权声明为「来自公开数据集与内置生成器，仅供学习交流」，
所以本脚本只在**免费分发**的前提下使用；商用要先换源。

为什么不直接搬 zx 的结构：
  本地题型只有 CHOICE / FILL / JUDGE / WORD / CALC / OTHER 六类，
  渲染层不认连线题、也不认 LaTeX，所以这里做一次有损但保守的映射，
  映射不了的（matching 连线）直接丢掉 —— 宁可少几百道，也不印半道题。

顺手做的事（都是本项目自己的规矩）：
  · 判断题 0/1 转成 √/×（zx 里 0=对、1=错，跟直觉相反）
  · 看拼音写词语把拼音拼回题干（zx 把拼音放在 options 里）
  · 带 $$LaTeX$$ 的竞赛题一律丢（本项目的 PDF 不渲染公式）
  · 英语模板噪声题丢掉（"The example is snowy. 这句话的意思是？"这类）
  · 同一题干+选项只留一条
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB_OUT = ROOT / 'web' / 'data' / 'papers' / 'questions.json'
APP_OUT = ROOT / 'app' / 'src' / 'main' / 'assets' / 'papers' / 'questions.json'

SUBJECT_CODE = {1: 'CHINESE', 2: 'MATH', 3: 'ENGLISH'}
SUBJECT_CN = {1: '语文', 2: '数学', 3: '英语'}

# 新增题目的来源编号从 1000 起，原来的 100~4xx 是下载来的试卷
SRC_BASE = 1000

# 英语模板噪声：这些句子是程序填的，语义不通，不能印到纸上
EN_NOISE = re.compile(r'(这句话的意思是|shows an action in this sentence|本课中很重要'
                      r'|我在动物园看到|午饭我喝了汤|衣服在本课中很重要)')
# LaTeX 公式：本项目的排版引擎只会印文本
LATEX = re.compile(r'\$\$|\$[^$]+\$|\\frac|\\times')
# 明显不是给小学生做的（竞赛卷标记、论文式题干）
COMPETITION = re.compile(r'(卷第\s*\d+\s*题|奥数|竞赛|华杯|希望杯)')


def norm(s: str) -> str:
    """去重用：抹掉空白与全角半角差异"""
    return re.sub(r'\s+', '', s or '')


def dedupe_key(stem: str, options) -> str:
    return norm(stem) + '|' + norm(json.dumps(options, ensure_ascii=False) if options else '')


def as_list(v):
    """zx 的 answer/options 有的是 JSON、有的是裸文本"""
    if v is None:
        return []
    s = str(v).strip()
    if not s:
        return []
    if s[0] in '[{':
        try:
            return json.loads(s)
        except json.JSONDecodeError:
            return [s]
    return [s]


def letter_answer(idx_list, options) -> str:
    letters = 'ABCDEFGH'
    return ''.join(letters[i] for i in sorted(idx_list) if isinstance(i, int) and 0 <= i < len(options))


def convert(row) -> dict | None:
    """一行 zx question → 本项目的一条题，转不了返回 None"""
    qid, sid, grade, qtype, stem, options_raw, answer, difficulty = (
        row['id'], row['subject_id'], row['grade'], row['type'], row['stem'],
        row['options'], row['answer'], row['difficulty'])
    subject = SUBJECT_CODE.get(sid)
    if not subject:
        return None
    if not (1 <= grade <= 6):
        return None
    stem = (stem or '').strip()
    if len(stem) < 4 or len(stem) > 400:
        return None
    if LATEX.search(stem):
        return None
    if COMPETITION.search(stem):
        return None
    if subject == 'ENGLISH' and EN_NOISE.search(stem + ' ' + str(options_raw or '')):
        return None

    options = as_list(options_raw)
    ans = as_list(answer)
    out = {'g': grade, 's': subject, 'sec': '', 'src': ''}

    if qtype == 'single':
        if len(options) < 2 or not ans:
            return None
        try:
            idx = [int(x) for x in ans]
        except (TypeError, ValueError):
            return None
        letters = letter_answer(idx, options)
        if not letters:
            return None
        out.update(t='CHOICE', q=stem, o=[str(x) for x in options], a=letters)

    elif qtype == 'multiple':
        if len(options) < 2 or not ans:
            return None
        try:
            idx = [int(x) for x in ans]
        except (TypeError, ValueError):
            return None
        letters = letter_answer(idx, options)
        if not letters:
            return None
        out.update(t='CHOICE', q=f'（多选）{stem}', o=[str(x) for x in options], a=letters)

    elif qtype == 'judge':
        # zx：0 = 题干正确，1 = 题干错误。跟直觉相反，这里转成纸面上的 √ / ×
        code = str(ans[0]).strip() if ans else ''
        if code not in ('0', '1'):
            return None
        out.update(t='JUDGE', q=stem, a='√' if code == '0' else '×', o=[])

    elif qtype == 'fill':
        if not ans:
            return None
        # 组词题答案是开放列表（["看到|收看|看病"]），纸上只印参考，用「或」连
        text = str(ans[0])
        alts = [x for x in text.split('|') if x.strip()]
        out.update(t='FILL', q=stem if stem.endswith(('_', '＿')) else stem + '　（　　）',
                   a=' 或 '.join(alts[:3]), o=[])

    elif qtype == 'pinyin_word':
        # options 里是 {"words":[{"pinyin":["yuè","liàng"],"chars":["月","亮"]}]}
        words = []
        if isinstance(options_raw, str) and options_raw.startswith('{'):
            try:
                for w in json.loads(options_raw).get('words', []):
                    words.append(''.join(w.get('chars', [])))
            except json.JSONDecodeError:
                pass
        if not words:
            words = [re.sub(r'[\[\]"]', '', x) for x in ans]
        words = [w for w in words if w]
        if not words:
            return None
        # 题干只剩「看拼音写词语」，把拼音拼回去才是一道能印的题
        try:
            py = []
            if isinstance(options_raw, str) and options_raw.startswith('{'):
                for w in json.loads(options_raw).get('words', []):
                    py.append(' '.join(w.get('pinyin', [])))
        except json.JSONDecodeError:
            py = []
        if py:
            out.update(t='FILL', q=f'看拼音写词语：{"　".join(py)}（　　）', a=' 或 '.join(words[:3]), o=[])
        else:
            out.update(t='FILL', q=f'看拼音写词语：{stem}（　　）', a=' 或 '.join(words[:3]), o=[])

    elif qtype == 'cloze':
        if not ans:
            return None
        # 题干里 ___{1} 是挖空占位，纸面上就是下划线
        text = re.sub(r'_+\{\d+\}', '＿＿', stem)
        out.update(t='FILL', q=text, a=' 或 '.join(str(x) for x in ans[:3]), o=[])

    elif qtype == 'calculation':
        if not ans:
            return None
        has_cn = bool(re.search(r'[\u4e00-\u9fa5]', stem))
        # 有汉字的当应用题（要写过程），纯算式的当计算题
        out.update(t='WORD' if has_cn else 'CALC', q=stem,
                   a=' '.join(str(x) for x in ans), o=[])

    elif qtype == 'short_answer':
        if isinstance(ans, dict):          # {"reference": "…", "keywords": [...]}
            ans = [ans.get('reference') or (ans.get('keywords') or [''])[0]]
        if not ans:
            return None
        ref = ''
        if isinstance(ans[0], dict):
            ref = str(ans[0].get('reference') or (ans[0].get('keywords') or [''])[0])
        else:
            ref = str(ans[0])
        if not ref.strip():
            return None
        out.update(t='OTHER', q=stem, a=ref.strip(), o=[])

    elif qtype == 'sorting':
        # 排序题：题干只有指令（「请排列成通顺的句子：」），词卡在 options 里。
        # 纸面上把词卡按顺序印出来，孩子写序号或直接抄成句子，答案页给正确顺序。
        if len(options) < 2 or not ans:
            return None
        cards = '　'.join(str(x) for x in options)
        out.update(t='FILL', q=f'{stem.rstrip("：:")}：{cards}　（填序号）',
                   a='　'.join(str(x) for x in ans), o=[])

    else:
        # matching（连线）在本地没有对应渲染，放弃
        return None

    if not out.get('q') or not out.get('a'):
        return None
    # 半角连续下划线统一成全角（跟自编题的挖空写法一致，印出来是一条实线）
    out['q'] = re.sub(r'_{2,}', '＿＿＿＿', out['q'])
    # 难度 1~3：出卷时按「由易到难」排布要用它；老题库没有这个字段，按 2 算
    out['d'] = difficulty if isinstance(difficulty, int) and 1 <= difficulty <= 3 else 2
    return out


def load_db(path: Path):
    con = sqlite3.connect(f'file:{path}?mode=ro', uri=True)
    con.row_factory = sqlite3.Row
    rows = con.execute(
        'select id, subject_id, grade, type, stem, options, answer, difficulty '
        'from question order by subject_id, grade, id').fetchall()
    con.close()
    return rows


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('db', type=Path)
    ap.add_argument('--dry', action='store_true', help='只统计，不写文件')
    args = ap.parse_args()

    doc = json.loads(WEB_OUT.read_text(encoding='utf-8'))
    # 幂等：每次都从「下载来的原始卷」重建，把上一次合并进去的 zx 题剥掉。
    # 否则重跑一次就变成「老题库里已经有 6.8 万道」，全被判重复，新增 0。
    old_items = [q for q in doc['items']
                 if not (str(q.get('src') or '').isdigit() and int(q['src']) >= SRC_BASE)]
    sources = {k: v for k, v in (doc.get('sources') or {}).items()
               if not (str(k).isdigit() and int(k) >= SRC_BASE)}
    print(f'现有原始题库 {len(old_items)} 道 · {len(sources)} 份来源')

    # ---------------- 1. 先按同一套闸门过一遍老题 ----------------
    # 老题库里 93 道左右的语文题答案是整体错位的（答案是下一题的评分标准），
    # 这些印到纸上就是「这软件出错了」，顺手在这里清掉。
    SUBJECTIVE = re.compile(r'(示例|评分标准|酌情|答案不唯一|合理即可|言之有理|每空|每小?题|得分)')
    ASK = re.compile(r'(写|说|画|连|仿照|补充|读一读|口语|看图|观察|扩写|缩写|改写|解释|举|谈谈|想一想|理解|体会)')

    def old_is_bad(q) -> bool:
        a = str(q.get('a') or '')
        if not a.strip():
            return True
        if SUBJECTIVE.search(a) and not ASK.search(q.get('q') or ''):
            return True
        return False

    kept_old, dropped_old, stats_fixed = [], 0, 0
    CN_ORDINAL = re.compile(r'^\s*[一二三四五六七八九十]{1,3}\s*、\s*')
    for q in old_items:
        if old_is_bad(q):
            dropped_old += 1
            continue
        # 切卷时把大题的「六、」带进了题干 —— 印到纸上就是一个假的第六大题，
        # 排版层还会把它当成大题标题。序号剥掉，题目本身留着。
        if CN_ORDINAL.match(q.get('q') or ''):
            q = {**q, 'q': CN_ORDINAL.sub('', q['q'])}
            stats_fixed += 1
        kept_old.append(q)
    print(f'老题保留 {len(kept_old)} 道，闸门丢掉 {dropped_old} 道（答案错位/评分标准混进答案）'
          + (f'，剥掉大题序号 {stats_fixed} 道' if stats_fixed else ''))

    # ---------------- 2. 转换新题 ----------------
    rows = load_db(args.db)
    print(f'zx 题库 {len(rows)} 行')

    seen = {dedupe_key(q.get('q', ''), q.get('o')) for q in kept_old}
    new_items, stats = [], {}
    for row in rows:
        q = convert(row)
        if not q:
            stats['转不了/被过滤'] = stats.get('转不了/被过滤', 0) + 1
            continue
        key = dedupe_key(q['q'], q.get('o'))
        if key in seen:
            stats['重复'] = stats.get('重复', 0) + 1
            continue
        seen.add(key)
        # 来源按「科目·年级·题型」分组：出卷时按来源轮转，能把同一生成家族的
        # 相似题拆开，不至于一张卷五道题全是「下列计算结果正确的有」
        slug = f'{q["s"]}-{q["g"]}-{q["t"]}'
        if slug not in stats.setdefault('_slug', {}):
            src = str(SRC_BASE + len(stats['_slug']))
            stats['_slug'][slug] = src
            sources[src] = f'zx-study 正贤题库（MIT）· {SUBJECT_CN[row["subject_id"]]} {q["g"]} 年级 · {q["t"]}'
        q['src'] = stats['_slug'][slug]
        stats[slug] = stats.get(slug, 0) + 1
        new_items.append(q)

    slug_map = stats.pop('_slug')
    total = len(kept_old) + len(new_items)
    print(f'新增 {len(new_items)} 道，合计 {total} 道')
    print('过滤统计：', {k: v for k, v in stats.items() if k in ('转不了/被过滤', '重复')})
    by_subject = {}
    for q in new_items:
        by_subject[q['s']] = by_subject.get(q['s'], 0) + 1
    print('新增按科目：', by_subject)
    by_type = {}
    for q in new_items:
        by_type[q['t']] = by_type.get(q['t'], 0) + 1
    print('新增按题型：', by_type)
    print(f'来源新增 {len(slug_map)} 组')

    if args.dry:
        print('\n--dry：没写文件')
        return 0

    doc['items'] = kept_old + new_items
    doc['count'] = total
    doc['sources'] = sources
    doc['note'] = (
        '题目来自两部分：① downloads/papers 里下载的公开分享试卷，由 tools/build-question-bank.py 自动切分；'
        '② zx-study 正贤学习平台（MIT）的 bank.db，由 tools/import-zx-study.py 转换合并。'
        '题干保留原文，答案取卷内给出的参考答案；没有给出答案的题 a 字段为空。'
    )
    text = json.dumps(doc, ensure_ascii=False, separators=(',', ':'))
    for out in (WEB_OUT, APP_OUT):
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding='utf-8')
        print(f'写入 {out.relative_to(ROOT)} · {len(text.encode()) // 1024} KB')
    return 0


if __name__ == '__main__':
    sys.exit(main())
