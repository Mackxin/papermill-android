#!/usr/bin/env python3
"""把 web/ 打包成一个自包含的「试卷工坊.html」——双击就能用，不用起本地服务。

为什么需要它：
  web/index.html 是开发版，24 个 ES 模块 + 10 个 JSON 数据文件靠 http 取。
  浏览器在 file:// 下会拦下模块加载（CORS）和 fetch（读不到本地 JSON），
  双击 index.html 只能看到「数据没加载上」。

怎么打：
  1. JS   —— esbuild 把 js/app.js 及其 23 个依赖合成一个 IIFE，从 module 变成普通脚本，
             这样 file:// 下没有模块解析这回事，也顺带做了压缩。
  2. CSS  —— 两份样式表内联成 <style>。
  3. 数据 —— data/**.json 每个文件塞一个
                 <script type="application/json" data-pm="data/xxx.json">{…}</script>
             不写成 JS 对象字面量：3MB 的对象字面量启动时就要解析，而 JSON 文本
             浏览器只当文本放着，等 store.js 真用到那一科才 JSON.parse。
  4. 骨架 —— 直接读 web/index.html 做替换，所以 index.html 改了重建即可，两边不会漂。

用法：  python3 tools/build-single.py
产物：  项目根目录 / 试卷工坊.html（约 3.6MB）
"""

import base64
import gzip
import json
import pathlib
import re
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
WEB = ROOT / "web"
OUT = ROOT / "试卷工坊.html"
ESBUILD = "esbuild@0.24.2"


def kb(n: int) -> str:
    return f"{n / 1024:.0f} KB" if n < 1024 * 1024 else f"{n / 1024 / 1024:.2f} MB"


def bundle_js() -> str:
    """用 esbuild 把 ES module 图合成一个 IIFE，返回压缩后的源码。"""
    with tempfile.TemporaryDirectory() as td:
        target = pathlib.Path(td) / "app.bundle.js"
        cmd = [
            "npx", "--yes", ESBUILD,
            str(WEB / "js" / "app.js"),
            "--bundle",
            "--format=iife",
            "--target=es2020",
            "--minify",
            "--legal-comments=none",
            "--charset=utf8",
            f"--outfile={target}",
        ]
        proc = subprocess.run(cmd, cwd=str(WEB), capture_output=True, text=True)
        if proc.returncode != 0:
            sys.stderr.write(proc.stdout + proc.stderr)
            raise SystemExit("esbuild 打包失败")
        return target.read_text(encoding="utf-8")


def inline_css(html: str) -> tuple[str, int]:
    total = 0

    def repl(m: re.Match) -> str:
        nonlocal total
        href = m.group(1)
        css = (WEB / href).read_text(encoding="utf-8")
        total += len(css.encode())
        return f"<style>\n/* {href}（构建时内联） */\n{css}\n</style>"

    return re.sub(r'<link rel="stylesheet" href="([^"]+)">', repl, html), total


def inline_data() -> tuple[str, int]:
    """data/ 下每个 JSON 一条 <script>，路径即 store.js 用的 key。

    v0.29.0 起大的走 gzip：真题库 11.1 MB 明文 gzip 后只有 1.88 MB，
    base64 再涨三分之一也就 2.4 MB —— 单文件从 14.8 MB 掉到 6 MB 出头。
    小的（几 KB）继续明文体，省掉一次解压，也方便出问题时直接翻源码。

    压缩版标签写成 type="application/json-gz"，store.js 按这个类型认，
    用 DecompressionStream 现解。明文版仍要把 '<' 转成 \\u003c，
    否则题库里的「<」会提前闭合 </script>；base64 里没有这种字符，不用管。
    """
    blocks = []
    total = 0
    for f in sorted((WEB / "data").rglob("*.json")):
        rel = f.relative_to(WEB).as_posix()
        raw = f.read_bytes()
        # 构建期先把这份 JSON 校验一遍，坏数据宁可在打包时炸
        json.loads(raw.decode("utf-8"))

        if len(raw) > 64 * 1024:
            # mtime=0：不把构建时刻写进 gzip 头，否则同样源码每次构建出的
            # 字节都不同，git 里每次都多出一个 4 MB 的假 diff。
            gz = gzip.compress(raw, 9, mtime=0)
            packed = base64.b64encode(gz).decode("ascii")
            # base64 会比 gzip 大 1/3；压完还不如原文就老实写明文体
            if len(packed) < len(raw):
                blocks.append(
                    f'<script type="application/json-gz" data-pm="{rel}">{packed}</script>'
                )
                total += len(packed.encode())
                continue

        text = raw.decode("utf-8").strip().replace("<", "\\u003c")
        blocks.append(
            f'<script type="application/json" data-pm="{rel}">{text}</script>'
        )
        total += len(text.encode())
    return "\n".join(blocks), total


def main() -> None:
    html = (WEB / "index.html").read_text(encoding="utf-8")

    js = bundle_js()
    css_bytes = 0
    html, css_bytes = inline_css(html)

    data_html, data_bytes = inline_data()
    # '</script' 出现在被压缩的 JS 字符串里会截断脚本，esbuild 一般已处理，这里兜一层
    js_safe = js.replace("</script", "<\\/script")
    app_tag = f"<script>\n{js_safe}\n</script>"

    needle = '<script type="module" src="js/app.js"></script>'
    if needle not in html:
        raise SystemExit(f"index.html 里找不到入口标签：{needle}")
    html = html.replace(
        needle,
        "<!-- 内置数据：每个文件一条，store.js 按 data-pm 路径取 -->\n"
        + data_html
        + "\n\n<!-- 应用本体（esbuild 打的 IIFE，等价于原来 24 个 ES 模块） -->\n"
        + app_tag,
    )

    html = html.replace(
        "<title>试卷工坊 · 网页版</title>",
        "<title>试卷工坊</title>\n<!-- 本文件由 tools/build-single.py 生成，请勿手改；改源码后重跑构建 -->",
    )

    OUT.write_text(html, encoding="utf-8")
    size = OUT.stat().st_size
    print(f"产物：{OUT}")
    print(f"  合计   {kb(size)}")
    print(f"  ├ CSS  {kb(css_bytes)}")
    print(f"  ├ 数据 {kb(data_bytes)}（{len(list((WEB / 'data').rglob('*.json')))} 个 JSON）")
    print(f"  └ JS   {kb(len(js_safe.encode()))}（压缩后）")


if __name__ == "__main__":
    main()
