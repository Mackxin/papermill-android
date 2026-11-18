#!/usr/bin/env python3
"""
把 shijuan.on2on.cn「小学试卷库」的下载资源抓成本地索引。

原站有两部分内容：
  1. /item/*.html  15 个知识点自助出题页 —— 规则已逐条搬进 web/js/gen/points.js
     与 app/.../MathPointGenerators.kt，不需要这里抓。
  2. /shijuan/api/article/page  776 条试卷资源 —— 站内**没有题目正文**，
     每条只有一个标题 + 年级/科目/类型 + 一个网盘链接。本项目只做**索引**：
     存标题和链接，用户点了自己去网盘取文件，不转载、不打包任何试题文件。

字段压到最短（776 条进单文件版不能占太多体积）：
  i=站内 id  n=标题  g=年级  s=科目  t=类型  m=学期  y=发布年份  u=网盘链接

用法：
  python3 tools/fetch-papers.py            # 抓全量并写两个端
  python3 tools/fetch-papers.py --dry      # 只打印统计，不落盘
"""

import json
import os
import sys
import urllib.request

BASE = 'https://shijuan.on2on.cn'
PAGE_API = BASE + '/shijuan/api/article/page'
PAGE_SIZE = 50

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_WEB = os.path.join(ROOT, 'web', 'data', 'papers', 'library.json')
OUT_APP = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'papers', 'library.json')

SUBJECT_CODE = {
    '语文': 'CHINESE',
    '数学': 'MATH',
    '英语': 'ENGLISH',
    '科学': 'SCIENCE',
}


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 papermill-index'})
    with urllib.request.urlopen(req, timeout=30) as res:
        return json.loads(res.read().decode('utf-8'))


def fetch_all():
    rows = []
    page = 1
    pages = 1
    while page <= pages:
        url = f'{PAGE_API}?pageNum={page}&pageSize={PAGE_SIZE}'
        ret = get(url)
        if ret.get('code') != 200:
            raise SystemExit(f'第 {page} 页失败：{ret.get("msg")}')
        data = ret['data']
        pages = int(data.get('pages') or 1)
        rows.extend(data.get('list') or [])
        page += 1
    # 同一条可能翻页时重复（数据在翻页中途被编辑），按 id 去重
    seen = set()
    uniq = []
    for r in rows:
        rid = str(r.get('id'))
        if rid in seen:
            continue
        seen.add(rid)
        uniq.append(r)
    return uniq


def compact(r):
    subject = r.get('subjectName') or ''
    year = (r.get('publishTimeText') or '')[:4]
    return {
        'i': str(r.get('id')),
        'n': (r.get('title') or '').strip(),
        'g': int(r.get('gradeCode') or 0),
        's': SUBJECT_CODE.get(subject, subject),
        't': r.get('typeName') or '',
        'm': r.get('gradeSubName') or '',
        'y': int(year) if year.isdigit() else 0,
        'u': r.get('netdiskDownloadUrl') or '',
    }


def main():
    rows = fetch_all()
    items = [compact(r) for r in rows]
    items = [x for x in items if x['n'] and x['u']]

    # 年级 / 科目 / 类型 三个筛选维度按固定顺序出，界面直接照着渲染
    grades = sorted({x['g'] for x in items})
    subjects = ['MATH', 'CHINESE', 'ENGLISH', 'SCIENCE']
    subjects = [s for s in subjects if any(x['s'] == s for x in items)]
    types = []
    for x in items:
        if x['t'] and x['t'] not in types:
            types.append(x['t'])

    payload = {
        'source': BASE + '/index.html',
        'note': '仅索引原站标题与网盘链接，不转载文件；链接由第三方网盘提供，失效属正常',
        'count': len(items),
        'facets': {'grades': grades, 'subjects': subjects, 'types': types},
        'items': items,
    }

    per_grade = {g: sum(1 for x in items if x['g'] == g) for g in grades}
    per_subject = {s: sum(1 for x in items if x['s'] == s) for s in subjects}
    print(f'共 {len(items)} 条')
    print('年级', per_grade)
    print('科目', per_subject)
    print('类型', {t: sum(1 for x in items if x['t'] == t) for t in types})

    if '--dry' in sys.argv:
        return

    text = json.dumps(payload, ensure_ascii=False, separators=(',', ':'))
    for path in (OUT_WEB, OUT_APP):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(text)
        print('写入', os.path.relpath(path, ROOT), f'{len(text.encode()) / 1024:.1f} KB')


if __name__ == '__main__':
    main()
