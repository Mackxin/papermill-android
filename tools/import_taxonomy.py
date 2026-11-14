#!/usr/bin/env python3
"""
把 xiaoyang111222/cn-math-taxonomy 的知识点与题库导入本 App 的 assets。

============================================================
为什么要有这个脚本
============================================================
那个仓库每周都在长（一轮扩容就多出几千道题）。手工拷 JSON 既容易漏，
也没法控制体积 —— 原始三个文件加起来 14MB，全塞进 APK 不合适。
这个脚本做三件事：
  1. 只保留 **1–9 年级、五科**（数学 / 语文 / 英语 / 物理 / 化学）以内的内容；
  2. 把字段名压成短键、去掉缩进，体积压到原来的 ~12%；
  3. 按科目拆成五个文件，App 端只加载当前科目那一个。

============================================================
许可与署名（重要）
============================================================
数据来源：xiaoyang111222/cn-math-taxonomy
许可：CC BY-NC 4.0（署名 — 非商业性使用）
  - 本文档与 App 内「关于」页均已署名，并注明「已裁剪为 1–9 年级五科」；
  - **本 App 一旦商业化（付费、广告、随硬件销售等），必须删除 assets/taxonomy 并
    移除对应题型**，否则违反 NC 条款。
  - CC BY-NC 4.0 完整文本：https://creativecommons.org/licenses/by-nc/4.0/

用法：
    python3 tools/import_taxonomy.py            # 用内置默认路径（已下载到 /tmp/tax）
    python3 tools/import_taxonomy.py --src DIR  # 指定 topics.json / questions.json 所在目录
"""

import json
import os
import re
import sys
import urllib.request

RAW = "https://raw.githubusercontent.com/xiaoyang111222/cn-math-taxonomy/main/data"

# domain（学科知识点分类）→ 本 App 的科目代码
DOMAIN_TO_SUBJECT = {
    # 数学
    "数与代数": "math", "图形与几何": "math", "统计与概率": "math", "综合与实践": "math",
    # 英语
    "语音": "english", "词汇": "english", "语法": "english",
    "日常表达": "english", "读写": "english",
    # 语文
    "识字写字": "chinese", "习作基础": "chinese", "阅读理解": "chinese", "拼音": "chinese",
    "古诗文积累": "chinese", "句子": "chinese", "词语": "chinese",
    "文言文阅读": "chinese", "名著阅读": "chinese",
    # 物理
    "运动和相互作用": "physics", "物质": "physics", "能量": "physics",
    # 化学
    "科学探究与化学实验": "chemistry", "物质的化学变化": "chemistry",
    "物质的性质与应用": "chemistry", "物质的组成与结构": "chemistry",
    "化学与社会·跨学科实践": "chemistry",
}

DIFF = {"basic": 1, "standard": 2, "challenge": 3, "advanced": 3}

SUBJECT_LABEL = {
    "math": "数学", "chinese": "语文", "english": "英语",
    "physics": "物理", "chemistry": "化学",
}


def load(src_dir):
    def read(name):
        local = os.path.join(src_dir, name)
        if os.path.exists(local):
            with open(local, encoding="utf-8") as f:
                return json.load(f)
        print(f"  下载 {name} …")
        with urllib.request.urlopen(f"{RAW}/{name}", timeout=120) as r:
            return json.load(r)

    return read("topics.json"), read("questions.json")


def short_topic(t):
    out = {
        "i": t["id"],
        "n": t["name"],
        "g": t["grade"],
        "s": t.get("semester", ""),
        "c": t.get("chapter", ""),
        "d": t.get("description", ""),
    }
    crit = t.get("mastery_criteria") or []
    if crit:
        out["cr"] = crit
    dq = t.get("diagnostic_question") or {}
    if dq.get("question"):
        out["dq"] = dq["question"]
        out["da"] = dq.get("answer", "")
    return out


def short_question(q):
    # 题型映射：1 = 填空/简答（答案是一段文字），2 = 选择（答案是一个字母）
    if q["type"] == "choice":
        out = {
            "i": q["id"], "ti": q["topicId"], "t": 2, "p": q["prompt"].strip(),
            "o": [[c["key"], c["text"]] for c in q.get("choices", [])],
            "a": q["answer"], "f": DIFF.get(q.get("difficulty"), 2),
        }
    else:
        out = {
            "i": q["id"], "ti": q["topicId"], "t": 1, "p": q["prompt"].strip(),
            "a": q["answer"].strip(), "f": DIFF.get(q.get("difficulty"), 2),
        }
    if q.get("explanation"):
        out["e"] = q["explanation"].strip()
    return out


def main():
    src = "/tmp/tax"
    for i, a in enumerate(sys.argv):
        if a == "--src" and i + 1 < len(sys.argv):
            src = sys.argv[i + 1]

    print(f"读取数据：{src}")
    topics, questions = load(src)
    by_id = {t["id"]: t for t in topics}

    buckets = {k: {"topics": [], "questions": []} for k in SUBJECT_LABEL}

    for t in topics:
        subject = DOMAIN_TO_SUBJECT.get(t["domain"])
        if not subject or t["grade"] > 9:
            continue
        buckets[subject]["topics"].append(short_topic(t))

    dropped = 0
    for q in questions:
        t = by_id.get(q["topicId"])
        if not t:
            dropped += 1
            continue
        subject = DOMAIN_TO_SUBJECT.get(t["domain"])
        if not subject or t["grade"] > 9:
            continue
        # 选项少于 2 个的选择题没法当选择题用，转成填空（答案是字母会歧义，直接丢）
        if q["type"] == "choice" and len(q.get("choices") or []) < 2:
            dropped += 1
            continue
        # 答案或题干为空的题一律不要
        if not q.get("prompt", "").strip() or not str(q.get("answer", "")).strip():
            dropped += 1
            continue
        sq = short_question(q)
        sq["g"] = t["grade"]
        buckets[subject]["questions"].append(sq)

    out_dir = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app", "src", "main", "assets", "taxonomy",
    )
    os.makedirs(out_dir, exist_ok=True)

    total_t = total_q = 0
    for subject, data in buckets.items():
        payload = {
            "schema": 1,
            "source": "https://github.com/xiaoyang111222/cn-math-taxonomy",
            "license": "CC BY-NC 4.0",
            "note": "已裁剪为 1–9 年级、数学/语文/英语/物理/化学五科；字段名做了压缩",
            "topics": data["topics"],
            "questions": data["questions"],
        }
        path = os.path.join(out_dir, f"{subject}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
        size = os.path.getsize(path) / 1024
        total_t += len(data["topics"])
        total_q += len(data["questions"])
        print(f"  {SUBJECT_LABEL[subject]:<4} 知识点 {len(data['topics']):>5}  题 {len(data['questions']):>5}  {size:>7.1f} KB")

    # 索引文件：只放数量统计，让 App 的「内置内容」页能在不解析 2.9MB 数据的前提下显示真实数字
    index = {
        "schema": 1,
        "source": "https://github.com/xiaoyang111222/cn-math-taxonomy",
        "license": "CC BY-NC 4.0",
        "subjects": {
            k: {
                "topics": len(v["topics"]),
                "questions": len(v["questions"]),
                "blank": sum(1 for q in v["questions"] if q["t"] == 1),
                "choice": sum(1 for q in v["questions"] if q["t"] == 2),
            }
            for k, v in buckets.items()
        },
    }
    with open(os.path.join(out_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, separators=(",", ":"))

    print(f"合计：知识点 {total_t}，题 {total_q}，丢弃 {dropped} 条")
    print(f"输出目录：{out_dir}")


if __name__ == "__main__":
    main()
