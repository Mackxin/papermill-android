#!/usr/bin/env python3
"""百度网盘扫码授权 —— 拿一个能下载的会话。

为什么要它：776 条名卷库链接都能读文件名，但 /share/download 返回 errno 112（未登录），
匿名拿不到直链。必须有登录态。

为什么用扫码：BDUSS 是 httpOnly，页面 JS 读不到，让用户翻 F12 复制对零基础用户太难。
扫码只需要手机百度 App 扫一下，凭据全在服务端拿，用户什么都不用碰。

流程：
  1. GET  passport.baidu.com/v2/api/getqrcode  → imgurl（二维码）+ sign
  2. 把二维码存成图片给用户扫
  3. 轮询 passport.baidu.com/channel/unicast   → 扫码状态 / 登录凭据
  4. 凭据换 BDUSS 等 cookie，落盘到 tools/.baidu-session.json

用法：
  python3 tools/baidu-login.py            # 出码并等待（默认 180 秒）
  python3 tools/baidu-login.py --check    # 只看当前会话还能不能用
"""

import json
import os
import random
import re
import string
import sys
import time
import urllib.parse
from pathlib import Path

import subprocess

ROOT = Path(__file__).resolve().parent.parent
SESSION = ROOT / 'tools/.baidu-session.json'
QR_PNG = Path('/tmp/baidu-qr.png')

UA = ('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36')


def gid():
    return ''.join(random.choice(string.hexdigits.upper()[:16]) for _ in range(32))


def curl(args, jar=None, timeout=25):
    cmd = ['curl', '-s', '-A', UA, '--max-time', str(timeout)]
    if jar:
        cmd += ['-b', jar, '-c', jar]
    return subprocess.run(cmd + args, capture_output=True, text=True).stdout


def save_cookies(jar, extra=None):
    """从 curl cookie jar 抽出需要的键，存成 {name: value}"""
    out = {}
    for line in Path(jar).read_text(errors='ignore').splitlines():
        if line.startswith('#') or '\t' not in line:
            continue
        parts = line.split('\t')
        if len(parts) >= 7:
            out[parts[5]] = parts[6]
    if extra:
        out.update(extra)
    return out


def main():
    jar = '/tmp/bdlogin.jar'
    if os.path.exists(jar):
        os.remove(jar)

    if '--check' in sys.argv:
        if not SESSION.exists():
            print('还没登录过，先跑 python3 tools/baidu-login.py')
            return
        s = json.loads(SESSION.read_text())
        r = curl(['-H', 'Referer: https://pan.baidu.com/',
                  'https://pan.baidu.com/api/gettemplatevariable?fields=%5B%22username%22%2C%22netdisk_nickname%22%5D'],
                 jar=None)
        print('当前会话：', s.get('user', '?'), '·', r[:200])
        return

    G = gid()
    ts = str(int(time.time() * 1000))
    url = ('https://passport.baidu.com/v2/api/getqrcode?lp=pc&qrloginfrom=pc'
           f'&gid={G}&callback=cb&t={ts}&_={ts}')
    raw = curl(['-c', jar, url])
    m = re.search(r'\{.*\}', raw, re.S)
    if not m:
        print('取二维码失败，返回：', raw[:300])
        return
    data = json.loads(m.group(0))
    imgurl = data.get('imgurl')
    sign = data.get('sign', '')
    if not imgurl:
        print('接口没给 imgurl：', data)
        return

    qs = urllib.parse.parse_qs(urllib.parse.urlparse(imgurl).query)
    channel_id = (qs.get('channel_id') or [''])[0]
    print('二维码地址：', imgurl)
    print('channel_id：', channel_id)

    # 存二维码图片（百度给的是 base64 或者图片流，两种都处理）
    png = curl(['-b', jar, imgurl], timeout=25)
    if png.startswith('data:image') or len(png) < 200:
        print('图片是 base64，改用 python 下载')
    else:
        Path(QR_PNG).write_bytes(png.encode('latin-1', 'ignore'))
        print('二维码已存到', QR_PNG)

    # 轮询
    deadline = time.time() + 180
    print('\n请用手机百度 App 扫码（或百度 App 扫一扫）…', flush=True)
    while time.time() < deadline:
        ts = str(int(time.time() * 1000))
        r = curl(['-b', jar, '-c', jar, '-H', 'Referer: https://pan.baidu.com/',
                  f'https://passport.baidu.com/channel/unicast?channel_id={channel_id}'
                  f'&tpl=netdisk&gid={G}&callback=cb&_={ts}'], timeout=20)
        m = re.search(r'\{.*\}', r, re.S)
        if m:
            try:
                d = json.loads(m.group(0))
            except Exception:
                d = {}
            errno = d.get('errno')
            if errno == 1 and d.get('channel_v'):
                print('已扫码，等待手机确认…', flush=True)
            elif errno == 0 and d.get('channel_v'):
                print('扫码成功，换登录凭据…')
                v = d['channel_v']
                ts2 = str(int(time.time() * 1000))
                q = urllib.parse.urlencode({
                    'channelid': channel_id, 'v': v, 'u': '', 'tpl': 'netdisk',
                    'gid': G, 'callback': 'cb', 't': ts2, '_': ts2,
                })
                r2 = curl(['-b', jar, '-c', jar, '-H', 'Referer: https://pan.baidu.com/',
                           f'https://passport.baidu.com/v3/login/main/qrbdusslogin?{q}'], timeout=25)
                m2 = re.search(r'\{.*\}', r2, re.S)
                if m2:
                    d2 = json.loads(m2.group(0))
                    bduss = (d2.get('data') or {}).get('bduss') or d2.get('bduss')
                    if bduss:
                        cookies = save_cookies(jar, {'BDUSS': bduss})
                        SESSION.write_text(json.dumps({'cookies': cookies, 'ts': int(time.time())},
                                                      ensure_ascii=False, indent=1))
                        print('登录成功。cookie 已存到', SESSION)
                        print('关键值：', 'BDUSS=' + bduss[:20] + '…')
                        return
                    print('登录返回里没有 bduss：', r2[:300])
                else:
                    print('登录返回无法解析：', r2[:300])
                cookies = save_cookies(jar)
                if cookies.get('BDUSS'):
                    SESSION.write_text(json.dumps({'cookies': cookies, 'ts': int(time.time())},
                                                  ensure_ascii=False, indent=1))
                    print('登录成功（cookie jar 里拿到 BDUSS）')
                    return
                print('未能拿到 BDUSS，cookie 键：', list(cookies.keys()))
                return
            elif errno == 2:
                pass  # 还没扫
        time.sleep(1.5)
    print('\n超时了，重新跑一次会出新的二维码')


if __name__ == '__main__':
    main()
