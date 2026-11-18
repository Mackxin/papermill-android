#!/usr/bin/env python3
"""读 .docx 的纯文本（只用标准库，不装 python-docx）。

为什么不用 python-docx：本机没装任何第三方库，为这一个功能去装一整套依赖不值当。
.docx 本质是个 zip，正文在 word/document.xml，用 zipfile + ElementTree 就够。

抽出来的东西要满足后面「切题」的需要，所以有三处讲究：
  · 段落要**按行还原** —— 试卷的题号、题干、选项常常靠软回车（<w:br/>）分行，
    整段直接拼成一个字符串会把「1. 3+5=（ ）  2. 7-2=（ ）」糊成一行，切不开。
  · 表格要**按格还原** —— 一二年级大量题目排成表格（一行 4 道口算），
    丢掉表格等于丢掉半张卷子。
  · 公式要**线性化** —— 数学卷的分数、根号、上下标是 OMML（Word 的公式对象），
    文字在 <m:t> 里，不在 <w:t> 里。不管它的话，卷面上就是
    「已知，则x的值为____」—— 算式整块消失，成了残题。见 _omath_text。
  · 抽不出来的一律打**占位符** —— 图片（w:drawing / w:pict）和 MathType 那种
    OLE 公式对象（w:object）在 docx 里只是二进制 + 缩略图，文本里没有对应内容。
    这里插一个 \ufffc（OBJECT REPLACEMENT CHARACTER）标出「此处原本有东西」，
    切题时凡是带占位符的题直接剔除 —— 比猜「如图」两个字准得多：
    「一汽车▯小时行了▯千米」这种悄悄丢数字的残题也能一并拦住。
"""

import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
M = '{http://schemas.openxmlformats.org/officeDocument/2006/math}'

# 抽不出文字的东西（图片 / OLE 公式对象）留下的占位符
OBJ = '\ufffc'

# 单个数字的上/下标换成真上标字符，纸面上比 a^2 好看
SUP = str.maketrans('0123456789+-n', '⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻ⁿ')
SUB = str.maketrans('0123456789', '₀₁₂₃₄₅₆₇₈₉')


def _join(node):
    """把一个 m:xxx 节点下的内容全部线性化拼起来。"""
    if node is None:
        return ''
    return ''.join(_omath_text(c) for c in node)


def _wrap(s):
    """多字符的分子/分母加括号，免得 1+x/2 被看成 1 + x/2。"""
    s = s.strip()
    if not s:
        return ''
    if len(s) == 1 or (s.isalnum() and ' ' not in s):
        return s
    return '(' + s + ')'


def _omath_text(node):
    """OMML 公式 → 一行线性文本（分数线写成 /，根号写成 √()）。"""
    tag = node.tag
    if tag == M + 't':
        return node.text or ''
    if tag == M + 'f':                       # 分数
        return f'{_wrap(_join(node.find(M + "num")))}/{_wrap(_join(node.find(M + "den")))}'
    if tag == M + 'sSup':                    # 上标
        e = _join(node.find(M + 'e'))
        sup = _join(node.find(M + 'sup')).strip()
        return e + (sup.translate(SUP) if re.fullmatch(r'[0-9+\-n]{1,2}', sup) else '^' + sup)
    if tag == M + 'sSub':                    # 下标
        e = _join(node.find(M + 'e'))
        sub = _join(node.find(M + 'sub')).strip()
        return e + (sub.translate(SUB) if re.fullmatch(r'[0-9]{1,2}', sub) else '_' + sub)
    if tag == M + 'sSubSup':
        e = _join(node.find(M + 'e'))
        return f'{e}^{_join(node.find(M + "sup"))}_{_join(node.find(M + "sub"))}'
    if tag == M + 'rad':                     # 根号
        return '√(' + _join(node.find(M + 'e')) + ')'
    if tag == M + 'd':                       # 括号，内容在 m:e 里
        inner = ''.join(_join(c) for c in node if c.tag == M + 'e')
        return '(' + inner + ')'
    if tag == M + 'nary':                    # ∑ ∏ ∫，带上自己的上下限
        op = '∑'
        return (op + _join(node.find(M + 'sub')) + _join(node.find(M + 'sup'))
                + _join(node.find(M + 'e')))
    # m:r / m:func / m:box / m:bar 之类：往下递归就够
    return ''.join(_omath_text(c) for c in node)


def _para_text(p):
    """一个 <w:p> 里的文字，按文档顺序拼，软回车变换行，公式线性化。"""
    out = []

    def walk(node):
        for child in node:
            tag = child.tag
            if tag == W + 't':
                out.append(child.text or '')
            elif tag == W + 'tab':
                out.append('\t')
            elif tag in (W + 'br', W + 'cr'):
                out.append('\n')
            elif tag == M + 'oMath':
                out.append(_omath_text(child))
            elif tag in (W + 'drawing', W + 'pict', W + 'object'):
                # 图片 / MathType 公式对象：正文里没有对应文字，只留个占位符
                out.append(OBJ)
            else:
                walk(child)

    walk(p)
    return ''.join(out)


def _table_rows(tbl):
    """表格 → 行列表，每行是单元格文字列表。"""
    rows = []
    for tr in tbl.findall(W + 'tr'):
        cells = []
        for tc in tr.findall(W + 'tc'):
            parts = [_para_text(p) for p in tc.findall(W + 'p')]
            cells.append(' '.join(x.strip() for x in parts if x.strip()))
        rows.append(cells)
    return rows


def read_docx(path):
    """返回 [(类型, 内容)]，类型是 'p' 段落 / 'tbl' 表格（内容是行列表）。"""
    with zipfile.ZipFile(path) as z:
        xml = z.read('word/document.xml')
    root = ET.fromstring(xml)
    body = root.find(W + 'body')
    blocks = []
    if body is None:
        return blocks
    for child in body:
        if child.tag == W + 'p':
            blocks.append(('p', _para_text(child)))
        elif child.tag == W + 'tbl':
            blocks.append(('tbl', _table_rows(child)))
    return blocks


def to_lines(blocks):
    """压成「一行一句」的纯文本行，表格按行展开、格间用制表符。"""
    lines = []
    for kind, val in blocks:
        if kind == 'p':
            for line in val.split('\n'):
                t = re.sub(r'[ \u00a0]+', ' ', line).strip()
                if t:
                    lines.append(t)
        else:
            for row in val:
                cells = [c for c in row if c]
                if cells:
                    lines.append('\t'.join(cells))
    return lines


def dump(path, show_table=True):
    blocks = read_docx(path)
    lines = to_lines(blocks)
    nchars = sum(len(x) for x in lines)
    print(f'# {Path(path).name}')
    print(f'# 块 {len(blocks)} · 行 {len(lines)} · 字 {nchars}')
    print('-' * 60)
    for line in lines:
        if not show_table and '\t' in line:
            continue
        print(line)
    return lines


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    p = sys.argv[1]
    if p.endswith('.docx'):
        dump(p)
    else:
        print('目前只认 .docx')
