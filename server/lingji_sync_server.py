#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
灵记 LingJi 云同步服务器（零依赖单文件版）

仅使用 Python 标准库，Python 3.8+ 即可运行：

    python lingji_sync_server.py --host 0.0.0.0 --port 8765
    python lingji_sync_server.py --port 8765 --token your-secret   # 启用访问令牌

数据存储在同目录下的 SQLite 文件中（默认 lingji_sync.db），无需任何外部服务。

协议（全部为 JSON）：
    GET  /api/ping                -> {"ok": true, "serverTime": <ms>, "schemaVersion": <int>}
    GET  /api/model_catalog       -> 模型目录（见下方「模型目录」）
                                     支持 ?version=<客户端已有版本>，相同则只回 {"unchanged": true}
    POST /api/sync
        请求:
        {
          "deviceId": "LJ-7K3M-9Q2X-P4TN",    # 设备标识，仅用于标记变更来源；
                                               # 多台设备填同一个值即可共用同一份数据
          "schemaVersion": 1,                  # 客户端同步协议版本（缺省按 1 处理）
          "lastRev": 0,                        # 客户端已同步到的服务端游标
          "changes":  {"<table>": [ {<实体完整 JSON>}, ... ], ...},
          "tombstones": [ {"tbl": "<table>", "id": "...", "deletedAt": <ms>} ]
        }
        响应:
        {
          "rev": 42,                           # 客户端应保存为新的 lastRev
          "schemaVersion": 1,                  # 服务端协议版本
          "changes":  {"<table>": [ {...} ]},  # rev > lastRev 且非本设备产生的变更
          "tombstones": [ {"tbl": "...", "id": "...", "deletedAt": <ms>} ]
        }

合并规则：Last-Write-Wins。
  - 实体按 updatedAt 比较，较新者胜出；
  - 墓碑（tombstone）的 deletedAt >= 实体 updatedAt 时删除胜出；
  - 服务端只解析每条记录的 id / updatedAt（subject_summaries 表为 subjectId），
    其余字段原样透传存储，因此 App 端实体结构演进无需改动服务器。

协议版本（重要）：
  - SCHEMA_VERSION 是本服务器的同步数据结构版本。**新增/删除同步表、改变字段语义时必须 +1**，
    因为 TABLES 是硬编码白名单，旧服务器会把不认识的新表静默丢弃。
  - 客户端同步前先读 /api/ping 的 schemaVersion，低于自己就拒绝同步（一个字节都不上传）。
  - 本服务器再兜一层：请求里的 schemaVersion 高于本服务器时返回 409 server_outdated，
    响应体带 serverSchemaVersion，供客户端展示。
  - 没有 schemaVersion 字段的旧客户端按 1 处理（加版本号之前的协议就是 1）。

模型目录：
  模型列表不再随 App 发版，而是从本服务器的 model_catalog 表下发。
  首次启动表为空时用 DEFAULT_MODEL_CATALOG 种子填充；日常维护用
    --dump-catalog catalog.json   导出
    --import-catalog catalog.json 整表覆盖（版本 +1）后退出
  两者都不启动服务，改完重启服务器即可生效。
"""

import argparse
import json
import sqlite3
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# 允许同步的表：表名 -> (主键字段名, 更新时间字段名)
TABLES = {
    "subjects": ("id", "updatedAt"),
    "fragments": ("id", "updatedAt"),
    "notebook_pages": ("id", "updatedAt"),
    "folders": ("id", "updatedAt"),
    "home_conversations": ("id", "updated_at"),
    "home_messages": ("id", "updatedAt"),
    "subject_summaries": ("subjectId", "updatedAt"),
    "todo_items": ("id", "updatedAt"),
    "model_configs": ("id", "updatedAt"),
    # 更新源访问令牌（GitHub / Gitee）：单行 app_settings#singleton，客户端不建表
    "app_settings": ("id", "updatedAt"),
}

MAX_BODY_BYTES = 64 * 1024 * 1024  # 64 MB

# 同步数据结构版本。新增/删除下面 TABLES 里的表、或改变字段语义时必须 +1；
# 客户端同步前会读 /api/ping 的 schemaVersion，低于自己就拒绝同步。
SCHEMA_VERSION = 1

SCHEMA = """
CREATE TABLE IF NOT EXISTS entities (
    tbl        TEXT NOT NULL,
    id         TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    device     TEXT NOT NULL DEFAULT '',
    rev        INTEGER NOT NULL,
    data       TEXT NOT NULL,
    PRIMARY KEY (tbl, id)
);
CREATE TABLE IF NOT EXISTS tombstones (
    tbl        TEXT NOT NULL,
    id         TEXT NOT NULL,
    deleted_at INTEGER NOT NULL,
    device     TEXT NOT NULL DEFAULT '',
    rev        INTEGER NOT NULL,
    PRIMARY KEY (tbl, id)
);
CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS model_catalog (
    provider        TEXT NOT NULL,
    model_id        TEXT NOT NULL,
    name            TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    supports_vision INTEGER NOT NULL DEFAULT 0,
    thinking_levels TEXT,               -- NULL=沿用供应商默认档位；''=不支持思考；'OFF,HIGH'=显式档位
    sort_order      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (provider, model_id)
);
"""


# App 各供应商的内置模型列表，首次启动表为空时作为 model_catalog 的种子。
# 只影响「全新数据库」；已有数据库请用 --dump-catalog / --import-catalog 改表，改这个常量不会覆盖已有数据。
DEFAULT_MODEL_CATALOG = {
    "OPENAI": [
        {
            "id": "gpt-6-astra",
            "name": "GPT-6 Astra",
            "description": "最强旗舰，1.05M 上下文，复杂推理、编程、研究与文档创作",
            "supportsVision": True,
        },
        {
            "id": "gpt-6.1-sol",
            "name": "GPT-6.1 Sol",
            "description": "接近 Astra 的智能，价格约为其五分之一，适合日常高价值工作",
            "supportsVision": True,
        },
        {
            "id": "gpt-6.1-terra",
            "name": "GPT-6.1 Terra",
            "description": "快速高效，视觉推理与精细计算机操作，1.05M 上下文",
            "supportsVision": True,
        },
        {
            "id": "gpt-6-luna",
            "name": "GPT-6 Luna",
            "description": "成本最低，适合高并发、大批量的日常任务",
            "supportsVision": True,
        },
    ],
    "DOUBAO": [
        {
            "id": "doubao-seed-2-1-pro-260915",
            "name": "Doubao Seed 2.1 Pro",
            "description": "官方推荐旗舰，1M 上下文，Coding/Agent/多模态全面升级",
            "supportsVision": True,
        },
        {
            "id": "doubao-seed-2-1-lite-260915",
            "name": "Doubao Seed 2.1 Lite",
            "description": "全模态理解，1M 上下文，性能与成本均衡",
            "supportsVision": True,
        },
        {
            "id": "doubao-seed-evolving",
            "name": "Doubao Seed Evolving",
            "description": "周级迭代的 Coding & Agent 模型，1M 上下文，能力持续进化",
            "supportsVision": False,
        },
        {
            "id": "doubao-seed-2-1-pro-260628",
            "name": "Doubao Seed 2.1 Pro (0628)",
            "description": "面向生产级任务，升级编程、智能体与多模态能力",
            "supportsVision": True,
        },
        {
            "id": "doubao-seed-2-1-turbo-260628",
            "name": "Doubao Seed 2.1 Turbo",
            "description": "效果与成本均衡，适合高频调用",
            "supportsVision": True,
        },
    ],
    "XIAOMI": [
        {
            "id": "mimo-v2.6-pro",
            "name": "MiMo-V2.6-Pro",
            "description": "最新旗舰推理模型，全模态，面向长程任务与高价值工作",
            "supportsVision": True,
        },
        {
            "id": "mimo-v2.6-flash",
            "name": "MiMo-V2.6-Flash",
            "description": "全模态高效推理，高智能低成本，适合规模化调用",
            "supportsVision": True,
        },
        {
            "id": "mimo-v2.6-pro-ultraspeed",
            "name": "MiMo-V2.6-Pro-UltraSpeed",
            "description": "V2.6-Pro 旗舰性能，最高 20 倍推理速度，面向强实时场景",
            "supportsVision": True,
        },
    ],
    "BAILIAN": [
        {
            "id": "qwen4-max",
            "name": "Qwen 4 Max",
            "description": "最新旗舰，1M 上下文，推理、视觉与 Agent 能力全面升级",
            "supportsVision": True,
            "thinkingLevels": ["OFF", "LOW", "MEDIUM", "HIGH", "MAX"],
        },
        {
            "id": "qwen3.7-plus",
            "name": "Qwen 3.7 Plus",
            "description": "均衡之选，1M 上下文，文本、图像与视频理解，成本更优",
            "supportsVision": True,
        },
        {
            "id": "qwen3.8-flash",
            "name": "Qwen 3.8 Flash",
            "description": "成本优先，1M 上下文，适合高频规模化调用",
            "supportsVision": True,
        },
        {
            "id": "qwen3.8-max",
            "name": "Qwen 3.8 Max",
            "description": "上一代旗舰，1M 上下文，推理与视觉理解",
            "supportsVision": True,
            "thinkingLevels": ["OFF", "LOW", "MEDIUM", "MAX"],
        },
        {
            "id": "qwen-long",
            "name": "Qwen Long",
            "description": "1000 万上下文，适合长文档分析与总结",
            "supportsVision": False,
            "thinkingLevels": [],
        },
    ],
    "ZHIPU": [
        {
            "id": "glm-5.3",
            "name": "GLM-5.3",
            "description": "最新旗舰，编程与智能体能力最强，1M 上下文（始终开启思考）",
            "supportsVision": False,
            "thinkingLevels": ["LOW", "HIGH", "MAX"],
        },
        {
            "id": "glm-5.3-flash",
            "name": "GLM-5.3-Flash",
            "description": "原生多模态，可理解图片/视频/文件，成本极低",
            "supportsVision": True,
            "thinkingLevels": ["LOW", "HIGH", "MAX"],
        },
        {
            "id": "glm-5.3-flashx",
            "name": "GLM-5.3-FlashX",
            "description": "GLM-5.3-Flash 高速版，推理速度约 200 tokens/s",
            "supportsVision": True,
            "thinkingLevels": ["LOW", "HIGH", "MAX"],
        },
        {
            "id": "glm-5v-turbo",
            "name": "GLM-5V-Turbo",
            "description": "多模态 Coding 基座，视觉理解、推理与代码生成，200K 上下文",
            "supportsVision": True,
            "thinkingLevels": ["LOW", "HIGH", "MAX"],
        },
        {
            "id": "glm-5.2",
            "name": "GLM-5.2",
            "description": "复杂长程任务稳定执行，Coding 从代码生成走向工程交付",
            "supportsVision": False,
            "thinkingLevels": ["OFF", "LOW", "MEDIUM", "HIGH", "MAX"],
        },
        {
            "id": "glm-4.7",
            "name": "GLM-4.7",
            "description": "日常主力，性价比高，通用对话与推理",
            "supportsVision": False,
        },
        {
            "id": "glm-4.7-flash",
            "name": "GLM-4.7-Flash",
            "description": "免费文本模型，快速响应，轻量任务",
            "supportsVision": False,
        },
        {
            "id": "glm-4.6v",
            "name": "GLM-4.6V",
            "description": "视觉推理模型，支持 128K 上下文",
            "supportsVision": True,
        },
    ],
    "DEEPSEEK": [
        {
            "id": "deepseek-flash",
            "name": "DeepSeek-V4.1-Flash",
            "description": "最新旗舰，1M 上下文，原生多模态，性能与成本全面超越 V4-Pro",
            "supportsVision": True,
        },
        {
            "id": "deepseek-v4-pro",
            "name": "DeepSeek-V4-Pro",
            "description": "上一代旗舰，1M 上下文，通用对话、推理与 Agent 任务",
            "supportsVision": False,
        },
    ],
    "KIMI": [
        {
            "id": "kimi-k3.1-pro",
            "name": "Kimi K3.1 Pro",
            "description": "最新旗舰，1M 上下文，原生视觉，推理与 Agent 能力再升级",
            "supportsVision": True,
        },
        {
            "id": "kimi-k3.1-pro-highspeed",
            "name": "Kimi K3.1 Pro Highspeed",
            "description": "K3.1 Pro 高速版，约 2～3 倍速度，1M 上下文，原生视觉",
            "supportsVision": True,
        },
        {
            "id": "kimi-k3",
            "name": "Kimi K3",
            "description": "上一代旗舰，1M 上下文，原生视觉，编程与 Agent 能力",
            "supportsVision": True,
        },
    ],
    "HUNYUAN": [
        {
            "id": "hy4",
            "name": "Hy4",
            "description": "最新旗舰，1M 上下文，Agent 与长程任务能力最强，支持图片理解",
            "supportsVision": True,
        },
        {
            "id": "hy4-flash",
            "name": "Hy4 Flash",
            "description": "轻量高效，1M 上下文，适合日常高频调用，支持图片理解",
            "supportsVision": True,
        },
        {
            "id": "hy3",
            "name": "Hy3",
            "description": "上一代主力模型，256K 上下文，效果与成本均衡",
            "supportsVision": False,
            "thinkingLevels": ["OFF", "LOW", "HIGH"],
        },
    ],
}


def now_ms() -> int:
    return int(time.time() * 1000)


class SyncStore:
    """SQLite 存储，所有操作都在内部串行化（个人级部署足够）。"""

    def __init__(self, db_path: str):
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(db_path, check_same_thread=False)
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.executescript(SCHEMA)
        self._conn.commit()
        self._seed_catalog_if_empty()

    def close(self):
        with self._lock:
            self._conn.close()

    # ---- 模型目录 ----

    @staticmethod
    def _meta_int(cur, key: str, default: int = 0) -> int:
        cur.execute("SELECT value FROM meta WHERE key=?", (key,))
        row = cur.fetchone()
        return int(row[0]) if row else default

    @staticmethod
    def _set_meta_int(cur, key: str, value: int):
        cur.execute(
            "INSERT INTO meta(key, value) VALUES(?,?) "
            "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            (key, value),
        )

    def _seed_catalog_if_empty(self):
        """首次启动（表为空）时灌入 App 的内置模型列表，保证客户端一上来就有完整目录。"""
        with self._lock:
            cur = self._conn.cursor()
            cur.execute("SELECT COUNT(*) FROM model_catalog")
            if int(cur.fetchone()[0]) > 0:
                return
            rows = []
            for provider, models in DEFAULT_MODEL_CATALOG.items():
                for order, model in enumerate(models):
                    levels = model.get("thinkingLevels")
                    rows.append((
                        provider,
                        model["id"],
                        model["name"],
                        model.get("description", ""),
                        1 if model.get("supportsVision") else 0,
                        None if levels is None else ",".join(levels),
                        order,
                    ))
            cur.executemany(
                "INSERT INTO model_catalog(provider, model_id, name, description, "
                "supports_vision, thinking_levels, sort_order) VALUES(?,?,?,?,?,?,?)",
                rows,
            )
            self._set_meta_int(cur, "catalog_version", 1)
            self._set_meta_int(cur, "catalog_updated_at", now_ms())
            self._conn.commit()
            print("[seed] model_catalog: %d models / %d providers"
                  % (len(rows), len(DEFAULT_MODEL_CATALOG)), flush=True)

    def catalog(self, known_version=None) -> dict:
        """返回整份模型目录；known_version 与服务端一致时只回 unchanged。"""
        with self._lock:
            cur = self._conn.cursor()
            version = self._meta_int(cur, "catalog_version")
            if known_version is not None and known_version == version:
                return {"ok": True, "unchanged": True, "catalogVersion": version}
            cur.execute(
                "SELECT provider, model_id, name, description, supports_vision, thinking_levels "
                "FROM model_catalog ORDER BY provider, sort_order, model_id"
            )
            providers = {}
            for provider, model_id, name, description, vision, levels in cur.fetchall():
                item = {
                    "id": model_id,
                    "name": name,
                    "description": description or "",
                    "supportsVision": bool(vision),
                }
                # thinking_levels 为 NULL 时不下发该字段 -> 客户端沿用供应商默认档位
                if levels is not None:
                    item["thinkingLevels"] = [x for x in str(levels).split(",") if x]
                providers.setdefault(provider, []).append(item)
            return {
                "ok": True,
                "catalogVersion": version,
                "updatedAt": self._meta_int(cur, "catalog_updated_at"),
                "providers": providers,
            }

    def import_catalog(self, providers: dict) -> dict:
        """整表覆盖模型目录并让版本 +1，供 --import-catalog 使用。"""
        if not isinstance(providers, dict):
            raise ValueError("providers 必须是对象")
        rows = []
        for provider, models in providers.items():
            if not isinstance(models, list):
                raise ValueError("providers.%s 必须是数组" % provider)
            for order, model in enumerate(models):
                if not isinstance(model, dict):
                    raise ValueError("providers.%s[%d] 必须是对象" % (provider, order))
                model_id = str(model.get("id") or "").strip()
                name = str(model.get("name") or "").strip()
                if not model_id or not name:
                    raise ValueError("providers.%s[%d] 缺少 id 或 name" % (provider, order))
                levels = model.get("thinkingLevels")
                if levels is not None:
                    if not isinstance(levels, list):
                        raise ValueError("providers.%s[%d].thinkingLevels 必须是数组" % (provider, order))
                    levels = ",".join(str(x) for x in levels)
                rows.append((
                    str(provider), model_id, name, str(model.get("description") or ""),
                    1 if model.get("supportsVision") else 0, levels, order,
                ))
        with self._lock:
            cur = self._conn.cursor()
            try:
                cur.execute("DELETE FROM model_catalog")
                cur.executemany(
                    "INSERT INTO model_catalog(provider, model_id, name, description, "
                    "supports_vision, thinking_levels, sort_order) VALUES(?,?,?,?,?,?,?)",
                    rows,
                )
                version = self._meta_int(cur, "catalog_version") + 1
                self._set_meta_int(cur, "catalog_version", version)
                self._set_meta_int(cur, "catalog_updated_at", now_ms())
                self._conn.commit()
            except Exception:
                self._conn.rollback()
                raise
            return {"providers": len(providers), "models": len(rows), "catalogVersion": version}

    def _next_rev(self, cur) -> int:
        cur.execute(
            "INSERT INTO meta(key, value) VALUES('rev', 0) "
            "ON CONFLICT(key) DO NOTHING"
        )
        cur.execute("UPDATE meta SET value = value + 1 WHERE key = 'rev'")
        cur.execute("SELECT value FROM meta WHERE key = 'rev'")
        return int(cur.fetchone()[0])

    def sync(self, device_id: str, last_rev: int, changes: dict, tombstones: list) -> dict:
        with self._lock:
            cur = self._conn.cursor()
            try:
                # 本轮开始前的全局 rev：响应里只返回这个位置之前的数据，
                # 从而排除「本轮自己刚推上去的记录」，同时不依赖 deviceId 过滤
                # （多台设备共用同一个 deviceId 时也能互相同步）。
                cur.execute("SELECT value FROM meta WHERE key = 'rev'")
                row = cur.fetchone()
                base_rev = int(row[0]) if row else 0

                # ---- 1. 合并客户端推送的实体（LWW）----
                for tbl, rows in (changes or {}).items():
                    table_conf = TABLES.get(tbl)
                    if table_conf is None or not isinstance(rows, list):
                        continue
                    id_field, updated_field = table_conf
                    for row in rows:
                        if not isinstance(row, dict):
                            continue
                        rid = row.get(id_field)
                        updated_at = row.get(updated_field)
                        if not rid or not isinstance(updated_at, (int, float)):
                            continue
                        rid = str(rid)
                        updated_at = int(updated_at)

                        # 墓碑优先：已存在较新的删除记录则忽略该实体
                        cur.execute(
                            "SELECT deleted_at FROM tombstones WHERE tbl=? AND id=?",
                            (tbl, rid),
                        )
                        t = cur.fetchone()
                        if t and int(t[0]) >= updated_at:
                            continue

                        cur.execute(
                            "SELECT updated_at FROM entities WHERE tbl=? AND id=?",
                            (tbl, rid),
                        )
                        e = cur.fetchone()
                        if e and int(e[0]) > updated_at:
                            continue  # 服务端数据更新，拒绝旧写入

                        rev = self._next_rev(cur)
                        cur.execute(
                            "INSERT INTO entities(tbl, id, updated_at, device, rev, data) "
                            "VALUES(?,?,?,?,?,?) "
                            "ON CONFLICT(tbl, id) DO UPDATE SET "
                            "updated_at=excluded.updated_at, device=excluded.device, "
                            "rev=excluded.rev, data=excluded.data",
                            (tbl, rid, updated_at, device_id, rev, json.dumps(row, ensure_ascii=False)),
                        )

                # ---- 2. 合并客户端推送的墓碑 ----
                for ts in tombstones or []:
                    if not isinstance(ts, dict):
                        continue
                    tbl = ts.get("tbl")
                    rid = ts.get("id")
                    deleted_at = ts.get("deletedAt")
                    if tbl not in TABLES or not rid or not isinstance(deleted_at, (int, float)):
                        continue
                    rid = str(rid)
                    deleted_at = int(deleted_at)

                    # 实体更新于删除之后 -> 实体胜出，忽略墓碑
                    cur.execute(
                        "SELECT updated_at FROM entities WHERE tbl=? AND id=?",
                        (tbl, rid),
                    )
                    e = cur.fetchone()
                    if e and int(e[0]) > deleted_at:
                        continue

                    rev = self._next_rev(cur)
                    cur.execute(
                        "INSERT INTO tombstones(tbl, id, deleted_at, device, rev) "
                        "VALUES(?,?,?,?,?) "
                        "ON CONFLICT(tbl, id) DO UPDATE SET "
                        "deleted_at=MAX(tombstones.deleted_at, excluded.deleted_at), "
                        "device=excluded.device, rev=excluded.rev",
                        (tbl, rid, deleted_at, device_id, rev),
                    )
                    cur.execute("DELETE FROM entities WHERE tbl=? AND id=?", (tbl, rid))

                # ---- 3. 取出 lastRev 之后、本轮推送之前的变更 ----
                # 只按 rev 窗口筛选，不再按 device 排除本机：
                # 客户端可以手动把多台设备设成同一个 deviceId 来共用数据，
                # 若继续用 device 过滤，这些设备将永远拉不到对方推送的内容。
                out_changes: dict = {}
                cur.execute(
                    "SELECT tbl, data FROM entities WHERE rev > ? AND rev <= ? "
                    "ORDER BY rev",
                    (last_rev, base_rev),
                )
                for tbl, data in cur.fetchall():
                    out_changes.setdefault(tbl, []).append(json.loads(data))

                out_tombstones = []
                cur.execute(
                    "SELECT tbl, id, deleted_at FROM tombstones "
                    "WHERE rev > ? AND rev <= ? ORDER BY rev",
                    (last_rev, base_rev),
                )
                for tbl, rid, deleted_at in cur.fetchall():
                    out_tombstones.append({"tbl": tbl, "id": rid, "deletedAt": int(deleted_at)})

                cur.execute("SELECT value FROM meta WHERE key = 'rev'")
                row = cur.fetchone()
                current_rev = int(row[0]) if row else 0

                self._conn.commit()
                return {"rev": current_rev, "changes": out_changes, "tombstones": out_tombstones}
            except Exception:
                self._conn.rollback()
                raise


class SyncHandler(BaseHTTPRequestHandler):
    server_version = "LingjiSync/1.0"
    store: SyncStore = None   # 由 main() 注入
    token: str = None         # 可选访问令牌

    # 日志到 stdout，一行一个请求（flush 便于重定向到文件时实时查看）
    def log_message(self, fmt, *args):
        print("[%s] %s - %s" % (now_ms(), self.address_string(), fmt % args), flush=True)

    def _send_json(self, code: int, payload: dict):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self) -> bool:
        if not self.token:
            return True
        auth = self.headers.get("Authorization", "")
        return auth == "Bearer " + self.token

    def do_GET(self):
        path, _, query = self.path.partition("?")
        if path == "/api/ping":
            if not self._authorized():
                return self._send_json(401, {"ok": False, "error": "unauthorized"})
            return self._send_json(200, {
                "ok": True,
                "serverTime": now_ms(),
                "schemaVersion": SCHEMA_VERSION,
            })
        if path == "/api/model_catalog":
            if not self._authorized():
                return self._send_json(401, {"ok": False, "error": "unauthorized"})
            known = None
            for part in query.split("&"):
                key, _, value = part.partition("=")
                if key == "version" and value.isdigit():
                    known = int(value)
            return self._send_json(200, self.store.catalog(known))
        self._send_json(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        if self.path.split("?")[0] != "/api/sync":
            return self._send_json(404, {"ok": False, "error": "not found"})
        if not self._authorized():
            return self._send_json(401, {"ok": False, "error": "unauthorized"})

        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BODY_BYTES:
            return self._send_json(400, {"ok": False, "error": "bad content length"})

        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return self._send_json(400, {"ok": False, "error": "invalid json"})

        # 协议版本兜底：客户端比服务器新时一律拒绝，避免新表/新字段被旧服务器静默丢弃。
        # 缺省视为 1（加版本号之前的客户端），它们本来就不认识新结构，放行是安全的。
        client_schema = payload.get("schemaVersion", 1)
        if not isinstance(client_schema, (int, float)) or client_schema < 1:
            client_schema = 1
        client_schema = int(client_schema)
        if client_schema > SCHEMA_VERSION:
            print("[%s] refuse sync: client schema v%d > server v%d"
                  % (now_ms(), client_schema, SCHEMA_VERSION), flush=True)
            return self._send_json(409, {
                "ok": False,
                "error": "server_outdated",
                "serverSchemaVersion": SCHEMA_VERSION,
                "clientSchemaVersion": client_schema,
            })

        device_id = str(payload.get("deviceId") or "")
        if not device_id:
            return self._send_json(400, {"ok": False, "error": "deviceId required"})
        last_rev = payload.get("lastRev", 0)
        if not isinstance(last_rev, (int, float)) or last_rev < 0:
            last_rev = 0

        try:
            result = self.store.sync(
                device_id=device_id,
                last_rev=int(last_rev),
                changes=payload.get("changes") or {},
                tombstones=payload.get("tombstones") or [],
            )
        except Exception as exc:  # noqa: BLE001 - 服务器不应因单请求崩溃
            return self._send_json(500, {"ok": False, "error": str(exc)})

        self._send_json(200, dict(result, schemaVersion=SCHEMA_VERSION))


def main():
    parser = argparse.ArgumentParser(description="灵记 LingJi 云同步服务器")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址（默认 0.0.0.0）")
    parser.add_argument("--port", type=int, default=8765, help="监听端口（默认 8765）")
    parser.add_argument("--db", default="lingji_sync.db", help="SQLite 数据文件路径")
    parser.add_argument("--token", default=None, help="可选访问令牌；设置后客户端必须携带")
    parser.add_argument("--dump-catalog", metavar="FILE",
                        help="导出模型目录到 JSON 后退出（不启动服务），用于编辑前取现状")
    parser.add_argument("--import-catalog", metavar="FILE",
                        help="用 JSON 整表覆盖模型目录（版本 +1）后退出，不启动服务")
    args = parser.parse_args()

    store = SyncStore(args.db)

    if args.dump_catalog:
        data = store.catalog()
        data.pop("ok", None)
        with open(args.dump_catalog, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        store.close()
        print("已导出模型目录 -> %s（v%s，%d 个供应商）"
              % (args.dump_catalog, data.get("catalogVersion"),
                 len(data.get("providers") or {})), flush=True)
        return

    if args.import_catalog:
        with open(args.import_catalog, encoding="utf-8") as f:
            payload = json.load(f)
        providers = payload.get("providers", payload)
        summary = store.import_catalog(providers)
        store.close()
        print("已导入模型目录 <- %s：%d 个供应商 / %d 个模型，新版本 v%d"
              % (args.import_catalog, summary["providers"], summary["models"],
                 summary["catalogVersion"]), flush=True)
        return

    SyncHandler.store = store
    SyncHandler.token = args.token

    server = ThreadingHTTPServer((args.host, args.port), SyncHandler)
    print("灵记同步服务器已启动: http://%s:%d  (db=%s, auth=%s, schema=v%d)"
          % (args.host, args.port, args.db, "on" if args.token else "off", SCHEMA_VERSION),
          flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        store.close()


if __name__ == "__main__":
    main()
