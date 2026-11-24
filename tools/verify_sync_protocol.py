#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""云同步服务器端到端校验（无需 Android 设备）。

真起 `server/lingji_sync_server.py`，用 HTTP 走一遍：
  1. /api/ping 带 schemaVersion
  2. 旧版本服务器拒绝新客户端（这里用「新客户端打旧服务器」的反向场景模拟：客户端版本调高 -> 409）
  3. 正常同步（带 schemaVersion）与不带版本号的旧客户端
  4. /api/model_catalog 下发、版本未变时只回 unchanged
  5. --dump-catalog / --import-catalog 往返，导入后版本 +1、新模型出现在下发结果里

用法：python tools/verify_sync_protocol.py
"""

import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SERVER = os.path.join(ROOT, "server", "lingji_sync_server.py")

# 本地代理会拦截 127.0.0.1 的请求，必须显式绕开
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

PASSED = 0


def check(name: str, condition: bool, detail: str = ""):
    global PASSED
    if condition:
        PASSED += 1
        print("  [ok] %s" % name)
    else:
        print("  [FAIL] %s %s" % (name, detail))
        raise AssertionError(name)


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def request(method: str, url: str, body=None, token=None):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with OPENER.open(req, timeout=5) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read().decode("utf-8"))


def wait_ready(base: str, proc: subprocess.Popen, token: str, tries: int = 60):
    for _ in range(tries):
        if proc.poll() is not None:
            raise RuntimeError("server exited early: %s" % proc.stdout.read().decode("utf-8", "replace"))
        try:
            code, payload = request("GET", base + "/api/ping", token=token)
            if code == 200:
                return payload
        except Exception:
            pass
        time.sleep(0.1)
    raise RuntimeError("server did not become ready")


def child_env():
    env = dict(os.environ)
    env["PYTHONIOENCODING"] = "utf-8"
    for key in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy", "ALL_PROXY", "all_proxy"):
        env.pop(key, None)
    return env


def run_child(*args):
    out = subprocess.run(
        [sys.executable, SERVER, *args],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace",
        env=child_env(),
    )
    if out.returncode != 0:
        raise RuntimeError("child failed: %s\n%s" % (out.stdout, out.stderr))
    return out.stdout.strip()


def main() -> int:
    tmp = tempfile.mkdtemp(prefix="lingji_sync_")
    db_path = os.path.join(tmp, "sync.db")
    port = free_port()
    base = "http://127.0.0.1:%d" % port

    env = child_env()
    proc = subprocess.Popen(
        [sys.executable, SERVER, "--host", "127.0.0.1", "--port", str(port),
         "--db", db_path, "--token", "t0ken"],
        cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env,
    )
    try:
        print("[1] ping / 协议版本")
        ready = wait_ready(base, proc, "t0ken")
        check("ping 返回 serverTime", isinstance(ready.get("serverTime"), int) and ready["serverTime"] > 0)
        check("ping 返回 schemaVersion=2", ready.get("schemaVersion") == 2, str(ready))
        code, payload = request("GET", base + "/api/ping", token="wrong")
        check("错误令牌被拒", code == 401, str(code))

        print("[2] 客户端版本高于服务器 -> 409 兜底")
        code, payload = request("POST", base + "/api/sync", {
            "deviceId": "LJ-TEST-0001", "schemaVersion": 99, "lastRev": 0,
            "changes": {"subjects": [{"id": "s1", "updatedAt": 1, "title": "x"}]},
        }, token="t0ken")
        check("返回 409", code == 409, str(code))
        check("error=server_outdated", payload.get("error") == "server_outdated", str(payload))
        check("回带 serverSchemaVersion=2", payload.get("serverSchemaVersion") == 2, str(payload))
        # 被拒后不应写入任何数据：用正常请求拉一次全量，应为空
        code, payload = request("POST", base + "/api/sync", {
            "deviceId": "LJ-TEST-0001", "schemaVersion": 1, "lastRev": 0, "changes": {},
        }, token="t0ken")
        check("被拒的写入没有落库", payload.get("changes") == {} and payload.get("tombstones") == [],
              str(payload))

        print("[3] 正常同步")
        code, payload = request("POST", base + "/api/sync", {
            "deviceId": "LJ-TEST-0001", "schemaVersion": 2, "lastRev": 0,
            "changes": {"subjects": [{"id": "s1", "updatedAt": 1000, "title": "第一篇"}]},
            "tombstones": [],
        }, token="t0ken")
        check("同步成功", code == 200, str(code))
        check("响应带 schemaVersion=2", payload.get("schemaVersion") == 2, str(payload))
        check("rev 已推进", payload.get("rev", 0) >= 1, str(payload))
        code, payload = request("POST", base + "/api/sync", {
            "deviceId": "LJ-TEST-0002", "schemaVersion": 2, "lastRev": 0, "changes": {},
        }, token="t0ken")
        rows = (payload.get("changes") or {}).get("subjects") or []
        check("另一台设备能拉到", len(rows) == 1 and rows[0]["title"] == "第一篇", str(payload))
        # 不带 schemaVersion 的旧客户端按 1 处理，必须放行
        code, payload = request("POST", base + "/api/sync", {
            "deviceId": "LJ-LEGACY", "lastRev": 0, "changes": {},
        }, token="t0ken")
        check("旧客户端（无版本号）放行", code == 200 and "schemaVersion" in payload, str(code))

        print("[3.5] v1 客户端兼容（文件夹嵌套）")
        _, payload = request("POST", base + "/api/sync", {
            "deviceId": "LJ-TEST-0002", "schemaVersion": 2, "lastRev": 0,
            "changes": {"folders": [
                {"id": "fParent", "name": "父", "orderIndex": 1, "updatedAt": 1000},
                {"id": "fChild", "name": "子", "orderIndex": 1, "updatedAt": 2000, "parentId": "fParent"},
            ]},
        }, token="t0ken")
        rev_after_push = payload.get("rev", 0)
        _, payload = request("POST", base + "/api/sync", {
            "deviceId": "LJ-LEGACY", "schemaVersion": 1, "lastRev": 0, "changes": {},
        }, token="t0ken")
        child = {f["id"]: f for f in (payload.get("changes") or {}).get("folders") or []}["fChild"]
        check("v1 客户端拉到的文件夹不带 parentId", "parentId" not in child, str(child))
        # v1 客户端改名（不带 parentId），服务端应保留云端归属
        request("POST", base + "/api/sync", {
            "deviceId": "LJ-LEGACY", "schemaVersion": 1, "lastRev": rev_after_push,
            "changes": {"folders": [{"id": "fChild", "name": "子改名", "orderIndex": 1, "updatedAt": 3000}]},
        }, token="t0ken")
        _, payload = request("POST", base + "/api/sync", {
            "deviceId": "LJ-TEST-0002", "schemaVersion": 2, "lastRev": rev_after_push, "changes": {},
        }, token="t0ken")
        child = {f["id"]: f for f in (payload.get("changes") or {}).get("folders") or []}["fChild"]
        check("v1 改名后嵌套关系保留", child.get("parentId") == "fParent", str(child))

        print("[3.6] 管理台变更日志")
        code, payload = request("GET", base + "/api/admin/changes?limit=10", token="t0ken")
        labels = [r.get("label", "") for r in payload.get("rows") or []]
        check("变更日志可读名称", any("文件夹「子改名」" in x for x in labels), str(labels))
        check("变更日志区分新增/更新",
              {r.get("action") for r in payload.get("rows") or []} >= {"add", "update"}, str(labels))

        print("[4] 模型目录下发")
        code, catalog = request("GET", base + "/api/model_catalog", token="t0ken")
        check("目录请求成功", code == 200, str(code))
        providers = catalog.get("providers") or {}
        check("8 个供应商", len(providers) == 8, str(list(providers)))
        openai = {m["id"]: m for m in providers.get("OPENAI", [])}
        check("OpenAI 含 gpt-6-astra", "gpt-6-astra" in openai)
        check("未声明思考档位的模型不带 thinkingLevels", "thinkingLevels" not in openai["gpt-6-astra"])
        zhipu = {m["id"]: m for m in providers.get("ZHIPU", [])}
        check("GLM-5.3 带显式档位", zhipu["glm-5.3"].get("thinkingLevels") == ["LOW", "HIGH", "MAX"],
              str(zhipu["glm-5.3"]))
        baailian = {m["id"]: m for m in providers.get("BAILIAN", [])}
        check("Qwen Long 显式不支持思考", baailian["qwen-long"].get("thinkingLevels") == [],
              str(baailian["qwen-long"]))
        version = catalog["catalogVersion"]
        code, same = request("GET", base + "/api/model_catalog?version=%d" % version, token="t0ken")
        check("版本未变只回 unchanged", same.get("unchanged") is True, str(same))
        code, changed = request("GET", base + "/api/model_catalog?version=0", token="t0ken")
        check("版本不同回全量", "providers" in changed and not changed.get("unchanged"), str(changed)[:120])

        print("[5] dump / import 往返")
        dump_path = os.path.join(tmp, "catalog.json")
        out = run_child("--db", db_path, "--dump-catalog", dump_path)
        check("dump 命令成功", os.path.exists(dump_path), out)
        dumped = json.load(open(dump_path, encoding="utf-8"))
        check("dump 内容包含 providers", "providers" in dumped and dumped["providers"], str(dumped)[:80])
        dumped["providers"].setdefault("OPENAI", []).append({
            "id": "gpt-6.2-nova", "name": "GPT-6.2 Nova",
            "description": "校验用新增模型", "supportsVision": True,
            "thinkingLevels": ["OFF", "HIGH"],
        })
        import_path = os.path.join(tmp, "catalog_new.json")
        with open(import_path, "w", encoding="utf-8") as f:
            json.dump(dumped, f, ensure_ascii=False)
        out = run_child("--db", db_path, "--import-catalog", import_path)
        check("import 命令成功", "新版本" in out or "v" in out, out)

        # 服务进程持有的是同一个 SQLite 文件，import 后立即可见（WAL）
        code, catalog2 = request("GET", base + "/api/model_catalog", token="t0ken")
        check("导入后版本 +1", catalog2.get("catalogVersion") == version + 1, str(catalog2.get("catalogVersion")))
        ids = [m["id"] for m in catalog2["providers"]["OPENAI"]]
        check("新模型已下发", "gpt-6.2-nova" in ids, str(ids))
        print("\n全部通过：%d 项" % PASSED)
        return 0
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()


if __name__ == "__main__":
    sys.exit(main())
