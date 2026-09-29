#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
灵记 LingJi 云同步服务器（零依赖单文件版）

仅使用 Python 标准库，Python 3.8+ 即可运行：

    python lingji_sync_server.py --host 0.0.0.0 --port 8765
    python lingji_sync_server.py --port 8765 --token your-secret   # 启用访问令牌

数据存储在同目录下的 SQLite 文件中（默认 lingji_sync.db），无需任何外部服务。

协议（全部为 JSON）：
    GET  /api/ping                -> {"ok": true, "serverTime": <ms>}
    POST /api/sync
        请求:
        {
          "deviceId": "uuid",
          "lastRev": 0,                        # 客户端已同步到的服务端游标
          "changes":  {"<table>": [ {<实体完整 JSON>}, ... ], ...},
          "tombstones": [ {"tbl": "<table>", "id": "...", "deletedAt": <ms>} ]
        }
        响应:
        {
          "rev": 42,                           # 客户端应保存为新的 lastRev
          "changes":  {"<table>": [ {...} ]},  # rev > lastRev 且非本设备产生的变更
          "tombstones": [ {"tbl": "...", "id": "...", "deletedAt": <ms>} ]
        }

合并规则：Last-Write-Wins。
  - 实体按 updatedAt 比较，较新者胜出；
  - 墓碑（tombstone）的 deletedAt >= 实体 updatedAt 时删除胜出；
  - 服务端只解析每条记录的 id / updatedAt（subject_summaries 表为 subjectId），
    其余字段原样透传存储，因此 App 端实体结构演进无需改动服务器。
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
}

MAX_BODY_BYTES = 64 * 1024 * 1024  # 64 MB

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
"""


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

    def close(self):
        with self._lock:
            self._conn.close()

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

                # ---- 3. 取出 lastRev 之后、非本设备产生的变更 ----
                out_changes: dict = {}
                cur.execute(
                    "SELECT tbl, data FROM entities WHERE rev > ? AND device != ? "
                    "ORDER BY rev",
                    (last_rev, device_id),
                )
                for tbl, data in cur.fetchall():
                    out_changes.setdefault(tbl, []).append(json.loads(data))

                out_tombstones = []
                cur.execute(
                    "SELECT tbl, id, deleted_at FROM tombstones WHERE rev > ? AND device != ? "
                    "ORDER BY rev",
                    (last_rev, device_id),
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

    # 静默日志到 stdout，一行一个请求
    def log_message(self, fmt, *args):
        print("[%s] %s - %s" % (now_ms(), self.address_string(), fmt % args))

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
        if self.path.split("?")[0] == "/api/ping":
            if not self._authorized():
                return self._send_json(401, {"ok": False, "error": "unauthorized"})
            return self._send_json(200, {"ok": True, "serverTime": now_ms()})
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

        self._send_json(200, result)


def main():
    parser = argparse.ArgumentParser(description="灵记 LingJi 云同步服务器")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址（默认 0.0.0.0）")
    parser.add_argument("--port", type=int, default=8765, help="监听端口（默认 8765）")
    parser.add_argument("--db", default="lingji_sync.db", help="SQLite 数据文件路径")
    parser.add_argument("--token", default=None, help="可选访问令牌；设置后客户端必须携带")
    args = parser.parse_args()

    store = SyncStore(args.db)
    SyncHandler.store = store
    SyncHandler.token = args.token

    server = ThreadingHTTPServer((args.host, args.port), SyncHandler)
    print("灵记同步服务器已启动: http://%s:%d  (db=%s, auth=%s)"
          % (args.host, args.port, args.db, "on" if args.token else "off"))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        store.close()


if __name__ == "__main__":
    main()
