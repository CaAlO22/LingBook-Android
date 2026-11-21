# 2026-11-21 模型配置默认开启思考（HIGH / 次高档）

## 需求

新建或切换模型配置时，思考档位**默认开启**；有强度档位的模型默认取 **HIGH**，
若该模型不支持 HIGH，则取它支持的**第二高**档位。只支持开关的模型取「开启」。

## 规则

规则集中在一处，供应商 Config 不再逐个覆盖：

```kotlin
// domain/provider/ProviderConfig.kt
fun List<ThinkingLevel>.defaultThinkingSelection(): ThinkingLevel {
    val enabled = distinct().filter { it != OFF }.sortedBy { it.ordinal }
    if (enabled.isEmpty()) return OFF          // 不支持思考
    if (HIGH in enabled) return HIGH           // 首选 HIGH
    return if (enabled.size >= 2) enabled[size - 2] else enabled.last()  // 次高
}
```

- 供应商级：`ProviderConfig.defaultThinkingLevel` 默认实现改为按 `defaultThinkingLevels` 推导，
  MiMo 的显式覆盖已删除（`[OFF, ON]` 推导结果仍是 ON）。
- 模型级：新增 `ProviderConfig.defaultThinkingLevelFor(modelId)`，按 `thinkingLevelsFor(modelId)` 推导；
  `ProviderRegistry.defaultThinkingLevel(provider, modelId?)` 增加可选 `modelId`。
- UI（`ModelConfigEditorDialog`）三处都改成按模型推导：
  - 初始值：`initial?.thinkingLevel ?: defaultThinkingLevel(provider, defaultModelId)`
  - 切供应商：按新供应商默认模型的档位
  - `LaunchedEffect(thinkingLevels)` 的兜底从 `firstOrNull()`（可能是 OFF/LOW）改为 `defaultThinkingSelection()`

## 各供应商实际默认

| 供应商 | 供应商级档位 | 供应商级默认 | 新建默认模型 | 模型级默认 |
| --- | --- | --- | --- | --- |
| OpenAI | OFF/LOW/MEDIUM/HIGH/MAX | HIGH | gpt-6-astra | HIGH |
| DeepSeek | OFF/LOW/HIGH/MAX | HIGH | deepseek-flash | HIGH |
| 豆包（Volcano） | OFF/LOW/MEDIUM/HIGH | HIGH | doubao-seed-2-1-pro | HIGH |
| 智谱 | OFF/ON（4.x 只有开关） | ON | glm-5.3（LOW/HIGH/MAX） | HIGH |
| 阿里百炼 | OFF/ON | ON | qwen4-max（全档） | HIGH |
| Kimi | LOW/HIGH/MAX（始终思考） | HIGH | kimi-k3.1-pro | HIGH |
| 腾讯混元 | OFF/ON（Hy4 只有开关） | ON | hy4 | ON |
| 小米 MiMo | OFF/ON | ON | mimo-v2.6-pro | ON |

模型级差异：

- `qwen3.8-max` 档位 `OFF/LOW/MEDIUM/MAX`，无 HIGH → **MEDIUM**（第二高）
- `qwen-long` 档位为空（不支持思考）→ **OFF**，设置页不显示该下拉
- `glm-5.3` / `glm-5.3-flash` / `glm-5v-turbo` 不可关闭思考 → **HIGH**
- `hy3` 档位 `OFF/LOW/HIGH` → **HIGH**

## 不改存量配置

只改「新建 / 切换模型」时的默认值。已保存的模型配置保持原值 —— 无法区分用户是主动关闭思考
还是沿用了旧默认，强制改写会覆盖真实选择。如需一次性迁移，应作为独立的数据迁移版本。

## 验证

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

新增 `ThinkingLevelDefaultsTest`（8 条）：

- 8 个供应商的默认档位逐一断言；并断言**没有任何供应商默认 OFF**
- 新建配置按 defaultModelId 推导（ZHIPU/BAILIAN/HUNYUAN/XIAOMI/KIMI）
- 无 HIGH 时取次高（qwen3.8-max → MEDIUM）
- 始终思考模型取 HIGH（glm-5.3、hy3）
- 不支持思考的模型保持 OFF（qwen-long）
- 规则本身：优先 HIGH、次高、单档、全 OFF、空列表、乱序列表
- 不变量：每个模型推导出的默认值都必须落在该模型声明的档位里

`MimoRequestStrategyTest` 中「其它供应商默认 OFF」的旧断言已更新为 HIGH。
全量单测：**172 tests / 0 failures / 0 errors / 1 skipped**。
