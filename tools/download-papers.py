#!/usr/bin/env python3
"""把名卷库里的网盘文件下载到本地。

前置：tools/.baidu-session.json（由 tools/baidu-login.py 扫码产生）。

为什么要「转存」而不是直接下分享文件：
  分享文件的直链要走 /api/sharedownload，而百度对那个接口的 sign 校验是按页面 JS
  动态算的，空 sign 一律 errno 9019。改成先转存到自己网盘、再从自己网盘取 dlink，
  走的是普通网盘下载通道，稳定，也不用逆 JS。
  转存完立刻从网盘删掉，不留垃圾（--keep 可以保留）。

单条流程：
  1. GET  /s/<surl>                                    → uk / shareid / fs_id / 真实文件名
     需要提取码的走 POST /share/verify 换 randsk，并把它写成 BDCLND cookie 带回去
     （不写这一步，页面永远停在「请输入提取码」，读不到上面三个值）
  2. POST /share/transfer                              → 存到 /试卷工坊
  3. GET  /api/filemetas?dlink=1                       → 拿到 dlink
  4. GET  dlink                                        → 存本地
  5. POST /api/filemanager?opera=delete                → 删掉网盘里那份

用法：
  python3 tools/download-papers.py --limit 3      # 试 3 条
  python3 tools/download-papers.py                # 全量，断点续跑
  python3 tools/download-papers.py --grade 6      # 只下六年级
  python3 tools/download-papers.py --keep         # 网盘里那份不删
"""

import json
import random
import re
import subprocess
import sys
import time
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIB = ROOT / 'web/data/papers/library.json'
OUT = ROOT / 'downloads/papers'
STATE = ROOT / 'downloads/.state.json'
PAN_DIR = '/试卷工坊'

UA = ('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36')


class Baidu:
    def __init__(self):
        sess = json.loads((ROOT / 'tools/.baidu-session.json').read_text())
        self.cookie = '; '.join(
            [f'{k}={sess[k]}' for k in ('BDUSS', 'STOKEN', 'PTOKEN') if sess.get(k)]
            + ([sess['pan']] if sess.get('pan') else []))
        self.bdstoken = ''

    def get(self, url, cookie=None, timeout=40, out=None):
        # 必须带 -L：分享链接 /s/<surl> 会先 302 到 /share/init?surl=...，
        # 不跟随就只拿到一句「Redirecting to …」（109 字节，没有 uk/shareid/fs_id）
        cmd = ['curl', '-s', '-L', '--compressed', '-A', UA, '--max-time', str(timeout),
               '-H', f'Cookie: {cookie or self.cookie}', '-H', 'Referer: https://pan.baidu.com/']
        if out:
            cmd += ['-o', out, '-w', '%{http_code} %{size_download}']
        return subprocess.run(cmd + [url], capture_output=True, text=True).stdout

    def post(self, url, data, cookie=None, timeout=40):
        cmd = ['curl', '-s', '--compressed', '-A', UA, '--max-time', str(timeout),
               '-H', f'Cookie: {cookie or self.cookie}', '-H', 'Referer: https://pan.baidu.com/',
               '-X', 'POST', '--data', urllib.parse.urlencode(data)]
        return subprocess.run(cmd + [url], capture_output=True, text=True).stdout

    def boot(self):
        """拿 bdstoken 并确保转存目录存在。bdstoken 是转存/删除接口的必填项。"""
        html = self.get('https://pan.baidu.com/disk/main')
        m = re.search(r'"bdstoken":"([0-9a-f]{16,})"', html) or re.search(r'bdstoken=([0-9a-f]{16,})', html)
        if not m:
            raise SystemExit('拿不到 bdstoken，登录态可能过期了，重跑 tools/baidu-login.py')
        self.bdstoken = m.group(1)
        self.post(f'https://pan.baidu.com/api/create?a=commit&bdstoken={self.bdstoken}'
                  '&channel=chunlei&web=1&app_id=250528&clienttype=0',
                  {'path': PAN_DIR, 'isdir': '1', 'block_list': '[]'})

    def delete(self, path):
        self.post(f'https://pan.baidu.com/api/filemanager?opera=delete&bdstoken={self.bdstoken}'
                  '&channel=chunlei&web=1&app_id=250528&clienttype=0&async=0',
                  {'filelist': json.dumps([path], ensure_ascii=False)})

    def metas(self, path):
        """取单个路径的元信息（含 dlink）。目录不会有 dlink。"""
        tgt = urllib.parse.quote(json.dumps([path], ensure_ascii=False))
        fr = self.get(f'https://pan.baidu.com/api/filemetas?target={tgt}&dlink=1'
                      f'&bdstoken={self.bdstoken}&channel=chunlei&web=1&app_id=250528&clienttype=0')
        try:
            info = json.loads(fr)['info']
            return info[0] if info else {}
        except Exception:
            return {}

    def ls(self, path):
        fr = self.get(f'https://pan.baidu.com/api/list?dir={urllib.parse.quote(path)}&num=1000'
                      f'&order=name&bdstoken={self.bdstoken}&channel=chunlei&web=1&app_id=250528'
                      '&clienttype=0')
        try:
            return json.loads(fr).get('list') or []
        except Exception:
            return []

    def fetch(self, dlink, target, ck=None):
        """按直链下载到 target，返回实际字节数；失败返回 None。"""
        OUT.mkdir(parents=True, exist_ok=True)
        code = subprocess.run(
            ['curl', '-s', '-L', '-A', UA, '--max-time', '180',
             '-H', f'Cookie: {ck or self.cookie}', '-H', 'Referer: https://pan.baidu.com/',
             '-o', str(target), '-w', '%{http_code} %{size_download}', dlink],
            capture_output=True, text=True).stdout
        parts = (code.split() + ['0', '0'])[:2]
        http, sz = parts[0], int(parts[1])
        if http != '200' or sz < 1024:
            if target.exists():
                target.unlink()
            return None
        # 校验真的是 docx/pdf/压缩包，而不是百度返回的错误页
        head = target.open('rb').read(4)
        if not (head.startswith(b'PK') or head.startswith(b'%PDF') or head.startswith(b'Rar!')
                or head.startswith(b'7z\xbc\xaf')):
            target.unlink()
            return None
        return sz

    def grab_dir(self, dirpath, prefix, keep=False, depth=0):
        """把一个网盘目录里的试卷文件全下下来。返回 (成功数, 失败数)。"""
        got = bad = 0
        for f in self.ls(dirpath):
            name = f.get('server_filename') or ''
            if f.get('isdir'):
                if depth < 2:
                    g, b_ = self.grab_dir(f['path'], prefix, keep=keep, depth=depth + 1)
                    got += g
                    bad += b_
                continue
            if not re.search(r'\.(docx|doc|pdf)$', name, re.I):
                continue          # 听力 MP3、图片之类不要
            target = OUT / f'{prefix}_{safe(name)}'
            if target.exists() and target.stat().st_size > 1024:
                got += 1
                continue
            meta = self.metas(f['path'])
            sz = self.fetch(meta['dlink'], target) if meta.get('dlink') else None
            if sz:
                got += 1
                print(f'      └ {target.name}  {sz // 1024}KB', flush=True)
            else:
                bad += 1
        return got, bad


def safe(name, maxlen=70):
    name = re.sub(r'[\\/:*?"<>|\s]+', '_', name).strip('_')
    stem, dot, ext = name.rpartition('.')
    if dot and len(stem) > maxlen:
        return stem[:maxlen] + '.' + ext
    return name[:maxlen]


def download_one(b, item, keep=False):
    url = item.get('u') or ''
    m = re.search(r'pan\.baidu\.com/s/([A-Za-z0-9_\-]+)', url)
    if not m:
        return 'bad', '链接格式不认识'
    surl_full = m.group(1)
    surl = surl_full[1:] if surl_full.startswith('1') else surl_full
    pwd = re.search(r'pwd=([A-Za-z0-9]+)', url)
    share = f'https://pan.baidu.com/s/{surl_full}'

    ck = b.cookie
    html = ''

    # 绝大多数链接都带 pwd，直接 verify 拿 randsk，省掉一次 300KB 的分享页请求。
    # verify 失败时再拉页面，区分「分享失效」和「被风控」。
    if pwd:
        ts = str(int(time.time() * 1000))
        r = b.post(f'https://pan.baidu.com/share/verify?surl={surl}&t={ts}'
                   '&channel=chunlei&web=1&app_id=250528&clienttype=0',
                   {'pwd': pwd.group(1), 'vcode': '', 'vcode_str': ''}, cookie=ck)
        m_rand = re.search(r'"randsk":"([^"]+)"', r)
        if '"errno":0' in r and m_rand:
            ck = ck + '; BDCLND=' + m_rand.group(1)
            html = b.get(share, cookie=ck, timeout=40)
        else:
            probe = b.get(share, cookie=ck, timeout=40)
            if re.search(r'已经被取消|不存在|已过期|你来晚了', probe):
                return 'dead', '分享已失效'
            return 'locked', ('提取码被拦 ' + r[:60])
    else:
        html = b.get(share, cookie=ck, timeout=40)

    if re.search(r'已经被取消|不存在|已过期|你来晚了', html):
        return 'dead', '分享已失效'

    uk = re.search(r'uk:"?(\d+)"?', html)
    shareid = re.search(r'shareid:"?(\d+)"?', html)
    fsid = re.search(r'"fs_id":(\d{10,})', html)
    fn = re.search(r'([^"\'<>]{4,120}\.(?:docx|doc|pdf|zip|rar|7z))', html)
    if not (uk and shareid and fsid and fn):
        t = re.search(r'<title>([^<]*)</title>', html)
        diag = f'len={len(html)} title={(t.group(1)[:30] if t else "")}'
        return 'locked', f'读不到 uk/shareid/fs_id/文件名（{diag}）'

    name = fn.group(1).strip()
    target = OUT / f'{item["i"]}_{safe(name)}'
    if target.exists() and target.stat().st_size > 1024:
        return 'skip', f'已存在 {target.name}'

    # --- 转存
    tr = b.post(f'https://pan.baidu.com/share/transfer?shareid={shareid.group(1)}'
                f'&from={uk.group(1)}&bdstoken={b.bdstoken}'
                '&channel=chunlei&web=1&app_id=250528&clienttype=0',
                {'fsidlist': f'[{fsid.group(1)}]', 'path': PAN_DIR}, cookie=ck)
    try:
        td = json.loads(tr)
    except Exception:
        return 'fail', '转存返回非 JSON：' + tr[:80]
    if td.get('errno') == 4:
        # errno 4 = 网盘里已经有同名文件（上一次删失败，或并发时另一路刚转存同一个）。
        # 不用再转存一次，直接按目标路径去取直链就行。
        to_path = f'{PAN_DIR}/{name}'
    elif td.get('errno') != 0:
        return 'fail', f'转存 errno={td.get("errno")}'
    else:
        to_path = td['extra']['list'][0]['to']

    # --- 取直链
    meta = b.metas(to_path)
    dlink = meta.get('dlink')

    if not dlink:
        # 分享的是一整个文件夹（英语卷常是这样：卷子 docx + 听力 MP3 放一个目录）。
        # filemetas 对目录不返回 dlink，得把里面的文件一个个列出来下。
        if not meta.get('isdir'):
            if not keep:
                b.delete(to_path)
            return 'fail', '取直链失败：' + json.dumps(meta, ensure_ascii=False)[:80]
        got, bad = b.grab_dir(to_path, str(item['i']), keep=keep)
        if not keep:
            b.delete(to_path)
        if got:
            return 'ok', f'{to_path.split("/")[-1]}/ 内 {got} 个文件' + (f'（{bad} 个失败）' if bad else '')
        return 'fail', f'文件夹里没下到文件（{bad} 个失败）'

    sz = b.fetch(dlink, target, ck)
    if not keep:
        b.delete(to_path)
    if sz is None:
        return 'fail', '下载失败（http 非 200 或体积过小）'
    return 'ok', f'{target.name}  {sz // 1024}KB'


def main():
    b = Baidu()
    b.boot()

    items = json.loads(LIB.read_text(encoding='utf-8'))['items']
    if '--grade' in sys.argv:
        g = int(sys.argv[sys.argv.index('--grade') + 1])
        items = [x for x in items if x.get('g') == g]
    limit = int(sys.argv[sys.argv.index('--limit') + 1]) if '--limit' in sys.argv else None
    keep = '--keep' in sys.argv

    OUT.mkdir(parents=True, exist_ok=True)
    state = json.loads(STATE.read_text()) if STATE.exists() else {}
    todo = [x for x in items if state.get(x['i'], {}).get('s') not in ('ok', 'dead', 'bad')]
    if limit:
        todo = todo[:limit]
    print(f'待下 {len(todo)} 条（总 {len(items)}）', flush=True)

    from collections import Counter
    from concurrent.futures import ThreadPoolExecutor
    import threading

    tally = Counter()
    lock = threading.Lock()
    t0 = time.time()
    done = [0]

    def work(it):
        try:
            st, msg = download_one(b, it, keep=keep)
        except Exception as e:
            st, msg = 'fail', f'异常 {type(e).__name__}: {e}'
        with lock:
            done[0] += 1
            tally[st] += 1
            state[it['i']] = {'s': st, 'm': msg}
            ok_n = tally['ok'] + tally['skip']
            print(f"[{done[0]}/{len(todo)}] {st:6s} {msg[:58]}  "
                  f"(成功 {ok_n}, {int(time.time() - t0)}s)", flush=True)
            if done[0] % 10 == 0:
                STATE.write_text(json.dumps(state, ensure_ascii=False, indent=0), encoding='utf-8')
        # 出错就慢一点，别招风控
        time.sleep((2.0 + random.random() * 2) if st in ('fail', 'locked')
                   else (0.4 + random.random() * 0.5))

    # 三路并发：单条本身有四五个串行请求，单线程跑 776 条要六个多小时
    with ThreadPoolExecutor(max_workers=3) as ex:
        list(ex.map(work, todo))

    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=0), encoding='utf-8')
    print('\n汇总：', dict(tally))


if __name__ == '__main__':
    main()
