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

    POST /api/sync/reset           （App「覆盖云端数据」使用）
        请求:
        {
          "deviceId": "LJ-...",               # 设备标识，仅用于日志
          "schemaVersion": 1,
          "tables": ["subjects", ...]          # 要清空的表；缺省清空 TABLES 全部
        }
        响应:
        {
          "rev": 43,                           # 清空后新的服务端游标
          "tables": [...],                     # 实际清空的表
          "schemaVersion": 1
        }
        说明：只删除 entities / tombstones 中指定表的记录，模型目录等其它数据不受影响。
        该接口属于可选能力：旧服务器返回 404 时，App 会提示升级服务端程序。

管理台（浏览器直接打开，方便查看与清理云端数据）：
    GET  /admin                     -> 内置网页管理台（零依赖，自动适配深浅色）
    GET  /api/admin/overview        -> 全局 rev、各表实体/墓碑计数、数据库体积
    GET  /api/admin/entities        -> 实体列表，支持 ?tbl=&q=&limit=&offset=
    GET  /api/admin/tombstones      -> 墓碑列表，参数同上
    POST /api/admin/delete_entity   -> {"tbl","id"} 删除单条实体（写墓碑并推进 rev）
    POST /api/admin/clear_table     -> {"tbl"} 清空该表实体与墓碑并推进 rev
  /api/admin/* 与其它接口一样受 --token 保护；/admin 页面本身不鉴权，
  页面内填入访问令牌后保存在浏览器 localStorage，后续请求自动携带。

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
import os
import sqlite3
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

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
        self._db_path = db_path
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

    # 信息岛：App「覆盖云端数据」——清空指定同步表的实体与墓碑，再推进一次 rev
    def reset(self, tables: list) -> dict:
        """清空指定同步表在云端的实体与墓碑，并推进一次 rev（覆盖云端数据用）。

        tables 为空表示清空全部同步表；非空时只接受已知表名，
        避免因表名写错而误清空整份云端数据。
        """
        with self._lock:
            cur = self._conn.cursor()
            try:
                if tables:
                    allowed = [t for t in tables if t in TABLES]
                    if not allowed:
                        raise ValueError("no known tables to reset")
                else:
                    allowed = list(TABLES.keys())
                placeholders = ",".join("?" * len(allowed))
                cur.execute("DELETE FROM entities WHERE tbl IN (%s)" % placeholders, allowed)
                cur.execute("DELETE FROM tombstones WHERE tbl IN (%s)" % placeholders, allowed)
                rev = self._next_rev(cur)
                self._conn.commit()
                return {"rev": rev, "tables": allowed}
            except Exception:
                self._conn.rollback()
                raise

    # ---- 管理台 ----

    def admin_overview(self) -> dict:
        """管理台首页：全局 rev、各表计数、数据库体积。"""
        with self._lock:
            cur = self._conn.cursor()
            cur.execute("SELECT tbl, COUNT(*) FROM entities GROUP BY tbl")
            ents = {t: int(c) for t, c in cur.fetchall()}
            cur.execute("SELECT tbl, COUNT(*) FROM tombstones GROUP BY tbl")
            tombs = {t: int(c) for t, c in cur.fetchall()}
            tables = [
                {"tbl": tbl, "entities": ents.get(tbl, 0), "tombstones": tombs.get(tbl, 0)}
                for tbl in TABLES
            ]
            try:
                db_bytes = os.path.getsize(self._db_path)
            except OSError:
                db_bytes = 0
            return {
                "ok": True,
                "rev": self._meta_int(cur, "rev"),
                "schemaVersion": SCHEMA_VERSION,
                "catalogVersion": self._meta_int(cur, "catalog_version"),
                "entities": sum(ents.values()),
                "tombstones": sum(tombs.values()),
                "dbBytes": db_bytes,
                "tables": tables,
            }

    def admin_entities(self, tbl=None, q="", limit=50, offset=0) -> dict:
        """分页列出云端实体；q 同时匹配 id 与实体 JSON。"""
        limit = max(1, min(int(limit or 50), 200))
        offset = max(0, int(offset or 0))
        where, args = [], []
        if tbl:
            if tbl not in TABLES:
                raise ValueError("unknown table")
            where.append("tbl=?")
            args.append(tbl)
        if q:
            where.append("(id LIKE ? OR data LIKE ?)")
            like = "%" + q + "%"
            args.extend([like, like])
        clause = ("WHERE " + " AND ".join(where)) if where else ""
        with self._lock:
            cur = self._conn.cursor()
            cur.execute("SELECT COUNT(*) FROM entities " + clause, args)
            total = int(cur.fetchone()[0])
            cur.execute(
                "SELECT tbl, id, updated_at, device, rev, data FROM entities " + clause
                + " ORDER BY rev DESC LIMIT ? OFFSET ?",
                args + [limit, offset],
            )
            rows = []
            for tbl_name, rid, updated_at, device, rev, data in cur.fetchall():
                try:
                    parsed = json.loads(data)
                except ValueError:
                    parsed = data
                rows.append({
                    "tbl": tbl_name,
                    "id": rid,
                    "updatedAt": int(updated_at),
                    "device": device,
                    "rev": int(rev),
                    "data": parsed,
                })
            return {"ok": True, "total": total, "limit": limit, "offset": offset, "rows": rows}

    def admin_tombstones(self, tbl=None, q="", limit=50, offset=0) -> dict:
        """分页列出云端墓碑；q 匹配 id。"""
        limit = max(1, min(int(limit or 50), 200))
        offset = max(0, int(offset or 0))
        where, args = [], []
        if tbl:
            if tbl not in TABLES:
                raise ValueError("unknown table")
            where.append("tbl=?")
            args.append(tbl)
        if q:
            where.append("id LIKE ?")
            args.append("%" + q + "%")
        clause = ("WHERE " + " AND ".join(where)) if where else ""
        with self._lock:
            cur = self._conn.cursor()
            cur.execute("SELECT COUNT(*) FROM tombstones " + clause, args)
            total = int(cur.fetchone()[0])
            cur.execute(
                "SELECT tbl, id, deleted_at, device, rev FROM tombstones " + clause
                + " ORDER BY rev DESC LIMIT ? OFFSET ?",
                args + [limit, offset],
            )
            rows = [{
                "tbl": tbl_name,
                "id": rid,
                "deletedAt": int(deleted_at),
                "device": device,
                "rev": int(rev),
            } for tbl_name, rid, deleted_at, device, rev in cur.fetchall()]
            return {"ok": True, "total": total, "limit": limit, "offset": offset, "rows": rows}

    def admin_delete_entity(self, tbl: str, rid: str) -> dict:
        """管理台删除单条实体：写墓碑并推进 rev，让客户端同步时收到删除。"""
        if tbl not in TABLES:
            raise ValueError("unknown table")
        rid = str(rid or "")
        if not rid:
            raise ValueError("id required")
        with self._lock:
            cur = self._conn.cursor()
            try:
                rev = self._next_rev(cur)
                deleted_at = now_ms()
                cur.execute(
                    "INSERT INTO tombstones(tbl, id, deleted_at, device, rev) VALUES(?,?,?,?,?) "
                    "ON CONFLICT(tbl, id) DO UPDATE SET deleted_at=excluded.deleted_at, "
                    "device=excluded.device, rev=excluded.rev",
                    (tbl, rid, deleted_at, "admin", rev),
                )
                cur.execute("DELETE FROM entities WHERE tbl=? AND id=?", (tbl, rid))
                self._conn.commit()
                return {"ok": True, "rev": rev, "tbl": tbl, "id": rid}
            except Exception:
                self._conn.rollback()
                raise

    def admin_clear_table(self, tbl: str) -> dict:
        """管理台清空单表：删除该表全部实体与墓碑并推进 rev。"""
        if tbl not in TABLES:
            raise ValueError("unknown table")
        with self._lock:
            cur = self._conn.cursor()
            try:
                cur.execute("DELETE FROM entities WHERE tbl=?", (tbl,))
                deleted_entities = cur.rowcount
                cur.execute("DELETE FROM tombstones WHERE tbl=?", (tbl,))
                deleted_tombstones = cur.rowcount
                rev = self._next_rev(cur)
                self._conn.commit()
                return {
                    "ok": True,
                    "rev": rev,
                    "tbl": tbl,
                    "deletedEntities": deleted_entities,
                    "deletedTombstones": deleted_tombstones,
                }
            except Exception:
                self._conn.rollback()
                raise

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


ADMIN_HTML = """<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>灵记 · 云同步管理台</title>
<style>
:root{
  --bg:#f5f6f8;--panel:#ffffff;--text:#1b1e23;--muted:#6b7280;--border:#e4e7ec;
  --accent:#3b6ef0;--accent-soft:#e8eefe;--danger:#d64545;--danger-soft:#fdecec;--chip:#eef1f6;
}
@media (prefers-color-scheme: dark){
  :root{
    --bg:#141619;--panel:#1c1f23;--text:#e7e9ec;--muted:#98a0aa;--border:#2a2f36;
    --accent:#6f9bff;--accent-soft:#20293c;--danger:#ff7070;--danger-soft:#3a2222;--chip:#23282f;
  }
}
*{box-sizing:border-box}
html,body{margin:0}
body{background:var(--bg);color:var(--text);font:14px/1.55 -apple-system,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}
header{position:sticky;top:0;z-index:9;display:flex;flex-wrap:wrap;align-items:center;gap:10px;padding:12px 20px;background:var(--panel);border-bottom:1px solid var(--border)}
header h1{margin:0;font-size:15px;font-weight:600;letter-spacing:.3px}
header .sub{color:var(--muted);font-size:12px}
header .spacer{flex:1}
input,select,button{font:inherit;color:inherit}
input,select{background:var(--bg);border:1px solid var(--border);border-radius:8px;padding:7px 10px;outline:none}
input:focus,select:focus{border-color:var(--accent)}
button{cursor:pointer;border:1px solid var(--border);background:var(--panel);border-radius:8px;padding:7px 12px}
button:hover{border-color:var(--accent)}
button.primary{background:var(--accent);border-color:var(--accent);color:#fff}
button.danger{color:var(--danger)}
button.danger:hover{background:var(--danger-soft);border-color:var(--danger)}
button:disabled{opacity:.45;cursor:not-allowed}
main{max-width:1080px;margin:0 auto;padding:18px 20px 40px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}
.card{background:var(--panel);border:1px solid var(--border);border-radius:12px;padding:12px 14px}
.card .k{color:var(--muted);font-size:12px}
.card .v{margin-top:4px;font-size:20px;font-weight:600}
section{margin-top:18px}
section h2{margin:0 0 8px;font-size:13px;color:var(--muted);font-weight:600}
.chips{display:flex;flex-wrap:wrap;gap:8px}
.chip{display:inline-flex;align-items:center;gap:8px;background:var(--panel);border:1px solid var(--border);border-radius:999px;padding:6px 12px;cursor:pointer}
.chip.on{background:var(--accent-soft);border-color:var(--accent);color:var(--accent)}
.chip .n{color:var(--muted);font-size:12px}
.chip.on .n{color:inherit;opacity:.8}
.toolbar{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:10px}
.toolbar .spacer{flex:1}
.tabs{display:flex;border:1px solid var(--border);border-radius:8px;overflow:hidden}
.tabs button{border:0;border-radius:0;background:transparent}
.tabs button.on{background:var(--accent-soft);color:var(--accent)}
.list{display:flex;flex-direction:column;gap:8px;margin-top:8px}
.item{background:var(--panel);border:1px solid var(--border);border-radius:10px}
.item>summary{display:flex;gap:10px;align-items:center;padding:10px 12px;cursor:pointer;list-style:none}
.item>summary::-webkit-details-marker{display:none}
.tag{font-size:12px;background:var(--chip);border-radius:6px;padding:2px 7px;color:var(--muted);flex:none}
.mono{font-family:ui-monospace,Consolas,monospace;font-size:12px}
.grow{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.meta{color:var(--muted);font-size:12px;white-space:nowrap}
.item pre{margin:0;padding:10px 12px;border-top:1px solid var(--border);overflow:auto;max-height:340px;font-family:ui-monospace,Consolas,monospace;font-size:12px}
.item .actions{padding:0 12px 10px}
.item .actions button{padding:5px 10px;font-size:12px}
.empty{padding:28px;text-align:center;color:var(--muted);background:var(--panel);border:1px dashed var(--border);border-radius:10px}
.pager{display:flex;gap:10px;align-items:center;justify-content:flex-end;margin-top:12px;color:var(--muted);font-size:12px}
#status{font-size:12px;color:var(--muted);min-height:18px}
#status.err{color:var(--danger)}
</style>
</head>
<body>
<header>
  <div>
    <h1>灵记 · 云同步管理台</h1>
    <div class="sub" id="conn">未连接</div>
  </div>
  <div class="spacer"></div>
  <input id="token" type="password" placeholder="访问令牌（可选）" autocomplete="off" style="width:180px">
  <button id="saveToken">保存令牌</button>
  <button class="primary" id="refresh">刷新</button>
</header>
<main>
  <div class="cards" id="cards"></div>

  <section>
    <h2>同步表</h2>
    <div class="chips" id="chips"></div>
  </section>

  <section>
    <h2>数据</h2>
    <div class="toolbar">
      <div class="tabs">
        <button id="tabEntities" class="on">实体</button>
        <button id="tabTombstones">墓碑</button>
      </div>
      <input id="q" placeholder="搜索 id / 内容" style="width:220px">
      <button id="search">搜索</button>
      <button id="clearSearch">清除筛选</button>
      <div class="spacer"></div>
      <select id="limit">
        <option value="20">每页 20</option>
        <option value="50">每页 50</option>
        <option value="100">每页 100</option>
      </select>
      <button class="danger" id="clearTable" disabled>清空当前表</button>
    </div>
    <div id="status"></div>
    <div class="list" id="list"></div>
    <div class="pager">
      <button id="prev">上一页</button>
      <span id="pageInfo"></span>
      <button id="next">下一页</button>
    </div>
  </section>
</main>
<script>
const $ = (s) => document.querySelector(s);
const state = { tab: 'entities', tbl: '', q: '', offset: 0, limit: 20, total: 0, busy: false, overview: null };
let token = localStorage.getItem('lingji_admin_token') || '';
$('#token').value = token;

function el(tag, cls, text) {
  const n = document.createElement(tag);
  if (cls) n.className = cls;
  if (text !== undefined && text !== null) n.textContent = String(text);
  return n;
}
function fmtBytes(n) {
  if (!n) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let i = 0, v = n;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return (i === 0 ? v : v.toFixed(1)) + ' ' + units[i];
}
function fmtTime(ms) {
  if (!ms) return '-';
  return new Date(ms).toLocaleString('zh-CN', { hour12: false });
}
function setStatus(msg, isErr) {
  const n = $('#status');
  n.textContent = msg || '';
  n.className = isErr ? 'err' : '';
}

async function api(path, opts) {
  const o = Object.assign({}, opts || {});
  o.headers = Object.assign({ 'Authorization': 'Bearer ' + token }, o.headers || {});
  const res = await fetch(path, o);
  let data = null;
  try { data = await res.json(); } catch (e) { data = null; }
  if (res.status === 401) throw new Error('需要访问令牌：请在右上角填写后点「保存令牌」');
  if (!res.ok || !data || data.ok === false) {
    throw new Error((data && data.error) || ('HTTP ' + res.status));
  }
  return data;
}

function renderChips() {
  const d = state.overview;
  if (!d) return;
  const chips = $('#chips');
  chips.textContent = '';
  const all = el('button', 'chip' + (state.tbl ? '' : ' on'), '全部');
  all.appendChild(el('span', 'n', d.entities + ' 实体'));
  all.addEventListener('click', () => {
    state.tbl = '';
    state.offset = 0;
    $('#clearTable').disabled = true;
    renderChips();
    loadList();
  });
  chips.appendChild(all);
  for (const t of d.tables) {
    const b = el('button', 'chip' + (state.tbl === t.tbl ? ' on' : ''), t.tbl);
    b.appendChild(el('span', 'n', t.entities + ' 实体 / ' + t.tombstones + ' 墓碑'));
    b.addEventListener('click', () => {
      state.tbl = state.tbl === t.tbl ? '' : t.tbl;
      state.offset = 0;
      $('#clearTable').disabled = !state.tbl;
      renderChips();
      loadList();
    });
    chips.appendChild(b);
  }
}

async function loadOverview() {
  const d = await api('/api/admin/overview');
  state.overview = d;
  const cards = $('#cards');
  cards.textContent = '';
  const items = [
    ['同步游标 rev', d.rev],
    ['实体总数', d.entities],
    ['墓碑总数', d.tombstones],
    ['模型目录版本', 'v' + d.catalogVersion],
    ['协议版本', 'v' + d.schemaVersion],
    ['数据库体积', fmtBytes(d.dbBytes)]
  ];
  for (const pair of items) {
    const c = el('div', 'card');
    c.appendChild(el('div', 'k', pair[0]));
    c.appendChild(el('div', 'v', pair[1]));
    cards.appendChild(c);
  }
  renderChips();
  $('#conn').textContent = '已连接 · ' + location.host + ' · ' + new Date().toLocaleTimeString('zh-CN', { hour12: false });
}

function renderList(rows) {
  const list = $('#list');
  list.textContent = '';
  if (!rows.length) {
    list.appendChild(el('div', 'empty', (state.q || state.tbl) ? '没有匹配的数据' : '暂无数据'));
    return;
  }
  for (const row of rows) {
    const item = el('details', 'item');
    const summary = el('summary');
    summary.appendChild(el('span', 'tag', row.tbl));
    summary.appendChild(el('span', 'mono grow', row.id));
    const meta = state.tab === 'entities'
      ? 'rev ' + row.rev + ' · ' + fmtTime(row.updatedAt)
      : 'rev ' + row.rev + ' · 删除于 ' + fmtTime(row.deletedAt);
    summary.appendChild(el('span', 'meta', meta));
    item.appendChild(summary);

    if (state.tab === 'entities') {
      item.appendChild(el('pre', 'mono', JSON.stringify(row.data, null, 2)));
      const actions = el('div', 'actions');
      const del = el('button', 'danger', '删除此实体');
      del.addEventListener('click', (ev) => {
        ev.preventDefault();
        if (!confirm('确定删除 ' + row.tbl + ' / ' + row.id + ' ？\\n将写入墓碑，客户端下次同步会同步删除。')) return;
        api('/api/admin/delete_entity', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ tbl: row.tbl, id: row.id })
        }).then(() => {
          setStatus('已删除 ' + row.id);
          return loadOverview();
        }).then(loadList).catch((e) => setStatus(e.message, true));
      });
      actions.appendChild(del);
      item.appendChild(actions);
    } else {
      item.appendChild(el('pre', 'mono',
        '设备: ' + (row.device || '-') + '\\n删除时间: ' + fmtTime(row.deletedAt) + '\\nrev: ' + row.rev));
    }
    list.appendChild(item);
  }
}

async function loadList() {
  if (state.busy) return;
  state.busy = true;
  const path = state.tab === 'entities' ? '/api/admin/entities' : '/api/admin/tombstones';
  const params = new URLSearchParams();
  if (state.tbl) params.set('tbl', state.tbl);
  if (state.q) params.set('q', state.q);
  params.set('limit', state.limit);
  params.set('offset', state.offset);
  try {
    const d = await api(path + '?' + params.toString());
    state.total = d.total;
    renderList(d.rows);
    const page = Math.floor(state.offset / state.limit) + 1;
    const pages = Math.max(1, Math.ceil(d.total / state.limit));
    $('#pageInfo').textContent = '第 ' + page + ' / ' + pages + ' 页 · 共 ' + d.total + ' 条';
    $('#prev').disabled = state.offset <= 0;
    $('#next').disabled = state.offset + state.limit >= d.total;
    setStatus('');
  } catch (e) {
    setStatus(e.message, true);
    $('#list').textContent = '';
  } finally {
    state.busy = false;
  }
}

function refreshAll() {
  loadOverview().then(loadList).catch((e) => setStatus(e.message, true));
}

function switchTab(tab) {
  state.tab = tab;
  state.offset = 0;
  $('#tabEntities').classList.toggle('on', tab === 'entities');
  $('#tabTombstones').classList.toggle('on', tab === 'tombstones');
  loadList();
}

$('#saveToken').addEventListener('click', () => {
  token = $('#token').value.trim();
  localStorage.setItem('lingji_admin_token', token);
  refreshAll();
});
$('#refresh').addEventListener('click', refreshAll);
$('#tabEntities').addEventListener('click', () => switchTab('entities'));
$('#tabTombstones').addEventListener('click', () => switchTab('tombstones'));
$('#search').addEventListener('click', () => {
  state.q = $('#q').value.trim();
  state.offset = 0;
  loadList();
});
$('#q').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    state.q = $('#q').value.trim();
    state.offset = 0;
    loadList();
  }
});
$('#clearSearch').addEventListener('click', () => {
  state.q = '';
  state.tbl = '';
  state.offset = 0;
  $('#q').value = '';
  $('#clearTable').disabled = true;
  renderChips();
  loadList();
});
$('#limit').addEventListener('change', () => {
  state.limit = parseInt($('#limit').value, 10) || 20;
  state.offset = 0;
  loadList();
});
$('#prev').addEventListener('click', () => {
  state.offset = Math.max(0, state.offset - state.limit);
  loadList();
});
$('#next').addEventListener('click', () => {
  state.offset += state.limit;
  loadList();
});
$('#clearTable').addEventListener('click', () => {
  const tbl = state.tbl;
  if (!tbl) return;
  if (!confirm('确定清空表 ' + tbl + ' 的全部云端数据（实体 + 墓碑）？\\n该操作会推进 rev；客户端本地数据不受影响，但下次同步可能重新上传。')) return;
  if (!confirm('再次确认：清空 ' + tbl + '，此操作不可撤销。')) return;
  api('/api/admin/clear_table', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tbl: tbl })
  }).then((r) => {
    setStatus('已清空 ' + tbl + '：删除实体 ' + r.deletedEntities + ' 条、墓碑 ' + r.deletedTombstones + ' 条');
    return loadOverview();
  }).then(loadList).catch((e) => setStatus(e.message, true));
});

refreshAll();
</script>
</body>
</html>
"""


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

    def _send_html(self, text: str):
        body = text.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self) -> bool:
        if not self.token:
            return True
        auth = self.headers.get("Authorization", "")
        return auth == "Bearer " + self.token

    def _read_json_body(self):
        """读取并解析 JSON 请求体；失败时已回包并返回 None。"""
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BODY_BYTES:
            self._send_json(400, {"ok": False, "error": "bad content length"})
            return None
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            self._send_json(400, {"ok": False, "error": "invalid json"})
            return None

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        if path in ("/", "/admin"):
            # 管理台页面本身不鉴权：访问令牌由页面输入后存在 localStorage，
            # 后续 /api/admin/* 请求带 Authorization 头校验。
            return self._send_html(ADMIN_HTML)
        if path.startswith("/api/admin/"):
            if not self._authorized():
                return self._send_json(401, {"ok": False, "error": "unauthorized"})
            tbl = query.get("tbl", [""])[0] or None
            q = query.get("q", [""])[0]
            try:
                limit = int(query.get("limit", ["50"])[0])
                offset = int(query.get("offset", ["0"])[0])
            except ValueError:
                return self._send_json(400, {"ok": False, "error": "invalid limit/offset"})
            try:
                if path == "/api/admin/overview":
                    return self._send_json(200, self.store.admin_overview())
                if path == "/api/admin/entities":
                    return self._send_json(200, self.store.admin_entities(tbl, q, limit, offset))
                if path == "/api/admin/tombstones":
                    return self._send_json(200, self.store.admin_tombstones(tbl, q, limit, offset))
            except ValueError as exc:
                return self._send_json(400, {"ok": False, "error": str(exc)})
            except Exception as exc:  # noqa: BLE001
                return self._send_json(500, {"ok": False, "error": str(exc)})
            return self._send_json(404, {"ok": False, "error": "not found"})
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
            raw_version = query.get("version", [""])[0]
            if raw_version.isdigit():
                known = int(raw_version)
            return self._send_json(200, self.store.catalog(known))
        self._send_json(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        path = urlparse(self.path).path
        if path in ("/api/admin/delete_entity", "/api/admin/clear_table"):
            if not self._authorized():
                return self._send_json(401, {"ok": False, "error": "unauthorized"})
            payload = self._read_json_body()
            if payload is None:
                return
            try:
                if path == "/api/admin/delete_entity":
                    result = self.store.admin_delete_entity(
                        str(payload.get("tbl") or ""), str(payload.get("id") or ""))
                else:
                    result = self.store.admin_clear_table(str(payload.get("tbl") or ""))
            except ValueError as exc:
                return self._send_json(400, {"ok": False, "error": str(exc)})
            except Exception as exc:  # noqa: BLE001
                return self._send_json(500, {"ok": False, "error": str(exc)})
            print("[%s] admin %s -> %s" % (now_ms(), path.rsplit("/", 1)[-1], result), flush=True)
            return self._send_json(200, result)
        if path not in ("/api/sync", "/api/sync/reset"):
            return self._send_json(404, {"ok": False, "error": "not found"})
        if not self._authorized():
            return self._send_json(401, {"ok": False, "error": "unauthorized"})

        payload = self._read_json_body()
        if payload is None:
            return
        if not isinstance(payload, dict):
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

        if path == "/api/sync/reset":
            tables = payload.get("tables")
            if tables is not None and not isinstance(tables, list):
                return self._send_json(400, {"ok": False, "error": "tables must be an array"})
            try:
                result = self.store.reset(tables or [])
            except Exception as exc:  # noqa: BLE001 - 服务器不应因单请求崩溃
                return self._send_json(500, {"ok": False, "error": str(exc)})
            print("[%s] reset cloud tables=%s by %s"
                  % (now_ms(), ",".join(result["tables"]), device_id), flush=True)
            return self._send_json(200, dict(result, schemaVersion=SCHEMA_VERSION))

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
    print("管理台: http://%s:%d/admin"
          % ("127.0.0.1" if args.host in ("0.0.0.0", "::") else args.host, args.port),
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
