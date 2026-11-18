#!/usr/bin/env python3
"""给名卷库的 776 条网盘链接补上「真实文件名 + 文件大小 + 存活状态」。

为什么需要这个：
  原站列表页只给了标题（「人教版2025-2026学年小学六年级数学上册期末测试卷」），
  但那个标题是编辑自己起的，和网盘里真正那个文件不是一回事 ——
  有的标题写「附答案」其实文件里没有，有的没写「.pdf」结果是扫描版。
  打开分享页能读到 `<title>` 里的真实文件名和 `"size"` 字节数，
  先把这两个字段补上，用户点「打开网盘」之前就知道自己要去拿什么、多大。

怎么读（不需要登录，但不能下载）：
  1. GET  /s/<surl>                       → 拿到 uk、shareid，看是「请输入提取码」还是直接进详情
  2. POST /share/verify?surl=..&t=..      → 提取码换 randsk（errno 0 即通过）
  3. GET  /s/<surl>（带 BDCLND cookie）   → 这次 <title> 就是真实文件名，"size" 就是字节数

  下载另说：/share/download 返回 errno 112（未登录），匿名拿不到直链。

用法：
  python3 tools/probe-papers.py            # 断点续跑，已探过的跳过
  python3 tools/probe-papers.py --limit 20 # 只探前 20 条
  python3 tools/probe-papers.py --write    # 探完写回 library.json 与 App assets
"""

import json
import os
import random
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / 'web/data/papers/library.json'
APP = ROOT / 'app/src/main/assets/papers/library.json'
CACHE = ROOT / 'tools/.probe-cache.json'

UA = ('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36')

DEAD_PAT = re.compile(r'分享的文件已经被取消|链接不存在|链接已过期|来晚了|啊哦，你来晚了|已失效')
LOCK_PAT = re.compile(r'请输入提取码|提取码')


def curl(args, jar, timeout=25):
    cmd = ['curl', '-s', '-A', UA, '-b', jar, '-c', jar, '--max-time', str(timeout)] + args
    return subprocess.run(cmd, capture_output=True, text=True).stdout


def title_of(html):
    m = re.search(r'<title>([^<]*)</title>', html)
    return (m.group(1) if m else '').strip()


def probe_one(item, idx):
    """返回 {st, fn, sz, err}；st: ok / dead / locked / bad"""
    url = item.get('u') or ''
    m = re.search(r'pan\.baidu\.com/s/([A-Za-z0-9_\-]+)', url)
    if not m:
        return {'st': 'bad', 'err': '链接格式不认识'}
    surl_full = m.group(1)
    surl = surl_full[1:] if surl_full.startswith('1') else surl_full
    pwd = re.search(r'pwd=([A-Za-z0-9]+)', url)
    share_url = f'https://pan.baidu.com/s/{surl_full}'
    jar = f'/tmp/bdprobe_{idx}.txt'
    if os.path.exists(jar):
        os.remove(jar)

    html = curl(['-L', share_url], jar)
    if not html:
        return {'st': 'bad', 'err': '网络无响应'}
    t = title_of(html)

    if DEAD_PAT.search(html) or DEAD_PAT.search(t):
        return {'st': 'dead', 'err': t[:60]}

    if pwd and LOCK_PAT.search(t):
        ts = str(int(time.time() * 1000))
        r = curl(['-H', f'Referer: {share_url}', '-X', 'POST',
                  f'https://pan.baidu.com/share/verify?surl={surl}&t={ts}'
                  '&channel=chunlei&web=1&app_id=250528&clienttype=0',
                  '--data', f'pwd={pwd.group(1)}&vcode=&vcode_str='], jar)
        if '"errno":0' not in r:
            # errno -9 / 105 是要验证码，说明被打上了风控，隔一会儿再来
            return {'st': 'locked', 'err': r[:80]}
        html = curl(['-L', share_url], jar)
        t = title_of(html)

    fn = re.search(r'([^"\'<>]{4,120}\.(?:docx|doc|pdf|zip|rar|7z))', html)
    size = re.search(r'"size":(\d+)', html)
    if not fn:
        return {'st': 'locked', 'err': t[:60] or '未读到文件名'}
    if DEAD_PAT.search(t):
        return {'st': 'dead', 'err': t[:60]}

    out = {'st': 'ok', 'fn': fn.group(1).strip()}
    if size:
        out['sz'] = int(size.group(1))
    return out


def main():
    limit = None
    write_back = '--write' in sys.argv
    if '--limit' in sys.argv:
        limit = int(sys.argv[sys.argv.index('--limit') + 1])

    items = json.loads(WEB.read_text(encoding='utf-8'))['items']
    cache = json.loads(CACHE.read_text(encoding='utf-8')) if CACHE.exists() else {}

    todo = [(i, it) for i, it in enumerate(items) if it['i'] not in cache]
    if limit:
        todo = todo[:limit]
    print(f'共 {len(items)} 条，已探 {len(cache)}，本轮要探 {len(todo)}', flush=True)

    for n, (i, it) in enumerate(todo, 1):
        r = probe_one(it, i)
        if r.get('st') == 'locked':
            # 风控时这一条先不落盘，下一轮再来
            print(f'[{n}/{len(todo)}] 待重试  {it["n"][:28]}  {r.get("err", "")[:40]}', flush=True)
        else:
            cache[it['i']] = r
            mark = {'ok': '可读', 'dead': '失效', 'bad': '异常'}[r['st']]
            extra = r.get('fn', r.get('err', ''))[:44]
            print(f'[{n}/{len(todo)}] {mark}  {extra}', flush=True)
        if n % 20 == 0:
            CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=0), encoding='utf-8')
        time.sleep(0.45 + random.random() * 0.75)

    CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=0), encoding='utf-8')

    from collections import Counter
    c = Counter(v['st'] for v in cache.values())
    print(f'\n探到 {len(cache)} 条：可读 {c["ok"]} · 失效 {c["dead"]} · 待重试/被拦 {c["locked"]} · 异常 {c["bad"]}')

    if write_back:
        # 只把「确实探成功」的写回，没探到的不动原有字段
        for it in items:
            v = cache.get(it['i'])
            if not v:
                continue
            if v['st'] == 'ok':
                it['f'] = v['fn']
                if v.get('sz'):
                    it['z'] = round(v['sz'] / 1024)
                it['st'] = 'ok'
            else:
                it['st'] = v['st']
        doc = {'note': ('名卷库索引。数据来源 shijuan.on2on.cn 的公开下载目录，'
                        '只存标题与网盘链接，不转载、不打包任何试题文件。'
                        'f/z/st 由 tools/probe-papers.py 实地探测补上（真实文件名 / KB / 存活状态）。'),
               'items': items}
        payload = json.dumps(doc, ensure_ascii=False, separators=(',', ':'))
        WEB.write_text(payload, encoding='utf-8')
        APP.parent.mkdir(parents=True, exist_ok=True)
        APP.write_text(payload, encoding='utf-8')
        print(f'已写回 {WEB.relative_to(ROOT)} 与 {APP.relative_to(ROOT)}（{len(payload) // 1024} KB）')


if __name__ == '__main__':
    main()
