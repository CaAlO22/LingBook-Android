#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成灵记的历代更新日志数据（app/src/main/res/raw/changelog.json）。

数据来源（按优先级）：

1. `docs/changelog/current.md` —— **当前未发布版本**的日志，发版前由 agent / 人手动重写。
   标题 `# 2.0.0c` 就是版本号，下面的 `## 新功能 / ## 优化 / ## 修复` 是分组。
2. GitHub Releases 的发布说明 —— 已发布版本的历史日志，自动抓取，不用手工维护。
3. 兜底：抓不到网络时沿用现有 json 里的历史条目，不会把历史日志抹掉。

用法（在仓库根目录）：

    python tools/gen_changelog.py

发版流程里应当在「改完版本号、构建 APK 之前」执行，把生成的 json 一起提交，
这样安装包里的「历代更新日志」就包含本版内容。
"""

import json
import os
import re
import subprocess
import sys
import urllib.request
from datetime import date as _date

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE_FILE = os.path.join(REPO_ROOT, "app", "build.gradle.kts")
CURRENT_MD = os.path.join(REPO_ROOT, "docs", "changelog", "current.md")
OUTPUT = os.path.join(REPO_ROOT, "app", "src", "main", "res", "raw", "changelog.json")
API = "https://api.github.com/repos/CaAlO22/LingBook-Android/releases?per_page=100"

# 纯安装说明类句子，对更新日志没有价值
NOISE_PATTERNS = ("apk", "本次 Release 包含")


def app_version() -> str:
    with open(GRADLE_FILE, encoding="utf-8") as f:
        m = re.search(r'versionName\s*=\s*"([^"]+)"', f.read())
    return m.group(1) if m else ""


def clean(text: str) -> str:
    text = text.replace("\ufeff", "").replace("**", "").replace("`", "")
    text = re.sub(r"^\s*[-*]\s*", "", text)
    text = re.sub(r"^\s*\d+\.\s*", "", text)
    return re.sub(r"[ \t]+", " ", text).strip()


def is_noise(text: str) -> bool:
    low = text.lower()
    if any(p.lower() in low for p in NOISE_PATTERNS):
        return True
    # 「主要更新：」这类引导句没有内容
    return text.endswith("：") or text.endswith(":")


def parse_sections(markdown: str, skip_quote: bool = False):
    """把 markdown 拆成 [{title, items}]；一级标题当作版本号跳过，其余标题都是分组。"""
    sections = []
    current = None
    for raw in (markdown or "").replace("\ufeff", "").splitlines():
        line = raw.strip()
        if not line:
            continue
        # current.md 顶部的说明性引用块不算内容
        if skip_quote and line.startswith(">"):
            continue
        if line.startswith("#"):
            if line.startswith("# "):
                continue
            current = {"title": clean(line.lstrip("#").strip()), "items": []}
            sections.append(current)
            continue
        text = clean(line)
        if not text or is_noise(text):
            continue
        if current is None:
            current = {"title": "", "items": []}
            sections.append(current)
        current["items"].append(text)
    return [s for s in sections if s["items"]]


def load_current_md(version_fallback: str, today: str):
    """读取 docs/changelog/current.md（当前未发布版本的日志）。"""
    if not os.path.exists(CURRENT_MD):
        return None
    with open(CURRENT_MD, encoding="utf-8") as f:
        markdown = f.read()
    m = re.search(r"^#\s+(.+)$", markdown, re.MULTILINE)
    # 版本号不能走 clean()：clean 会吃掉「2.0.0c」里的「2.」，当成序号列表处理
    version = (m.group(1).strip() if m else version_fallback).lstrip("v")
    date = today
    d = re.search(r"日期\s*[：:]\s*(\d{4}-\d{2}-\d{2})", markdown)
    if d:
        date = d.group(1)
    sections = parse_sections(markdown, skip_quote=True)
    if not sections:
        return None
    return {"version": version, "date": date, "sections": sections}


def today_string() -> str:
    """当前未发布版本的日期：默认取今天（发版日），current.md 里可用「日期：」覆盖。"""
    return _date.today().isoformat()


def fetch_releases():
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    req = urllib.request.Request(
        API,
        headers={"User-Agent": "lingji-changelog", "Accept": "application/vnd.github+json"},
    )
    with opener.open(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def releases_to_entries(releases) -> list:
    entries = []
    for release in releases:
        sections = parse_sections(release.get("body") or "")
        if not sections:
            continue
        entries.append(
            {
                "version": release["tag_name"].lstrip("v"),
                "date": (release.get("published_at") or release.get("created_at") or "")[:10],
                "sections": sections,
            }
        )
    return entries


def git(*args) -> str:
    return subprocess.run(
        ["git"] + list(args), cwd=REPO_ROOT, capture_output=True, text=True, check=True
    ).stdout.strip()


def unreleased_fallback(version: str, latest_tag: str, today: str):
    """没有 current.md 时的兜底：用最新 tag 之后的提交标题拼一条。"""
    if not latest_tag:
        return None
    log = git("log", "%s..HEAD" % latest_tag, "--pretty=%s")
    sections = {"新功能": [], "修复": [], "优化": []}
    for subject in [s for s in log.splitlines() if s.strip()]:
        m = re.match(r"^(\w+)(\([^)]*\))?!?:\s*(.+)$", subject)
        body = m.group(3) if m else subject
        if m and m.group(1) == "feat":
            bucket = "新功能"
        elif m and m.group(1) in ("fix", "perf", "refactor"):
            bucket = "修复"
        else:
            bucket = "优化"
        for part in re.split(r"[；;]", body):
            part = clean(part)
            if part:
                sections[bucket].append(part)
    result = [{"title": k, "items": v} for k, v in sections.items() if v]
    if not result:
        return None
    return {"version": version, "date": today, "sections": result}


def load_existing() -> list:
    if not os.path.exists(OUTPUT):
        return []
    try:
        with open(OUTPUT, encoding="utf-8") as f:
            return json.load(f)
    except (ValueError, OSError):
        return []


def main():
    version = app_version()
    today = today_string()

    try:
        entries = releases_to_entries(fetch_releases())
        released = {e["version"] for e in entries}
        print("从 GitHub Release 取到 %d 个历史版本" % len(entries))
    except Exception as exc:  # 网络不通时不要抹掉历史日志
        entries = load_existing()
        # 断网时无从判断本版是否已发布，沿用现有 json 里的标记
        released = None
        print("抓取 Release 失败（%s），沿用现有 json 的 %d 个版本" % (exc.__class__.__name__, len(entries)))

    current = load_current_md(version, today)
    if current:
        if version and current["version"] != version:
            print(
                "！docs/changelog/current.md 的版本（%s）与 build.gradle.kts（%s）不一致，请先对齐"
                % (current["version"], version)
            )
        previous_flag = next(
            (e.get("unreleased") for e in entries if e["version"] == current["version"]), None
        )
        current["unreleased"] = (
            current["version"] not in released if released is not None
            else bool(previous_flag) if previous_flag is not None
            else True
        )
        entries = [e for e in entries if e["version"] != current["version"]]
        entries.insert(0, current)
        print(
            "使用 docs/changelog/current.md 的 v%s（%d 条%s）"
            % (
                current["version"],
                sum(len(s["items"]) for s in current["sections"]),
                "，未发布" if current["unreleased"] else "",
            )
        )
    elif version and released is not None and version not in released:
        fallback = unreleased_fallback(
            version, "v" + entries[0]["version"] if entries else "", today
        )
        if fallback:
            fallback["unreleased"] = True
            entries.insert(0, fallback)
            print("！未找到 docs/changelog/current.md，暂用提交标题兜底 v%s，建议补写正式日志" % version)

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("写入 %s：%d 个版本，%d 字节" % (OUTPUT, len(entries), os.path.getsize(OUTPUT)))


if __name__ == "__main__":
    sys.exit(main())
