# 2026-11-21 内部 Agent 工具层：问题修复与逐条验收

本文记录对「灵记内部 Agent 操作笔记失败」问题报告的处理：**哪些建议采纳、哪些不采纳、为什么**，以及每条验收标准的复现命令与实际返回值。

---

## 一、判断与取舍

| 编号 | 建议 | 处理 | 理由 |
| --- | --- | --- | --- |
| P0-2 | 碎片写入口按 `subject.type` 校验（方案 A） | **采纳** | 确实是「写成功、读不到」的孤立脏数据。同时发现**同类漏洞**：`PageTools` 原实现只拦 FRAGMENT，TODO 笔记也能被 `create_page` 写入页面，而 `fullNoteContent()` 对 TODO 只读 `todo_items` —— 一并修正为 `type != NOTEBOOK` 即拒绝。 |
| P0-1 | 补待办项 CRUD | **采纳**，字段命名改为对齐项目现有风格 | 仓库层早已有 `addTodoItem / setTodoItemDone / updateTodoItemContent / deleteTodoItem`，只是工具层没暴露。返回 `id / content / is_done / index`（与 `list_fragments` 的 `id / content / is_merged` 一致），而非建议里的 `todo_id / text / done`；额外给了 `todo_index` 整数定位，比让 LLM 复述 UUID 更稳。 |
| P1-3 | 结构化错误码、文案只指向真实能力 | **采纳**，但保留 `Error: ` 前缀 | `AgentService` / `HomeAgentService` 的 systemPrompt 依赖「工具返回以 `"Error:"` 开头」判定失败，改成纯 JSON 会让 Agent 把失败当成功。最终格式为 `Error: {"code":...,"message":...,"suggestion":...}`。 |
| P1-4 | ID 改成 `前缀_base62_校验位`，旧 id 只读兼容 | **不采纳** | 见下方「为什么不改 ID」。 |
| P2-5 | 支持人类可读定位 | **采纳并扩大** | 这是 ID 抄错问题的治本解法，且不动任何存量数据：`subject_title`（在 `ToolRegistry` 一处统一处理）、`page_index / page_title`、`todo_index / todo_text`；重名一律返回结构化候选列表，不擅自选一个。 |
| — | （额外修） | **已修** | 两个 Agent 的 systemPrompt 原文写「笔记有两种类型：notebook 和 fragment」，Agent 根本不知道 TODO 存在；`create_subject` 的 type 枚举也只有 NOTEBOOK / FRAGMENT，无法创建待办笔记。两处都已补上 TODO。 |

### 为什么不改 ID 编码

1. **当前生成逻辑并没有混用两种格式**。`Models.kt` 的 `generateId()` 已经统一为完整 UUID，注释写明「云同步多设备场景下 8 位短 ID 碰撞风险过高」。报告里看到的 8 位短 hex 是**旧版本遗留的历史数据**，不是当前代码同时生成两种 id。
2. 改编码要动 Room 主键、云同步的增量比对与 tombstone（按 id 对齐）、`.ling` 导入导出、跨设备兼容，属于**全量数据迁移**；而报告自己要求「旧 id 只读兼容一个版本周期」，实际上仍是两套格式并存，恰恰是它要消除的风险。
3. 校验位对已有的 UUID / 短 hex 都无效，LLM 仍要逐 token 复述随机串。
4. 设备真实数据核对：2 篇笔记的 id **都是 36 位 UUID**，没有长短混排。用 `subject_title` / `todo_index` 定位，直接绕开了「复述随机串」这个根因，改动面小得多。

> 结论：ID 格式改造的收益（更短、可校验）远小于风险（数据迁移 + 同步协议变更）。若将来确要做，应作为独立的数据迁移版本，而不是夹在本次工具层修复里。

---

## 二、改动文件

**新增**
- `domain/tool/ToolSupport.kt`：统一错误码、结构化错误构造、按类型的真实可用建议、参数容错读取
- `domain/tool/PageLocator.kt`：页面 `page_id / page_index / page_title` 三选一定位
- `domain/tool/todo/TodoTools.kt`：`list_todos / add_todo / update_todo / delete_todo`
- `tools/check_subject_type_consistency.py`：只读的「类型 - 数据」一致性校验脚本（含 WAL）
- 测试：`TodoToolsTest` / `ToolRegistryTitleTest` / `ToolLayerVerificationTest`

**修改**
- `domain/tool/{fragment,page,note,subject}/*Tools.kt`：类型校验、可读定位、结构化错误
- `domain/tool/ToolRegistry.kt`：注册 TODO 工具、`subject_title` 的 schema 注入与解析
- `domain/tool/ScopedToolRegistry.kt`：单笔记作用域开放 4 个 todo 工具（18 → 22）
- `data/repository/SubjectRepository.kt`：`addTodoItem` 返回新建的 `TodoItem`（UI 侧忽略返回值，编译通过）
- `data/remote/{AgentService,HomeAgentService}.kt`：systemPrompt 补齐三种类型、TODO 工具、可读定位、错误码含义
- 既有测试同步更新；`Codec82Test` 修掉硬编码 `D:` 盘样例路径

---

## 三、工具层错误码（公开说明）

所有错误形如 `Error: {"code":"...","message":"...", ...}`，`suggestion` 为 `null` 表示「这条路不通，换策略，别重试同一调用」。

| code | 触发条件 | 可行动建议 |
| --- | --- | --- |
| `UNKNOWN_TOOL` | 工具名不存在 | 从工具列表选已存在的名字 |
| `MISSING_PARAMETER` | 必填参数缺失（含三选一定位一个都没给） | 补齐参数后重试 |
| `INVALID_PARAMETER` | 参数值非法（如 `content` 空白、未知笔记类型） | 按枚举/说明改值 |
| `SUBJECT_NOT_FOUND` | `subject_id` 合法但笔记不存在，或标题无匹配 | `list_subjects` 重新取 id；改用 `subject_title` |
| `OBJECT_NOT_FOUND` | 页面 / 碎片 / 待办项不存在或序号越界 | 先 `list_*` 重新取 id 或序号 |
| `UNSUPPORTED_FOR_SUBJECT_TYPE` | 工具与 `subject.type` 不匹配 | **按 `suggestion` 换工具，不要重试同一调用**；字段含 `subject_type`、`supported_types` |
| `AMBIGUOUS_TITLE` / `AMBIGUOUS_MATCH` | 标题或正文文本匹配到多条 | 把候选的 `index` 作为 `subject_index`，连同原 `subject_title` 重试；或用候选 `id` |
| `SUBJECT_INDEX_OUT_OF_RANGE` | `subject_index` 为负或超出本次候选数，或不是整数（非整数返回 `INVALID_PARAMETER`） | 按返回的 `candidates` / `candidate_count` 重新选一个有效 `index` |
| `EXECUTION_FAILED` | 工具执行抛异常被兜底捕获 | 视为失败，如实告知用户，不得声称已完成 |

---

## 四、逐条验收自验

### 复现命令

```bash
./gradlew :app:testDebugUnitTest --tests com.lingji.app.domain.tool.ToolLayerVerificationTest
# 逐字返回值（stdout）在：
# app/build/test-results/testDebugUnitTest/TEST-com.lingji.app.domain.tool.ToolLayerVerificationTest.xml 的 <system-out>
# 本次输出另存于 tmp/verification_returns.txt
```

以下 `RETURN` 均为上述测试**实际打印的返回值**（`<TODO>` = `cb720714-f26d-4f51-b4ed-1ba94e143cec`）。

### P0-2 验收：不存在「写成功但读不到」

```
CALL add_fragment(subject_id=<TODO>, content='任意文本')
RETURN Error: {"code":"UNSUPPORTED_FOR_SUBJECT_TYPE","message":"笔记「每日委托」是 TODO 类型，add_fragment 不支持该类型；仅支持 FRAGMENT 类型。","operation":"add_fragment","subject_id":"cb720714-...","subject_type":"TODO","supported_types":["FRAGMENT"],"suggestion":"这是待办笔记，请改用 list_todos / add_todo / update_todo / delete_todo 管理待办项"}

CALL create_page(subject_id=<TODO>, title='不该存在的页')
RETURN Error: {"code":"UNSUPPORTED_FOR_SUBJECT_TYPE",...,"subject_type":"TODO","supported_types":["NOTEBOOK"],...}
```

- `add_fragment` / `update_fragment` 对非 FRAGMENT 一律拒绝；`list_fragments` 对非 FRAGMENT 的历史数据标 `orphaned:true` + 警告；`delete_fragment` 保留跨类型能力用于**清理**，返回 `cleaned_orphan:true`；`search_fragments` 不再把这类脏数据当成命中。
- `create_page / update_page / delete_page / list_pages / get_page` 对非 NOTEBOOK 一律拒绝（原实现漏了 TODO）。
- 数据库写接口单测断言 `coVerify(exactly = 0) { repo.addFragment(...) }` / `repo.addPage(...)`，确认拒绝时**没有落库**。
- 存量脏数据识别：`python tools/check_subject_type_consistency.py <app_data 目录>`，只读、在临时副本上打开（含 WAL），逐条打印可执行的清理参数，不自动删除。

### P0-1 验收：TODO 可增删改查，且 `get_aggregated_note` 立即读回

```
CALL list_todos(subject_id=<TODO>)
RETURN [{"index":0,"id":"t1","content":"森空岛签到","is_done":false}]

CALL add_todo(subject_id=<TODO>, content='验证Agent工具')
RETURN {"id":"t-new","content":"验证Agent工具","is_done":false,"suggestion":"如需修改或勾选，调用 update_todo 并传 todo_id 或 todo_index"}

CALL get_aggregated_note(subject_id=<TODO>)  # 新增后立即读回
RETURN {"content":"- [ ] 森空岛签到\n- [ ] 验证Agent工具"}

CALL update_todo(subject_id=<TODO>, todo_index=1, done=true)
RETURN {"success":true,"id":"t-new","index":1,"content":"验证Agent工具","is_done":true}

CALL get_aggregated_note(subject_id=<TODO>)  # 勾选后立即读回
RETURN {"content":"- [ ] 森空岛签到\n- [x] 验证Agent工具"}

CALL update_todo(subject_id=<TODO>, todo_text='验证Agent工具', content='改过的文字')
RETURN {"success":true,"id":"t-new","index":1,"content":"改过的文字","is_done":true}

CALL delete_todo(subject_id=<TODO>, todo_index=1)
RETURN {"success":true,"deleted_id":"t-new","deleted_content":"改过的文字"}

CALL get_aggregated_note(subject_id=<TODO>)  # 删除后立即读回
RETURN {"content":"- [ ] 森空岛签到"}
```

TODO 的存储模型明确统一到 `todo_items` 表；`list_todos / add_todo / update_todo / delete_todo` 已注册到全局工具表与 `ScopedToolRegistry.SINGLE_NOTE_TOOLS`（单笔记 Agent 从 18 → 22 个工具）。**不借助 fragment 接口**即可完成全部操作。

### P1-3 验收：错误只指向真实能力

```
CALL edit_replace(subject_id=<TODO>, find='森空岛签到', occurrence=1, replace='x')
RETURN Error: {"code":"UNSUPPORTED_FOR_SUBJECT_TYPE","message":"笔记「每日委托」是 TODO 类型，edit_replace 不支持该类型；仅支持 NOTEBOOK / FRAGMENT 类型。",...,"suggestion":"待办列表不支持文本替换。请直接管理待办项：list_todos 查看、add_todo 新增、update_todo 改文字/勾选、delete_todo 删除。这些工具真实可用，改动会立即反映在 get_aggregated_note 中。"}
```

`NoteToolsTest.edit_replace_todo_returnsStructuredErrorPointingToRealTodoTools` 断言 4 个工具名全部出现在返回中，且 `updateAggregatedNote` 未被调用。错误码文档即本文第三节。

### P1-4 / P2-5 验收：可读定位与重名候选

```
CALL get_subject(subject_title='委托')  # 重名，候选带 0 起步 index
RETURN Error: {"code":"AMBIGUOUS_TITLE",...,"candidates":[{"index":0,"id":"cb720714-...","title":"每日委托","type":"TODO"},{"index":1,"id":"other","title":"每周委托","type":"TODO"}],"suggestion":"把 candidates 里目标笔记的 index 作为 subject_index，连同原 subject_title 一起重新调用；或用候选的 id 作为 subject_id 重新调用（不要凭空编造 id）"}

CALL get_subject(subject_title='每日委托')  # 唯一命中，无需复述 UUID
RETURN {"id":"cb720714-f26d-4f51-b4ed-1ba94e143cec","title":"每日委托","type":"TODO","page_count":0,"fragment_count":0,"todo_count":1}

CALL get_subject(subject_title='委托', subject_index=1)  # 二段式重试，全程不抄 UUID
RETURN {"id":"other","title":"每周委托","type":"TODO",...}

CALL get_subject(subject_title='委托', subject_index=9)  # 越界
RETURN Error: {"code":"SUBJECT_INDEX_OUT_OF_RANGE",...,"candidate_count":2,"candidates":[...],"suggestion":"用 candidates 里实际存在的 index 重新调用..."}
```

- 凡声明 `subject_id` 的工具，`ToolRegistry.toOpenAITools()` 自动追加 `subject_title` 与 `subject_index` 参数并把 `subject_id` 从 `required` 移除（二选一）；执行时精确匹配优先、包含匹配兜底、重名返回带 index 的候选。
- 重名二段式重试：`AMBIGUOUS_TITLE` → 原样带 `subject_title` + 候选的 `subject_index`（0、1…）→ 命中目标笔记，全程不需要 LLM 复述 36 位 UUID。越界返回 `SUBJECT_INDEX_OUT_OF_RANGE` 并附完整候选；`subject_index` 单独传返回 `MISSING_PARAMETER`，非整数返回 `INVALID_PARAMETER`；显式 `subject_id` 始终优先。
- 页面支持 `page_id / page_index / page_title`，待办支持 `todo_id / todo_index / todo_text`。
- 单笔记作用域下 `subject_title` 被移除并强制注入当前 `subject_id`，不会被 LLM 误用跨笔记。
- **P1-4「所有 id 同一格式」这条未满足**，理由见第一节；当前设备真实数据为 2 条 UUID，无长短混排。

---

## 五、环境与存量改动验证

| 项 | 命令 / 操作 | 实际结果 |
| --- | --- | --- |
| 基线（改前） | `./gradlew :app:testDebugUnitTest` | **98 tests, 1 failed**：`Codec82Test.decode user provided web ling82 file` 硬编码 `D:/.../tmp/web_lingcode.txt`，文件不存在 |
| 该失败的处理 | 改为优先相对路径 + `org.junit.Assume` 缺失时跳过 | 用例 skip=1，其余 8 条 Codec82 用例照常通过（未伪造数据、未删测试） |
| 几个会话留下的改动 | 同一套单测 | `SyncApiPingTest` 4、`DeviceIdFormatTest` 7、`LanServerScannerTest` 4、`ArrowKeyCursorBehaviorTest` 7 等**全部通过** |
| 最终全套 | `./gradlew :app:testDebugUnitTest` | **135 tests, 0 failures, 0 errors, 1 skipped**（新增 37 条） |
| 编译 | `./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL`，仅既有 warning |
| 安装 | `ANDROID_SERIAL=127.0.0.1:5555 ./gradlew :app:installDebug` | 首次 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（设备上是 release 签名 2.1.0）；**先用 root 完整备份数据（含 WAL）**，卸载后安装成功。过程中第二台设备 `0289147049983540` 上线（装的是 release 包），导致不指定设备时 Gradle 向两台安装、其中一台签名冲突而 FAILED；**显式指定 `ANDROID_SERIAL` 后 `BUILD SUCCESSFUL` / `Installed on 1 device`**。第二台设备未做任何改动（无数据备份，不擅自卸载） |
| APK 一致性 | 拉取设备上 `base.apk` 与本机产物对比 MD5 | `local=e1c699a795ad4a1424a159253773807f`、`device=e1c699a795ad4a1424a159253773807f`，**完全一致**；`run-as` 读取设备库：`subjects=2 / todo_items=34 / fragments=0` |
| 数据恢复 | `tmp/device_restore.py` | 合并 WAL 后回灌数据库 + `shared_prefs`，`chown` 到新 uid；首页截图显示「每日委托 0/9」「Test 3/25」，共 34 条待办，与库内 `todo_items=34` 一致 |
| 启动 | `logcat` | `Displayed com.lingji.app/.MainActivity: +1s94ms`，**无 FATAL EXCEPTION / Force finishing** |
| UI（含那几个会话的输入改动） | 打开 TODO 笔记 → 点击输入框 → `adb shell input text AgentToolCheck` | 输入框正常显示 `AgentToolCheck`、发送按钮出现、页面无跳动/遮挡、无崩溃（截图 `tmp/verify_input2.png`） |
| 存量脏数据 | `python tools/check_subject_type_consistency.py tmp/device_backup/app_data` | `笔记总数 2 / TODO=2 / 笔记 id 形态 UUID(36)=2`；fragments=0、pages=0、todo_items=34，**类型错配 0、无主记录 0，结论：未发现孤立数据** |

**设备侧未能验证的部分（如实说明）**：设备数据库 `settings` / `model_configs` 均为空，没有配置任何 LLM 模型，因此**无法在设备上实跑一次「用户自然语言 → Agent 调用 todo 工具」的端到端对话**。工具层行为由 135 条 JVM 单测（含上述逐字返回值的验证测试）覆盖；如需端到端验证，请先在设备上配置模型，再让首页助手对「每日委托」下发增/勾/改/删指令。

---

## 六、遗留与提醒

1. 设备上的包已从 **release 2.1.0（versionCode 44）替换为 debug 签名包**。数据已备份并恢复，但如需回到正式版，必须卸载后重装 release APK（签名不同不能覆盖升级）。备份在 `tmp/device_backup/lingji_data_backup.tar.gz`。
2. 历史存量 id 仍可能同时存在 8 位短 hex 与 UUID（本次设备上没有，但别的设备/导入的 `.ling` 可能有）。`subject_title` 已让 Agent 不必依赖 id；若要彻底统一格式，需单独做数据迁移版本。
3. 未做 `invalid_id` / `not_found` 的区分：现有 id 形态多样（UUID、短 hex、导入的自定义 id），在入口硬校验格式有误拒合法历史数据的风险，因此没有引入校验位。

---

## 七、追加：MiMo 模型适配

**需求**：MiMo 默认开启深度思考；多轮工具调用场景中开启思考时，完整回传历史 `reasoning_content` 以保持最佳效果。

按 `AGENTS.md` 的供应商解耦规范实现 —— 能力声明只放在 MiMo 自己的 Config，公共层读能力、不写 `when(provider)` 分支：

- `ProviderConfig` 新增两个**带默认实现**的属性，其它供应商文件一行未改：
  - `defaultThinkingLevel: ThinkingLevel = OFF` —— 新建模型配置时的默认档位
  - `requiresReasoningHistory: Boolean = false` —— 思考模式下是否要求回传历史思考内容
- `MimoConfig` 覆盖为 `ON` / `true`；`ProviderRegistry` 新增两个查询：
  `defaultThinkingLevel(provider)`（保证落在支持的档位里）、
  `requiresReasoningHistory(provider, level)`（思考关闭或供应商未声明时一律 `false`）。
- **默认开启**：`ModelConfigEditorDialog` 在新建配置、切换供应商、`LaunchedEffect` 回落时都取
  `ProviderRegistry.defaultThinkingLevel(...)`；MiMo 新建即选中 ON，`MimoRequestStrategy` 发
  `thinking: {"type":"enabled"}`（OFF 才发 `disabled`）。**已保存的用户配置不强制改写**，尊重显式关闭。
- **完整回传**：`AgentService` / `HomeAgentService` / `SubagentTool` 把 assistant(tool_calls) 与最终回答
  写入历史时，按 `keepReasoning` 原样保留 `message.reasoning_content`；`priorMessages` 会按当前模型能力
  规范化一次 —— 换模型或关闭思考后剥掉历史里残留的字段，避免发给不认识该字段的接口。
  `LLMService` 未改动：Gson 默认不序列化 null 字段，非 null 的 `reasoning_content` 会原样进入请求体。
- **已知边界**：跨 app 重启的会话历史只持久化「用户可见消息」（Room 表没有 `reasoning_content` 列），
  重启后历史里的思考内容会丢失。本次覆盖的是单次会话内（含内存缓存的下一轮提问）的多轮工具调用；
  要做到重启后仍完整，需单独做一次数据库迁移，不在本次范围。

### 验证

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests com.lingji.app.data.remote.strategy.MimoRequestStrategyTest
```

| 断言 | 结果 |
| --- | --- |
| `MimoConfig.defaultThinkingLevel == ON`、`ProviderRegistry.defaultThinkingLevel(XIAOMI) == ON` | 通过 |
| 其它供应商（OPENAI / DEEPSEEK）默认仍为 OFF | 通过 |
| `requiresReasoningHistory(XIAOMI, ON)=true`；`XIAOMI+OFF` / `OPENAI+ON` / `DEEPSEEK+ON` 均为 false | 通过 |
| OFF → `thinking.type=disabled`，ON → `enabled` | 通过 |
| 多轮工具调用历史（assistant 带 `tool_calls` + `reasoning_content`、tool 带 `tool_call_id`）序列化后 `reasoning_content` 内容完整、`tool_calls` 配对 | 通过 |
| `reasoning_content=null` 时不出现在请求 JSON 里 | 通过 |

最终 `compileDebugKotlin` 与 `testDebugUnitTest` **BUILD SUCCESSFUL，141 tests / 0 failures / 1 skipped**。
本次 MiMo 验证只用 JVM 编译与单测，**没有对真机发任何 adb 命令**；APK 同步显式限定
`ANDROID_SERIAL=127.0.0.1:5555`（模拟器），避免干扰另一个正在真机上工作的 agent。

---

## 七、重名候选 index + 二段式重试（2.2.0）

用户选定方案：候选列表加 0 起步的 `index`，允许 `subject_title + subject_index` 组合重试，
让 LLM 从「复述 36 位 UUID」彻底降级为「复述一个小整数」。

改动：

- `ToolRegistry.resolveSubject`：候选携带 `index`；`subject_index` 命中候选后注入对应 `subject_id`；
  越界返回新错误码 `SUBJECT_INDEX_OUT_OF_RANGE`（附 `candidate_count` 与完整 `candidates`）。
- `subject_index` 单独传 → `MISSING_PARAMETER`；非整数 → `INVALID_PARAMETER`；显式 `subject_id` 始终优先。
- `toOpenAITools()` 追加 `subject_index`（integer，minimum=0，非必填）并更新工具描述；
  两个 Agent 的 systemPrompt 说明二段式选法与新错误码。
- `ScopedToolRegistry` 在单笔记作用域移除 `subject_index`，避免与强制注入的 `subject_id` 冲突。

实际返回值（`ToolLayerVerificationTest` 逐字打印，另存 `tmp/verification_returns.txt`）：

```
CALL get_subject(subject_title='委托')
RETURN Error: {"code":"AMBIGUOUS_TITLE","message":"标题「委托」匹配到 2 篇笔记，无法自动确定，请从候选中选一个","subject_title":"委托","candidates":[{"index":0,"id":"cb720714-f26d-4f51-b4ed-1ba94e143cec","title":"每日委托","type":"TODO"},{"index":1,"id":"other","title":"每周委托","type":"TODO"}],"suggestion":"把 candidates 里目标笔记的 index 作为 subject_index，连同原 subject_title 一起重新调用；或用候选的 id 作为 subject_id 重新调用（不要凭空编造 id）"}

CALL get_subject(subject_title='委托', subject_index=1)
RETURN {"id":"other","title":"每周委托","type":"TODO","page_count":0,"fragment_count":0,"todo_count":0}

CALL get_subject(subject_title='委托', subject_index=9)
RETURN Error: {"code":"SUBJECT_INDEX_OUT_OF_RANGE","message":"subject_index=9 超出候选范围（有效值 0..1）","subject_title":"委托","subject_index":9,"candidate_count":2,"candidates":[...],"suggestion":"用 candidates 里实际存在的 index 重新调用；候选顺序可能变化，请按候选内容确认"}
```

`ToolRegistryTitleTest` 新增 9 条用例覆盖：候选 index、`index=0/1` 选中目标、越界不执行写操作、
缺 `subject_title`、非整数、`subject_id` 优先、schema 暴露可选 integer、单笔记作用域忽略 index。
最终 `compileDebugKotlin` + `testDebugUnitTest` **BUILD SUCCESSFUL，150 tests / 0 failures / 0 errors / 1 skipped**。
