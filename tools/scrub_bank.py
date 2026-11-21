#!/usr/bin/env python3
"""真题库题干清洗 —— 把「PDF/docx 提取时压扁的版面」还原成能上纸的题干。

为什么单独一个脚本，而不是塞进 build-question-bank.py：
  · 题库有两个来源（下载的 docx + zx-study 的 bank.db），清洗规则对两边一样；
  · 两端的 questions.json 是同一份内容，必须逐字节一致 —— 只在一处改容易漏；
  · 幂等：重复跑结果不变，所以随时可以拿来兜底（换数据源、老库回炉）。

三类毛病，三种处理：

  1. 能修 → 修
     `~\\uline{~~~~~~~~~~}~`、`\\u3000 \\u3000`、制表符 这些是原卷的**填空横线**和
     **单元格分隔**，提取成了空白。换成看得见的 `＿＿＿`（填空）或换行（并列小题），
     题就恢复正常了。

  2. 修不干净 → 剔
     清洗一遍还剩下制表符 / 括号外的连续全角空格 / LaTeX 命令，说明结构已经丢了。

  3. 结构必丢 → 剔
     「在横线里填上＞＜＝」这一类，原卷是表格，每格两个数。提取后
     `8+4 ⎵⎵ 1220 ⎵⎵ 12` 里的「12」「20」黏成了一串 —— 横线在哪、怎么分组，
     都没法还原。印到纸上就是「6 9 4+3 5 8+4 1220 12」，孩子不知道要填什么。
     这类题不救，交给自编的比大小生成器（它出的题带 ◯ 符号，位置明确）。
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGETS = [
    ROOT / 'web/data/papers/questions.json',
    ROOT / 'app/src/main/assets/papers/questions.json',
]

SPACE_RUN = re.compile(r'[\u3000 ]{2,}')
# `~ ~ ~ ~` 这种是 docx 的缩进/条目分隔（LaTeX 的不断行空格被连写），
# 它不是填空位 —— 当成填空会印出「成绩＿＿＿王教官说」，很荒唐
TILDE_RUN = re.compile(r'(?:[ \u3000]*~[ \u3000]*){2,}')
# LaTeX 的填空横线：`~\uline{~~~~~~}~` / `\blank{...}`
ULINE = re.compile(r'~?\s*\\u?line\{[.~_\- ]*\}[~]?|~?\\blank\{[.~_\- ]*\}[~]?')

# 能认出来的 LaTeX 命令就地翻译 —— 剔掉一道题的成本比翻译高得多。
# 注意 \\u3000 分隔符不能是「两个」全角空格：SPACE_RUN 会把它们当成填空位再替换一次。
LATEX_FIX = [
    (re.compile(r'\\\\(textbf|textit|text|mathrm|mathbf)\{([^{}]*)\}'), r'\2'),
    (re.compile(r'\\ldots|\\cdots|\\dots'), '……'),
    (re.compile(r'\\textasciitilde'), '~'),
    (re.compile(r'\\times'), '×'),
    (re.compile(r'\\div'), '÷'),
    (re.compile(r'\\pm'), '±'),
    (re.compile(r'\\%'), '%'),
    (re.compile(r'\\[,;:]'), ' '),
    (re.compile(r'\\quad|\\qquad'), '\u3000'),
]
# 其它 LaTeX 命令：宁可剔，也不要把 `\frac{1}{2}` 印到小学卷上
LATEX = re.compile(r'\\[a-zA-Z]+|~\\\\')
# 句末标点：空格串前面是它，说明这段空格本来是「换段」，不是填空位。
# 半角 `.` 与右括号也算 —— 中文卷里「(  ).  1(  )2」这种中英对照、
# 「（以英文字母作答）  In which of…」这种双语并列，都是靠句末标点断开的。
SENT_END = '。．！？!?；;：:”’」』"\'）).'
# 圈号选项：制表符两侧都是 ①②③ 开头的，那是横排选项，不是并列小题
CIRCLED = '①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳'
# 比较符号题（填＞＜＝）。这类题在 docx 里几乎都是表格，单元格丢了一格就废
COMPARE = re.compile(r'[＞＜]|填\s*[上]?\s*[“"\']?\s*＝')
# 「填比较符号」这类题专用：题干里有「填上＞＜」，比较位是 ○ / ◯ / 〇 / （）
COMPARE_TASK = re.compile(r'填\s*[上]?\s*[“"\']?\s*[＞＜]')
# 比较位：圆圈，或者**空**括号。`(12分)` 那种括号里有内容的不算
SLOT_RE = re.compile(r'[○◯〇]|\(\s*\)|（\s*）')
CMP_CHAR = re.compile(r'[＞＜=＝<>]')
BLANK = '＿＿＿'
LEFT_PAREN = '（(【['
RIGHT_PAREN = '）)】]'
# 小题编号「（1）」「(2)」：长空白后面接它，说明那是几条并列小题的分界
ITEM_NO = re.compile(r'[（(]\s*\d{1,2}\s*[）)]')


def paren_depths(s: str):
    """每个字符位置处在第几层括号里 —— 括号内的空白是答题位，不能动。"""
    depth = 0
    out = []
    for ch in s:
        if ch in LEFT_PAREN:
            out.append(depth)
            depth += 1
        elif ch in RIGHT_PAREN:
            depth = max(0, depth - 1)
            out.append(depth)
        else:
            out.append(depth)
    return out


def bare_runs(s: str):
    """括号外的空格串。

    只看「前后紧邻是不是括号」是不够的：`逮捕(dǎi　　dài)` 里的空白两头都是字母，
    但它在括号里，是「两个读音二选一」，换成填空横线就变成了「dǎi＿＿＿dài」。
    所以要按括号层数判断。
    """
    depths = paren_depths(s)
    for m in SPACE_RUN.finditer(s):
        before = s[m.start() - 1] if m.start() > 0 else ''
        after = s[m.end():m.end() + 1]
        if before in LEFT_PAREN or after in RIGHT_PAREN:
            continue
        if depths[m.start()] > 0:
            continue
        yield m


def run_kind(s, m) -> str:
    """这段空白在原卷里是什么？决定它换成什么。"""
    before = s[m.start() - 1] if m.start() > 0 else ''
    after = s[m.end():m.end() + 1]
    if not before:
        return 'indent'          # 行首缩进 → 吞掉
    if ITEM_NO.match(s, m.end()):
        return 'newline'         # 后面接「（1）」「(2)」→ 这是几条并列的小题
    if after and after in CIRCLED:
        return 'option'          # 后面紧跟 ①②③ → 选项之间的空白
    if before in SENT_END:
        return 'newline'         # 句末标点之后 → 换段
    return 'blank'               # 其余 → 这就是让孩子写字的横线


def fix_space_runs(s: str) -> str:
    """空格串 → 填空横线 / 换行 / 选项分隔；下划线旁的保持原样。"""
    out = []
    pos = 0
    for m in bare_runs(s):
        before = s[m.start() - 1] if m.start() > 0 else ''
        # 紧挨下划线的是已经在写的横线，别再加一条
        if before in '＿_' or s[m.end():m.end() + 1] in '＿_':
            continue
        out.append(s[pos:m.start()])
        out.append({'indent': '', 'option': '\u3000\u3000', 'newline': '\n'}.get(run_kind(s, m), BLANK))
        pos = m.end()
    out.append(s[pos:])
    return ''.join(out)


def fix_tilde_runs(s: str) -> str:
    """`~ ~ ~ ~` 缩进串 → 按原意换成换行 / 选项分隔 / 答题括号。

    括号里的 `（~~~~ ）` 是「括号内留空」，换成 `（　　）` ——
    卷面上标准的答题括号就是它，比空括号 `（）` 清楚，比换行体面。
    """
    out = []
    pos = 0
    for m in TILDE_RUN.finditer(s):
        before = s[m.start() - 1] if m.start() > 0 else ''
        after = s[m.end():m.end() + 1]
        out.append(s[pos:m.start()])
        if before in LEFT_PAREN or after in RIGHT_PAREN:
            out.append('\u3000\u3000')
        elif after and after in CIRCLED:
            out.append('\u3000')
        else:
            out.append('\n' if before in SENT_END else '')
        pos = m.end()
    out.append(s[pos:])
    return ''.join(out)


def _is_option_chain(parts):
    """这一段制表符切出来的是「横排选项」还是「并列的小题」？

    横排长这样：`…错误的是( )。①2是加数 ②11是差 ③13是被减数`
    第一段是题干（带第一个选项），从第二段起全是 ①②③ 开头 —— 只在**同一行**
    并列的才是选项，所以横排。

    「（1）」「(2)」这类小题编号一律换行：`（1）11×300＝ （2）300×70＝` 横排四道
    口算会挤成一行、还容易被当成填空位；换行虽然多吃几行纸，但绝不会读错。
    这条和 run_kind 里的 ITEM_NO 判断必须一致，否则跑第二遍结果又变 —— 不再幂等。
    """
    body = [p.strip() for p in parts[1:] if p.strip()]
    if not body:
        return False
    return all(p[:1] in CIRCLED for p in body)


def fix_tabs(s: str) -> str:
    """制表符 → 换行；横排的两段之间用两个全角空格连起来。

    这一步必须排在 fix_space_runs **之后**：两个全角空格正好是「连续空格」，
    先做的话会被当成填空位，变成「①2是加数＿＿＿②11是差」——选项里凭空多出横线。
    """
    parts = s.split('\t')
    if len(parts) < 2:
        return s
    sep = '\u3000\u3000' if _is_option_chain(parts) else '\n'
    return sep.join(p.strip() for p in parts)


def scrub_stem(stem: str) -> str:
    """单条题干的修复（不判断该不该留 —— 那是 why_drop 的事）。"""
    s = str(stem)
    s = ULINE.sub(BLANK, s)
    for pat, rep in LATEX_FIX:
        s = pat.sub(rep, s)
    # 缩进串先清掉：再往下走会被当成连续空格，变成一条凭空多出来的填空线
    s = fix_tilde_runs(s)
    # 空白先定性质，最后再拆制表符 —— 反过来，横排用的两个全角空格会被当成填空位
    s = fix_space_runs(s)
    s = fix_tabs(s)
    s = '\n'.join(line.strip() for line in s.split('\n'))
    s = re.sub(r'\n{2,}', '\n', s).strip()
    return s


def compare_task_broken(stem: str, answer: str) -> str:
    """「在○里填上＞＜＝」这类题单独查——它的粘连比别的题更隐蔽。

    原卷是表格，每格「数 ○ 数」，提取后格子之间的分隔丢了，于是两个小题黏成一段：
    `5+3○9-2 6-4○3-0 9+0○3+64+2○8-3`   ← 「3+6」与「4+2」黏在「3+64+2」
    `240×30（）24×300560÷28（）560÷14`   ← 「300」与「560」黏在「300560」
    这种题单看空白分不出真假（它只用了单个空格，不触发「连续空白」那条）。两条判据，任一命中就不要：

      1. 按空白切开后，某一格里出现了**两个以上**比较位 —— 一格只该有一道比较题；
      2. 比较位个数与答案里的符号个数对不上 —— 有一格被并掉了，答案对不上位置。

    两个坑都踩过：
      · 「在○里填上」这个 ○ 是题目里的指示符号，不是比较位 —— 得先切掉说明句再数，
        否则每道这类题的第一个「格」都算两个比较位，好题全被冤枉；
      · `(12分)` 这种括号里**有内容**的也不是比较位 —— 只认空括号 `（）` / `()`。
    """
    if not COMPARE_TASK.search(stem):
        return ''
    # 说明句（「在○里填上“＞”“＜”或“＝”。」）之后的才是真正的比较内容
    cut = max(stem.find('。'), stem.find('：'), stem.find(':'))
    body = stem[cut + 1:] if cut >= 0 and len(stem) - cut > 8 else stem
    slots = len(SLOT_RE.findall(body))
    if slots < 2:
        return ''
    for token in re.split(r'[\s\u3000]+', body):
        if len(SLOT_RE.findall(token)) >= 2:
            return '表格压扁（比较位粘连）'
    # 答案基本是纯符号时才敢类比个数；答案里有文字说明的，这套算法不成立
    answer = str(answer or '')
    marks = CMP_CHAR.findall(answer)
    if marks and len(re.sub(r'[\s\u3000，,、；;。.]', '', answer)) <= len(marks) + 2:
        if len(marks) != slots:
            return f'表格压扁（比较位 {slots} / 答案 {len(marks)}）'
    return ''


def residual_space(s: str) -> bool:
    """清洗之后还剩不剩「本来该被修掉、却没修掉」的空格串。

    横排选项用的两个全角空格是故意留的（`①2是加数　　②11是差`），不算残留；
    其余括号外的长空白说明这条题的结构没认出规则，留着会印成一片糊。
    """
    for m in bare_runs(s):
        before = s[m.start() - 1] if m.start() > 0 else ''
        after = s[m.end():m.end() + 1]
        if before in '＿_':
            continue
        if after and (after in CIRCLED or ITEM_NO.match(s, m.end())):
            continue
        return True
    return False


def why_drop(raw: str, stem: str, answer: str = '') -> str:
    """返回剔除原因；合格返回空串。

    判断分两段：先看**原始题干**的结构（修复前就丢了的，修不出来），
    再看**清洗后**有没有残留（修不干净的）。
    """
    if not stem:
        return '空题干'
    # 比较符号题本来靠表格定位，空格串一出现就说明分组已丢
    if COMPARE.search(raw) and (list(bare_runs(raw)) or '\t' in raw):
        return '表格压扁（填符号题）'
    # 只用单个空格分隔的比较题，另有一套查法（见 compare_task_broken）
    why = compare_task_broken(stem, answer)
    if why:
        return why
    if '\t' in stem:
        return '残留制表符'
    if residual_space(stem):
        return '残留连续空格'
    if LATEX.search(stem):
        return 'LaTeX 残留'
    if len(re.sub(r'\s', '', stem)) < 4:
        return '题干过短'
    return ''


def scrub_doc(doc):
    """就地把一份题库文档洗净，返回统计。导入脚本调它，命令行也调它。"""
    kept = []
    changed = 0
    reasons = {}
    for q in doc.get('items', []):
        raw = q['q']
        stem = scrub_stem(raw)
        why = why_drop(raw, stem, q.get('a', ''))
        if why:
            reasons[why] = reasons.get(why, 0) + 1
            continue
        if stem != raw:
            changed += 1
            q = {**q, 'q': stem}
        kept.append(q)
    before = len(doc.get('items', []))
    doc['items'] = kept
    doc['count'] = len(kept)
    return {'before': before, 'after': len(kept), 'changed': changed, 'dropped': reasons}


def run(path):
    """对单个题库文件跑一遍，就地把结果写回去。"""
    doc = json.loads(path.read_text(encoding='utf-8'))
    st = scrub_doc(doc)
    path.write_text(json.dumps(doc, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    dropped = st['before'] - st['after']
    print(f'{path}: {st["before"]} → {st["after"]}'
          f'（剔除 {dropped} 道，改写题干 {st["changed"]} 道）')
    return st


def main() -> int:
    for path in TARGETS:
        run(path.relative_to(ROOT) if path.is_absolute() else path)
    return 0


if __name__ == '__main__':
    sys.exit(main())
