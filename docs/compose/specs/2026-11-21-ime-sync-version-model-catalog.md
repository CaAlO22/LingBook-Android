# 2026-11-21 长按选取不弹输入法 / 同步协议版本 / 模型目录云下发

三项改动，均不涉及数据库结构迁移。实现前先定取舍，下面记录**为什么这么做**，便于后续维护时不被"看起来更简单"的写法带回去。

---

## 一、长按选取文本时不弹输入法

### 现象与根因

笔记正文（`NoteEditor`）与笔记本页正文（`NotebookPageEditor`）用的是 value 版 `BasicTextField`。
长按进入选择模式时它会先请求焦点，焦点一到位就启动输入会话，输入法随之弹出：

1. 键盘直接盖住正在选的那几行；
2. 键盘 inset 变化触发宿主的"光标跟随"逻辑，页面被顶/滚一屏。

### 为什么不用 `KeyboardOptions(showKeyboardOnFocus = false)`

Compose 1.7.5 里这个开关只被新实现（`TextFieldDecoratorModifierNode`，即 `BasicTextField(state: TextFieldState)`）读取。
本项目用的是 value 版，底层走 `CoreTextField` → `TextFieldDelegate` → `PlatformTextInputService.startInput(TextFieldValue, ImeOptions, ...)`，
**整条链路都不传 `KeyboardOptions`**（`startInput` 签名里就没有这个参数），设了也不生效。
已核对 `foundation-release.aar` 1.7.5 字节码：全库只有 `KeyboardOptions` 与 `TextFieldDecoratorModifierNode` 引用该字段。
迁移到 `TextFieldState` 版本要重写撤销/方向键/滚动跟随，风险远大于收益，本次不搬。

### 采用的做法

新增 `ui/components/SelectionImeBehavior.kt`，用 `Modifier.pointerInput` 在 **Initial 阶段旁路观察**指针事件
（不消费，不影响文本选择、光标落位、长按拖拽手柄）：

| 手势 | 行为 |
| --- | --- |
| 短按（未越过 `longPressTimeout`，位移未超 `touchSlop`） | 下一帧主动 `show()`，保证"点一下还能打字" |
| 长按，且手势开始时字段**没有**焦点 | 下一帧 `hide()`，120ms 后再兜一次（压掉 `startInput` 的自动弹出） |
| 长按，但字段本来就聚焦、键盘已经开着（打字中途选词） | 不处理，避免平白把键盘收走 |

两条实现细节：

- 焦点状态用 `rememberUpdatedState` 读取，不能让 `pointerInput` 以 `focused` 为 key —— 长按瞬间焦点从 false 变 true，
  handler 会被重启，手势直接丢失。
- `hide()/show()` 一律 `launch` 到页面级 `rememberCoroutineScope` 并 `withFrameNanos {}` 后再执行：
  等 `CoreTextField` 处理完本次事件（请求焦点、启动会话），否则键盘命令会先于它的自动弹出，白做。

接入点：`NoteEditor`（`readOnly = false` 时）与 `NotebookPageEditor` 的正文输入框。

> 属于运行时行为，按本次约定不做实机验证；编译与单测只能保证不破坏既有逻辑。

---

## 二、云同步协议版本号

### 风险

服务器对同步表有**硬编码白名单**（`TABLES`）。App 新增一张表或改一条语义后，
旧服务器会把新数据静默丢弃（`table_conf is None → continue`），表现为"同步成功但换设备丢数据"。

### 版本语义

- App：`data/sync/SyncProtocol.kt` 的 `SCHEMA_VERSION`，**只在同步载荷结构变化时 +1**（新增表 / 改字段语义 / 改合并规则）；
  纯 UI 或客户端内部重构不动它。
- Server：同文件同名常量 `SCHEMA_VERSION`。
- **没有版本号字段的旧服务器按 1 处理**（加版本号之前的协议就是 1）。这样本次升级不会让任何现网服务器立刻失联。

### 双向校验

1. **客户端前置检查**（主闸门）：同步前先 `GET /api/ping` 取 `schemaVersion`；
   若 `server < client` → 本轮直接返回 `SyncResult.ServerOutdated`，**一个字节都不上传**。
2. **服务端兜底**：`/api/sync` 读请求里的 `schemaVersion`，若 `client > server` → `409 server_outdated`。
   防的是"客户端检查完到真正提交之间服务器被换回旧版"这类竞态。
3. 服务端版本更高（`server > client`）一律放行：旧客户端不认识的新字段它本来就会忽略。

设置页在协议不一致时显示红色文案：`服务器版本过旧（服务器 v1 / 应用 v2），请先升级服务器再同步`。

---

## 三、模型列表改为云端下发

### 目标

适配新模型不再发版：改服务器 SQLite 里的 `model_catalog` 表即可。

### 服务端

新增两张东西（同一个 SQLite 文件）：

```sql
CREATE TABLE model_catalog (
    provider       TEXT NOT NULL,   -- 与 APIProvider 枚举名一致：OPENAI / DOUBAO / ...
    model_id       TEXT NOT NULL,
    name           TEXT NOT NULL,
    description    TEXT NOT NULL DEFAULT '',
    supports_vision INTEGER NOT NULL DEFAULT 0,
    thinking_levels TEXT,           -- 逗号分隔；NULL=沿用供应商默认，''=不支持思考
    sort_order     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (provider, model_id)
);
```

- 首次启动且表为空时，用内置 `DEFAULT_MODEL_CATALOG` 种子填充（内容与 App 现有内置列表一致）。
- `meta` 里维护 `catalog_version`，每次 import 递增；客户端带 `?version=N` 请求，版本相同只回 `{"unchanged":true}`。
- `GET /api/model_catalog` 返回：

```json
{"ok":true,"catalogVersion":3,"updatedAt":1730000000000,
 "providers":{"OPENAI":[{"id":"gpt-6-astra","name":"GPT-6 Astra","description":"...",
                          "supportsVision":true,"thinkingLevels":["OFF","HIGH"]}]}}
```

- 运维入口（不需要装 sqlite3 客户端）：
  - `--dump-catalog file.json`：导出当前表 → 编辑 → 
  - `--import-catalog file.json`：整表覆盖并 `catalog_version + 1`，随后退出（不启动服务）。

### 客户端

- `domain/provider/RemoteModelCatalog.kt`：进程内快照（`mutableStateOf`，Compose 可订阅），
  保存 `version / updatedAt / Map<providerId, List<ProviderModel>>`。
- `data/sync/ModelCatalogRepository.kt`：抓取、解析、落 SharedPreferences（原文 JSON + 版本 + 时间），
  启动时先读缓存再按需刷新。
- `ProviderRegistry`：`config(provider)` 返回"叠加了云端目录"的代理对象（`ProviderConfig by base`），
  于是 `models`、`defaultModelId`、`supportsVision`、`thinkingLevelsFor()` 全部自动跟随，**调用点零改动**。
  - 云端给某个供应商的列表为空 → 视为未下发，继续用内置；
  - `defaultModelId` 若不在（新）列表里 → 取列表第一个，避免默认值指向已下线模型。
  - 数据库迁移（`LingjiDatabase`）改用 `builtInConfig()`，迁移必须与远端状态无关、结果可复现。
- 刷新时机：App 启动、每轮云同步（3 分钟）、设置页「刷新」按钮；服务端未变更时只回一个几十字节的 `unchanged`。
- 设置页「云同步」分组显示：`模型列表 v3 · 11/21 12:30`，带刷新按钮。

---

## 四、验收

| 项 | 方式 |
| --- | --- |
| 编译 | `gradlew.bat :app:compileDebugKotlin` |
| 单测 | `gradlew.bat :app:testDebugUnitTest`（新增版本判定、目录解析与叠加用例） |
| 服务端 | `python tools/verify_sync_protocol.py`：真起服务、真发 HTTP，覆盖版本拒绝、409 兜底、目录下发/未变更、dump/import |
| 实机 | **按约定跳过**（多 agent 共用模拟器冲突），运行时行为未验证 |

### 实际结果（2026-11-21）

- `:app:compileDebugKotlin` **BUILD SUCCESSFUL**（仅有既有告警：未使用参数、Haze 实验 API 等）。
- `:app:testDebugUnitTest`：**174 tests / 0 failures / 0 errors / 1 skipped**（跳过的是 `Codec82Test`，样例文件不在本机）。
  新增 24 条：
  - `SyncProtocolTest` 5 条（旧服务器缺字段按 1、非法值归一化、新旧版本兼容判定）；
  - `ModelCatalogParserTest` 8 条（字段裁剪、坏条目跳过、`thinkingLevels` 三态语义）；
  - `ProviderRegistryCatalogTest` 9 条（覆盖内置/远端切换、默认模型回落、视觉能力与档位跟随、迁移用 builtInConfig）；
  - `SyncApiPingTest` +2 条（`serverInfo` 解析 `schemaVersion`、缺字段回落 1）。
- `tools/verify_sync_protocol.py`：**25 项全部通过**，含"409 拒绝的写入确实没有落库"与"import 后运行中的服务立刻下发新模型"。

### 实现时补充的细节（文档前文未写）

- 目录解析抽成 `data/sync/ModelCatalogParser.kt`（纯函数）：单条缺 id/name 只跳过该条，整份失败才算失败。
- 旧服务器没有 `/api/model_catalog`（404/405）时显示"服务器未提供模型目录（旧版本），继续使用内置列表"，
  **不标红**——这是兼容状态，不是故障。
- 服务端首次启动灌种子时会打印 `[seed] model_catalog: 33 models / 8 providers`。
- 页标题（`UnderlinedTitleField`）也接入了长按不弹键盘；新建学科/文件夹的内联输入框保持原样（自动聚焦、以键盘输入为主）。
