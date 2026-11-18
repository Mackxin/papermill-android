#!/usr/bin/env python3
"""一条命令重建题库与单文件版。

    python3 tools/rebuild.py            # 切题 → 体检 → 重建单文件
    python3 tools/rebuild.py --audit    # 只体检，不切题也不重建

以前这三步是手敲三条命令，而且「体检」这一步全靠肉眼抽查——
题库是全量重生成的，任何一条过滤规则写宽了都会悄悄放进残题，
写窄了又会让某个年级组合变成空池，而界面上只会显示「0 道」。
这里把三件事串起来，空池直接在终端点名。

退出码：0 正常；1 体检发现空池（题还是生成了，但值得看一眼）。
"""

import json
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BANK = ROOT / 'web' / 'data' / 'papers' / 'questions.json'

# 默认勾上的四类。计算/其他默认不勾，所以它们为 0 不算问题。
DEFAULT_FAMS = [('填空', 'FILL'), ('选择', 'CHOICE'), ('判断', 'JUDGE'), ('应用', 'WORD')]
GRADE_LABEL = '一二三四五六'


def run(script):
    print(f'\n▶ {script}')
    r = subprocess.run([sys.executable, str(ROOT / 'tools' / script)], cwd=ROOT)
    if r.returncode != 0:
        print(f'✗ {script} 失败（退出码 {r.returncode}）')
        sys.exit(1)


def audit():
    if not BANK.exists():
        print('✗ 还没有题库，先跑一次切题')
        return 1
    doc = json.loads(BANK.read_text(encoding='utf-8'))
    items = doc['items']
    no_ans = [q for q in items if not q.get('a')]
    print(f'\n▶ 题库体检')
    print(f'  题数 {len(items)} · {BANK.stat().st_size // 1024} KB')
    print(f'  无答案 {len(no_ans)} 道', '✅' if not no_ans else '✗ 题库应当全部带答案')

    pools = defaultdict(Counter)
    for q in items:
        pools[(q['g'], q['s'])][q['t']] += 1

    print('\n  年级 科目      总计   ' + ' '.join(f'{n:>4}' for n, _ in DEFAULT_FAMS) + '   其他')
    empty = []
    for key in sorted(pools):
        c = pools[key]
        g, s = key
        row = [c[t] for _, t in DEFAULT_FAMS]
        other = sum(v for k, v in c.items() if k not in {t for _, t in DEFAULT_FAMS})
        name = f'{GRADE_LABEL[g - 1] if 1 <= g <= 6 else g} 年级 {s:<8}'
        print(f'  {name} {sum(c.values()):5d}  ' + ' '.join(f'{v:4d}' for v in row) + f' {other:6d}')
        if not any(row):
            empty.append(f'{name}（默认四类全 0）')
        elif sum(row) < 20:
            empty.append(f'{name}（默认四类只有 {sum(row)} 道）')

    if empty:
        print('\n  ⚠ 这些组合凑不出像样的真题卷，界面上那一类会置灰：')
        for e in empty:
            print('    ·', e)
        return 1
    print('\n  ✅ 每个年级科目都有能用的真题池')
    return 0


def main():
    only_audit = '--audit' in sys.argv
    if not only_audit:
        run('build-question-bank.py')
    code = audit()
    if not only_audit:
        run('build-single.py')
    print('\n完成。' if code == 0 else '\n完成，但体检有告警（见上）。')
    return code


if __name__ == '__main__':
    sys.exit(main())
