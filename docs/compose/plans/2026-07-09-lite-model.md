# Lite Model (轻量模型) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `LiteModelConfig` data class for a small/low-cost model that mirrors the main model's configuration and testing UI, stored alongside existing settings.

**Architecture:** New `LiteModelConfig` data class nested inside `AISettings`, persisted as 5 flat columns in `SettingsEntity` (consistent with existing pattern). The settings screen adds a "轻量模型配置" card that reuses all existing provider UI composables via adapter functions (`LiteModelConfig.toAISettings()` / `AISettings.toLiteModelConfig()`). The `providerApiKeys` cache is shared between main and lite model.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, OkHttp

## Global Constraints

- Language: Kotlin + Jetpack Compose, Hilt DI, Room DB, OkHttp for LLM calls
- Verification command: `./gradlew :app:compileDebugKotlin`
- Install command: `./gradlew :app:installDebug`
- String resources go in `app/src/main/res/values/strings.xml`, grouped by feature with comments
- Follow existing flat-column pattern for SettingsEntity
- No changes to existing provider UI components or LLMService
- `providerApiKeys` cache is shared: same provider name = same key for both main and lite model

---

### Task 1: Data Layer - LiteModelConfig + Persistence + Migration

**Covers:** Data model definition, Room persistence, migration

**Files:**
- Modify: `app/src/main/java/com/lingji/app/domain/model/Models.kt`
- Modify: `app/src/main/java/com/lingji/app/data/db/entities/SettingsEntity.kt`
- Modify: `app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt`
- Modify: `app/src/main/java/com/lingji/app/di/AppModule.kt`
- Modify: `app/src/main/java/com/lingji/app/data/repository/SettingsRepository.kt`

**Interfaces:**
- Produces: `LiteModelConfig` data class with fields `provider: APIProvider`, `baseUrl: String`, `apiKey: String`, `modelName: String`, `enableThinking: Boolean`
- Produces: `AISettings.liteModel: LiteModelConfig` field
- Produces: `LiteModelConfig.toAISettings(providerApiKeys: Map<String, String>): AISettings` extension
- Produces: `AISettings.toLiteModelConfig(): LiteModelConfig` extension

- [ ] **Step 1: Add LiteModelConfig data class and liteModel field to AISettings**

In `Models.kt`, add the data class after `AISettings`:

```kotlin
/**
 * 轻量模型配置：用于低成本频繁调用的小模型。
 * 配置和测试方式与主模型一致，可在设置界面中独立配置。
 */
data class LiteModelConfig(
    val provider: APIProvider = APIProvider.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val enableThinking: Boolean = false
)
```

Add `liteModel` field to `AISettings`:

```kotlin
data class AISettings(
    val provider: APIProvider = APIProvider.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
    val enableThinking: Boolean = false,
    val horizontalSwipeAction: HorizontalSwipeAction = HorizontalSwipeAction.TOGGLE_PREVIEW,
    /** 按供应商名缓存的 API Key，切换供应商时自动保存/恢复。 */
    val providerApiKeys: Map<String, String> = emptyMap(),
    /** 轻量模型配置：用于低成本频繁调用的小模型。 */
    val liteModel: LiteModelConfig = LiteModelConfig()
)
```

Add adapter extensions after the data class:

```kotlin
/** 将 LiteModelConfig 转为 AISettings，用于复用现有供应商 UI 组件。 */
fun LiteModelConfig.toAISettings(providerApiKeys: Map<String, String> = emptyMap()): AISettings = AISettings(
    provider = provider,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    enableThinking = enableThinking,
    providerApiKeys = providerApiKeys
)

/** 从 AISettings 提取模型配置部分，用于 lite model 回写。 */
fun AISettings.toLiteModelConfig(): LiteModelConfig = LiteModelConfig(
    provider = provider,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    enableThinking = enableThinking
)
```

- [ ] **Step 2: Add 5 lite model columns to SettingsEntity**

In `SettingsEntity.kt`, add after `providerApiKeys`:

```kotlin
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: String = "singleton",
    val provider: String = "OPENAI",
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
    val enableThinking: Boolean = false,
    /** 笔记页左右横滑手势行为：NONE / TOGGLE_PREVIEW / CHANGE_PAGE */
    val horizontalSwipeAction: String = "TOGGLE_PREVIEW",
    /** 按供应商名缓存的 API Key JSON，如 {"OPENAI":"sk-xxx","DEEPSEEK":"..."} */
    val providerApiKeys: String = "{}",
    /** 轻量模型配置 */
    val liteProvider: String = "OPENAI",
    val liteBaseUrl: String = "",
    val liteApiKey: String = "",
    val liteModelName: String = "",
    val liteEnableThinking: Boolean = false
)
```

- [ ] **Step 3: Add MIGRATION_10_11 to LingjiDatabase**

In `LingjiDatabase.kt`, bump version to 11 and add migration:

```kotlin
@Database(
    entities = [...],
    version = 11,
    exportSchema = false
)
```

Add in companion object after `MIGRATION_9_10`:

```kotlin
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN liteProvider TEXT NOT NULL DEFAULT 'OPENAI'")
        db.execSQL("ALTER TABLE settings ADD COLUMN liteBaseUrl TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE settings ADD COLUMN liteApiKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE settings ADD COLUMN liteModelName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE settings ADD COLUMN liteEnableThinking INTEGER NOT NULL DEFAULT 0")
    }
}
```

- [ ] **Step 4: Register MIGRATION_10_11 in AppModule**

In `AppModule.kt`, add to `.addMigrations(...)`:

```kotlin
.addMigrations(
    LingjiDatabase.MIGRATION_1_2,
    LingjiDatabase.MIGRATION_2_3,
    LingjiDatabase.MIGRATION_3_4,
    LingjiDatabase.MIGRATION_4_5,
    LingjiDatabase.MIGRATION_5_6,
    LingjiDatabase.MIGRATION_6_7,
    LingjiDatabase.MIGRATION_7_8,
    LingjiDatabase.MIGRATION_8_9,
    LingjiDatabase.MIGRATION_9_10,
    LingjiDatabase.MIGRATION_10_11
)
```

- [ ] **Step 5: Update SettingsRepository mapping**

In `SettingsRepository.kt`, update `toDomain()`:

```kotlin
private fun SettingsEntity.toDomain(): AISettings {
    val keys = runCatching { gson.fromJson<Map<String, String>>(providerApiKeys, mapType) }
        .getOrNull() ?: emptyMap()
    return AISettings(
        provider = runCatching { APIProvider.valueOf(provider.uppercase()) }.getOrDefault(APIProvider.OPENAI),
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        enableThinking = enableThinking,
        horizontalSwipeAction = runCatching { HorizontalSwipeAction.valueOf(horizontalSwipeAction) }
            .getOrDefault(HorizontalSwipeAction.TOGGLE_PREVIEW),
        providerApiKeys = keys,
        liteModel = LiteModelConfig(
            provider = runCatching { APIProvider.valueOf(liteProvider.uppercase()) }.getOrDefault(APIProvider.OPENAI),
            baseUrl = liteBaseUrl,
            apiKey = liteApiKey,
            modelName = liteModelName,
            enableThinking = liteEnableThinking
        )
    )
}
```

Update `toEntity()`:

```kotlin
private fun AISettings.toEntity() = SettingsEntity(
    provider = provider.name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    enableThinking = enableThinking,
    horizontalSwipeAction = horizontalSwipeAction.name,
    providerApiKeys = gson.toJson(providerApiKeys),
    liteProvider = liteModel.provider.name,
    liteBaseUrl = liteModel.baseUrl,
    liteApiKey = liteModel.apiKey,
    liteModelName = liteModel.modelName,
    liteEnableThinking = liteModel.enableThinking
)
```

Add import: `import com.lingji.app.domain.model.LiteModelConfig`

- [ ] **Step 6: Compile to verify data layer**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/lingji/app/domain/model/Models.kt app/src/main/java/com/lingji/app/data/db/entities/SettingsEntity.kt app/src/main/java/com/lingji/app/data/db/LingjiDatabase.kt app/src/main/java/com/lingji/app/di/AppModule.kt app/src/main/java/com/lingji/app/data/repository/SettingsRepository.kt
git commit -m "feat: add LiteModelConfig data class with Room persistence and migration"
```

---

### Task 2: ViewModel - Lite Model Save & Test Methods

**Covers:** ViewModel methods for saving and testing lite model

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt`

**Interfaces:**
- Consumes: `AISettings.liteModel`, `LiteModelConfig.toAISettings()`, `LLMService.testConnection()`, `LLMService.testMultimodalConnection()`
- Produces: `SubjectViewModel.saveLiteModelSettings(LiteModelConfig)`, `SubjectViewModel.testLiteModelConnection(onResult)`, `SubjectViewModel.testLiteMultimodalConnection(imageBase64, onResult)`

- [ ] **Step 1: Add save and test methods for lite model**

In `SubjectViewModel.kt`, add after `testMultimodalConnection` (around line 697):

```kotlin
fun saveLiteModelSettings(liteModel: LiteModelConfig) {
    viewModelScope.launch {
        settingsRepository.save(_uiState.value.settings.copy(liteModel = liteModel))
    }
}

fun testLiteModelConnection(onResult: (Pair<Boolean, String>) -> Unit) {
    if (_uiState.value.isProcessing) return
    viewModelScope.launch {
        setProcessing(true, "正在测试轻量模型连接…")
        try {
            val liteSettings = _uiState.value.settings.liteModel
                .toAISettings(_uiState.value.settings.providerApiKeys)
            val result = llmService.testConnection(liteSettings)
            onResult(result)
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(aiErrorMessage = e.message ?: "测试失败") }
            onResult(false to (e.message ?: "测试失败"))
        } finally {
            setProcessing(false)
        }
    }
}

fun testLiteMultimodalConnection(imageBase64: String, onResult: (Pair<Boolean, String>) -> Unit) {
    if (_uiState.value.isProcessing) return
    viewModelScope.launch {
        setProcessing(true, "正在测试轻量模型多模态连接…")
        try {
            val liteSettings = _uiState.value.settings.liteModel
                .toAISettings(_uiState.value.settings.providerApiKeys)
            val result = llmService.testMultimodalConnection(liteSettings, imageBase64)
            onResult(result)
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(aiErrorMessage = e.message ?: "多模态测试失败") }
            onResult(false to (e.message ?: "多模态测试失败"))
        } finally {
            setProcessing(false)
        }
    }
}
```

Add imports:
```kotlin
import com.lingji.app.domain.model.LiteModelConfig
import com.lingji.app.domain.model.toAISettings
```

- [ ] **Step 2: Compile to verify ViewModel**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/viewmodel/SubjectViewModel.kt
git commit -m "feat: add ViewModel methods for lite model save and test"
```

---

### Task 3: UI - Lite Model Settings Card + Strings

**Covers:** Settings screen UI for lite model configuration

**Files:**
- Modify: `app/src/main/java/com/lingji/app/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SubjectViewModel.saveLiteModelSettings()`, `SubjectViewModel.testLiteModelConnection()`, `SubjectViewModel.testLiteMultimodalConnection()`, `LiteModelConfig.toAISettings()`, `AISettings.toLiteModelConfig()`, existing `ProviderEndpointSettings`, `ProviderModelSettings`, `ApiKeyField`, `applyProviderPreset`, `providerDisplayName`

- [ ] **Step 1: Add string resources**

In `strings.xml`, after `test_multimodal_connection` (line 207), add:

```xml
<!-- 设置：轻量模型 -->
<string name="lite_model_settings">轻量模型配置</string>
<string name="lite_model_description">用于低成本频繁调用的小模型，配置方式与主模型一致</string>
```

- [ ] **Step 2: Add LiteModelSettingsCard composable to SettingsScreen**

In `SettingsScreen.kt`, add a new `liteTestResult` state variable inside `SettingsScreen` composable (after `testResult`):

```kotlin
var liteTestResult by remember { mutableStateOf("") }
var liteExpanded by remember { mutableStateOf(false) }
```

Add the lite model card in the Column, after the main "AI 配置" card (after line 275, before the editor gesture settings card):

```kotlin
// 轻量模型配置
SettingsCard(title = stringResource(R.string.lite_model_settings)) {
    Text(
        text = stringResource(R.string.lite_model_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
    val lite = settings.liteModel
    val liteAsSettings = lite.toAISettings(settings.providerApiKeys)

    ExposedDropdownMenuBox(
        expanded = liteExpanded,
        onExpandedChange = { liteExpanded = it }
    ) {
        SettingsOutlinedTextField(
            value = providerDisplayName(lite.provider),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.provider)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = liteExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = liteExpanded,
            onDismissRequest = { liteExpanded = false }
        ) {
            APIProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(providerDisplayName(provider)) },
                    onClick = {
                        val preset = applyProviderPreset(liteAsSettings, provider)
                        viewModel.saveSettings(
                            settings.copy(
                                liteModel = preset.toLiteModelConfig(),
                                providerApiKeys = preset.providerApiKeys
                            )
                        )
                        liteExpanded = false
                    }
                )
            }
        }
    }
    ProviderEndpointSettings(
        provider = lite.provider,
        settings = liteAsSettings,
        onSettingsChange = { newSettings ->
            viewModel.saveLiteModelSettings(newSettings.toLiteModelConfig())
        }
    )
    SettingsTextField(
        value = lite.baseUrl,
        onValueChange = { viewModel.saveLiteModelSettings(lite.copy(baseUrl = it)) },
        label = stringResource(R.string.base_url)
    )
    ApiKeyField(
        value = lite.apiKey,
        onValueChange = {
            viewModel.saveSettings(settings.copy(
                liteModel = lite.copy(apiKey = it),
                providerApiKeys = settings.providerApiKeys.toMutableMap().apply {
                    if (it.isNotBlank()) put(lite.provider.name, it) else remove(lite.provider.name)
                }
            ))
        },
        onCopy = {
            val key = lite.apiKey
            if (key.isBlank()) {
                Toast.makeText(context, R.string.api_key_empty, Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("API Key", key))
                Toast.makeText(context, R.string.api_key_copied, Toast.LENGTH_SHORT).show()
            }
        }
    )
    ProviderModelSettings(
        provider = lite.provider,
        settings = liteAsSettings,
        onSettingsChange = { newSettings ->
            viewModel.saveLiteModelSettings(newSettings.toLiteModelConfig())
        }
    )
    Button(
        onClick = {
            liteTestResult = ""
            viewModel.testLiteModelConnection { liteTestResult = it.second }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(stringResource(R.string.test_connection))
    }
    Button(
        onClick = {
            val base64 = context.getEmbeddedTestImageBase64()
            if (base64 != null) {
                liteTestResult = context.getString(R.string.multimodal_test_pending)
                viewModel.testLiteMultimodalConnection(base64) { liteTestResult = it.second }
            } else {
                liteTestResult = "无法加载测试图片"
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(stringResource(R.string.test_multimodal_connection))
    }
    if (liteTestResult.isNotBlank()) {
        Text(
            text = liteTestResult,
            style = MaterialTheme.typography.bodyMedium,
            color = if (liteTestResult.contains("成功") || liteTestResult.contains("ok", ignoreCase = true)) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
```

Add imports:
```kotlin
import com.lingji.app.domain.model.toAISettings
import com.lingji.app.domain.model.toLiteModelConfig
```

- [ ] **Step 3: Compile to verify UI**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Install to emulator**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL (or skip if no emulator available, with note)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingji/app/ui/screens/SettingsScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: add lite model settings card in settings screen"
```
