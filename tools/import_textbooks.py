#!/usr/bin/env python3
"""
把 TapXWorld/ChinaTextbook 的教材清单导入本 App 的 assets（**只存链接，不存 PDF**）。

============================================================
为什么只存链接
============================================================
1. 那个仓库 **没有 LICENSE 文件**（GitHub API 返回 license: null）。
   没有许可 = 默认保留全部版权，不能再分发；而这些教材的著作权本来就属于人教社等出版社。
   把 PDF 打包进公开仓库的 APK，等于二次分发，是明确侵权。
2. 体积也不允许：仓库 41.5GB，光人教版小学数学 12 册就 294MB，本 App 的 APK 才 11MB。
3. 用途也不匹配：那是整本扫描件，不是练习纸，App 里「打一本教材」这个需求本身也少见。

所以这里只做一份**目录 + 链接**：用户点一下用浏览器打开原仓库看，
App 不持有、不分发任何一页教材内容。

============================================================
筛选规则
============================================================
- 学段：只收「小学」「初中」（本 App 只做 1–9 年级）；高中 / 大学 / 五•四学制不收。
- 科目：语文 / 数学 / 英语 / 物理 / 化学（与 App 五科对齐）。
- 出版社：只收「人教版」「统编版」。同一册教材各版差异很大，
  全收会有 565 个文件，列表没法用；本 App 的课标题库也是按人教版组织的。
- 分片文件（.pdf.1 / .pdf.2，仓库因 GitHub 单文件 100MB 限制切过）：
  若同名整本存在则用整本，否则合并成一条并标注分片数（用户需自行合并）。

用法：
    python3 tools/import_textbooks.py                 # 现拉 GitHub 文件树
    python3 tools/import_textbooks.py --tree FILE     # 复用已下载的 tree JSON
"""

import json
import os
import re
import sys
import urllib.parse
import urllib.request

REPO = "TapXWorld/ChinaTextbook"
BRANCH = "master"
TREE_API = f"https://api.github.com/repos/{REPO}/git/trees/{BRANCH}?recursive=1"
BLOB = f"https://github.com/{REPO}/blob/{BRANCH}/"

STAGES = {"小学": "小学", "初中": "初中"}
SUBJECTS = ("语文", "数学", "英语", "物理", "化学")
PUBLISHERS = ("人教版", "统编版")

# 文件名里的中文年级 → 数字
GRADE_CN = {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
GRADE_CN.update({"1": 1, "2": 2, "3": 3, "4": 4, "5": 5, "6": 6, "7": 7, "8": 8, "9": 9})

GRADE_RE = re.compile(r"([一二三四五六七八九1-9])年级")
TERM_RE = re.compile(r"(上册|下册|全一册)")
PART_RE = re.compile(r"\.pdf\.(\d+)$")


def fetch_tree():
    print(f"  拉取文件树 {TREE_API}")
    req = urllib.request.Request(TREE_API, headers={"User-Agent": "papermill-import"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def parse_grade_term(name):
    # 注意取**最后**一个「X年级」：英语教材名字里带起点信息，
    # 「英语（一年级起点）三年级上册」真正的年级是三年级，按第一个匹配抓会全变成一年级。
    matches = list(GRADE_RE.finditer(name))
    t = TERM_RE.search(name)
    if not matches or not t:
        return None, None
    grade = GRADE_CN.get(matches[-1].group(1))
    term = {"上册": "上", "下册": "下", "全一册": "全"}[t.group(1)]
    return grade, term


def main():
    tree_file = None
    for i, a in enumerate(sys.argv):
        if a == "--tree" and i + 1 < len(sys.argv):
            tree_file = sys.argv[i + 1]

    if tree_file and os.path.exists(tree_file):
        print(f"复用文件树：{tree_file}")
        tree = json.load(open(tree_file, encoding="utf-8"))
    else:
        tree = fetch_tree()

    blobs = [t for t in tree["tree"] if t["type"] == "blob"]

    # ---- 第一遍：按「去掉分片后缀的路径」聚合，整本优先
    books = {}
    for t in blobs:
        path = t["path"]
        parts = path.split("/")
        if parts[0] not in STAGES or len(parts) < 4:
            continue
        subject = parts[1]
        if subject not in SUBJECTS:
            continue
        if not any(parts[2].startswith(p) for p in PUBLISHERS):
            continue

        name = parts[-1]
        m = PART_RE.search(name)
        if m:
            base_path = PART_RE.sub(".pdf", path)
            part_no = int(m.group(1))
        else:
            base_path = path
            part_no = 0
        if not base_path.lower().endswith(".pdf"):
            continue

        key = base_path
        rec = books.setdefault(key, {"parts": {}, "single": None, "size": 0})
        if part_no:
            rec["parts"][part_no] = t.get("size") or 0
        else:
            rec["single"] = t
            rec["size"] = t.get("size") or 0

    # ---- 第二遍：出条目
    out = []
    skipped = 0
    for path, rec in books.items():
        single = rec["single"]
        part_count = len(rec["parts"])
        if single is not None:
            size = rec["size"]
            total_parts = 1
        elif part_count:
            size = sum(rec["parts"].values())
            total_parts = part_count
        else:
            skipped += 1
            continue

        name = path.split("/")[-1]
        grade, term = parse_grade_term(name)
        if grade is None:
            skipped += 1
            continue

        parts = path.split("/")
        title = re.sub(r"\.pdf$", "", name).replace("义务教育教科书", "").strip(" ·・")
        out.append(
            {
                "s": STAGES[parts[0]],
                "sub": parts[1],
                "p": "人教版" if parts[2].startswith("人教版") else "统编版",
                "g": grade,
                "t": term,
                "n": title,
                "u": BLOB + urllib.parse.quote(path),
                "mb": round(size / 1048576, 1),
                "k": total_parts,
            }
        )

    out.sort(key=lambda b: (0 if b["s"] == "小学" else 1, b["g"], b["sub"], b["t"]))

    # 去重：同一册偶尔既有整本扫码件、又有另一份分片的（名字一样、大小差很多）。
    # 优先整本（用户不用自己去合并），其次页数/体积大的那份。
    dedup = {}
    for b in out:
        key = (b["s"], b["sub"], b["p"], b["g"], b["t"], b["n"])
        cur = dedup.get(key)
        if cur is None or (b["k"], -b["mb"]) < (cur["k"], -cur["mb"]):
            dedup[key] = b
    dropped = len(out) - len(dedup)
    out = sorted(
        dedup.values(), key=lambda b: (0 if b["s"] == "小学" else 1, b["g"], b["sub"], b["t"])
    )

    payload = {
        "schema": 1,
        "source": f"https://github.com/{REPO}",
        "note": "只存链接，不打包 PDF。教材著作权归各出版社，原仓库无 LICENSE，"
                "因此本 App 不分发任何一页教材内容，点击后用浏览器打开原仓库。",
        "books": out,
    }

    out_dir = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app", "src", "main", "assets", "textbook",
    )
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "catalog.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))

    total_mb = sum(b["mb"] for b in out)
    split = [b for b in out if b["k"] > 1]
    print(f"  教材 {len(out)} 册，合计 {total_mb:.0f} MB（仅为链接，不占包体）")
    print(f"  其中分片需合并 {len(split)} 册；跳过 {skipped} 条")
    print(f"  输出 {path} ({os.path.getsize(path)/1024:.0f} KB)")
    for b in out[:4]:
        print(f"    {b['s']}{b['g']}年级{b['t']} {b['sub']} {b['mb']}MB ×{b['k']}")


if __name__ == "__main__":
    main()
