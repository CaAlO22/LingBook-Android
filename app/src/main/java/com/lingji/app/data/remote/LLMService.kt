package com.lingji.app.data.remote

import com.google.gson.Gson
import com.lingji.app.data.remote.models.ChatError
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.data.remote.models.ContentPart
import com.lingji.app.data.remote.models.ImageContentPart
import com.lingji.app.data.remote.models.ImageUrl
import com.lingji.app.data.remote.models.TextContentPart
import com.lingji.app.data.remote.strategy.RequestStrategy
import com.lingji.app.data.remote.strategy.RequestStrategyRegistry
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.provider.ProviderRegistry
import com.lingji.app.domain.model.Fragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMService @Inject constructor() {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun resolveEndpoint(baseUrl: String?): String {
        val url = (baseUrl ?: "https://api.openai.com/v1").trimEnd('/')
        return when {
            url.endsWith("/responses", true) -> url
            url.endsWith("/chat/completions", true) -> url
            Regex("/v\\d+\$").containsMatchIn(url) -> "$url/chat/completions"
            else -> "$url/chat/completions"
        }
    }

    private fun buildUserContent(
        prompt: String,
        images: List<String>,
        isResponses: Boolean,
        supportsVision: Boolean
    ): Any {
        if (images.isEmpty()) return prompt
        if (!supportsVision) {
            return "$prompt\n\n[系统提示：当前模型不支持图片输入，已忽略 ${images.size} 张图片。以下仅基于页面文字内容作答。]"
        }
        if (isResponses) {
            return "$prompt\n\n[附图片数据：${images.size} 张，请在分析时结合图片内容]"
        }
        val parts = mutableListOf<ContentPart>(TextContentPart(text = prompt))
        images.forEach { url ->
            parts.add(ImageContentPart(imageUrl = ImageUrl(url)))
        }
        return parts
    }

    suspend fun generate(
        prompt: String,
        settings: AISettings,
        systemPrompt: String? = null,
        images: List<String> = emptyList(),
        onWarning: (String) -> Unit = {}
    ): String =
        withContext(Dispatchers.IO) {
            val endpoint = resolveEndpoint(settings.baseUrl)
            val isResponses = endpoint.endsWith("/responses", true)
            val supportsVision = ProviderRegistry.supportsVision(settings.provider, settings.modelName)
            val strategy = RequestStrategyRegistry.get(settings.provider)
            if (images.isNotEmpty() && !supportsVision) {
                onWarning("当前模型 ${settings.modelName} 不支持图片输入，已忽略 ${images.size} 张图片。")
            }
            val messages = listOf(
                ChatMessage("system", systemPrompt ?: "你是一个有用的助手。"),
                ChatMessage("user", buildUserContent(prompt, images, isResponses, supportsVision))
            )
            val body = strategy.buildChatRequestBody(settings, messages, stream = false)
            val request = buildRequest(endpoint, body, strategy, settings)

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string() ?: ""
                    val detail = parseError(text)?.takeIf { it.isNotBlank() }
                    throw Exception(
                        buildString {
                            append("请求失败: ${response.code}")
                            if (detail != null) append(" - ").append(detail)
                        }
                    )
                }
                val text = response.body?.string() ?: ""
                val data = gson.fromJson(text, com.lingji.app.data.remote.models.ChatResponse::class.java)
                val messageContent = data.choices?.firstOrNull()?.message?.content
                val messageText = messageContent?.let { if (it is String) it else "" } ?: ""
                sanitizeOutput(messageText)
            }
        }

    suspend fun streamGenerate(
        prompt: String,
        settings: AISettings,
        systemPrompt: String? = null,
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        images: List<String> = emptyList(),
        onWarning: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val endpoint = resolveEndpoint(settings.baseUrl)
        val isResponses = endpoint.endsWith("/responses", true)
        val supportsVision = ProviderRegistry.supportsVision(settings.provider, settings.modelName)
        val strategy = RequestStrategyRegistry.get(settings.provider)
        if (images.isNotEmpty() && !supportsVision) {
            onWarning("当前模型 ${settings.modelName} 不支持图片输入，已忽略 ${images.size} 张图片。")
        }
        val messages = listOf(
            ChatMessage("system", systemPrompt ?: "你是一个有用的助手。"),
            ChatMessage("user", buildUserContent(prompt, images, isResponses, supportsVision))
        )
        val body = strategy.buildChatRequestBody(settings, messages, stream = true)
        val request = buildRequest(endpoint, body, strategy, settings)

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string() ?: ""
                val detail = parseError(text)?.takeIf { it.isNotBlank() }
                throw Exception(
                    buildString {
                        append("请求失败: ${response.code}")
                        if (detail != null) append(" - ").append(detail)
                    }
                )
            }
            val source = response.body?.source() ?: throw Exception("AI 响应为空")
            val acc = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ").trim()
                if (payload == "[DONE]") continue
                try {
                    val obj = gson.fromJson(payload, com.lingji.app.data.remote.models.ChatResponse::class.java)
                    val choice = obj.choices?.firstOrNull()
                    val delta = choice?.delta
                    val reasoning = choice?.reasoning_content
                        ?: delta?.reasoning_content
                    val deltaText = delta?.content?.let { if (it is String) it else "" } ?: ""
                    if (settings.enableThinking && reasoning != null && reasoning.isNotEmpty()) {
                        onReasoning(reasoning)
                    }
                    if (deltaText.isNotEmpty()) {
                        onToken(deltaText)
                        acc.append(deltaText)
                    }
                } catch (_: Exception) {
                }
            }
            sanitizeOutput(acc.toString())
        }
    }

    suspend fun testConnection(settings: AISettings): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val output = generate("PING", settings.copy(enableThinking = false), "只回复OK")
            val ok = Regex("\\b(ok|pong)\\b", RegexOption.IGNORE_CASE).containsMatchIn(output)
            ok to if (ok) "连接正常" else "返回异常: $output"
        }.getOrElse { false to (it.message ?: "连接失败") }
    }

    suspend fun testMultimodalConnection(settings: AISettings, imageBase64: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!ProviderRegistry.supportsVision(settings.provider, settings.modelName)) {
            return@withContext false to "当前模型 ${settings.modelName} 不支持图片输入"
        }
        runCatching {
            val output = generate(
                prompt = "请用一句话简要描述这张图片的主要内容。",
                settings = settings.copy(enableThinking = false),
                systemPrompt = "你是一个视觉助手，请根据用户提供的图片内容作答。",
                images = listOf(imageBase64)
            )
            val ok = output.isNotBlank()
            ok to if (ok) "多模态连接成功" else "多模态返回异常: 模型未返回内容"
        }.getOrElse { false to (it.message ?: "多模态连接失败") }
    }

    suspend fun mergeFragment(
        currentNote: String,
        newFragments: List<Fragment>,
        settings: AISettings,
        instruction: String? = null,
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit = {}
    ): String {
        val systemPrompt = "你是一个知识聚合专家。你的任务是将一系列新的信息片段无缝地整合到现有的结构化笔记（Markdown格式）中。保持结构、清晰度和语气。如果新的片段重复了现有信息，则进行合并或丢弃。如果是新的信息，请找到最佳插入位置或创建新部分。不要添加对话式填充，只输出更新后的完整Markdown笔记。并且：对于由“本次新片段”产生或修改的知识点，请在该句子或列表项末尾添加来源标注，格式为【#编号】或【#编号1,#编号2】（编号为下方新片段列表的序号）。不要在无关句子添加标注。"
        val content = newFragments.mapIndexed { i, f -> "碎片 ${i + 1}: ${f.content}" }.joinToString("\n\n---\n\n")
        val userPref = instruction?.let { "\n\n用户整理偏好:\n$it\n" } ?: ""
        val prompt = "现有笔记:\n$currentNote\n\n新的片段（多条，含序号）:\n$content$userPref\n请输出更新后的完整 Markdown 笔记（仅限笔记内容）。注意：对由本次新片段支撑的新增或修改的知识点，按【#编号】在句末标注来源；多个来源用逗号分隔。"
        return streamGenerate(prompt, settings, systemPrompt, onToken, onReasoning)
    }

    suspend fun refineNote(
        fragments: List<Fragment>,
        currentNote: String,
        settings: AISettings,
        instruction: String? = null,
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit = {}
    ): String {
        val systemPrompt = "你是一个专业的知识架构师。你的任务是基于用户收集的所有原始碎片信息，重新构建一份逻辑严密、结构清晰的完整笔记。并且：为每个知识点或关键句在句末添加来源碎片编号，格式为【#编号】或【#编号1,#编号2】（编号为下方碎片列表的序号）。标题可以不标注。"
        val content = fragments.mapIndexed { i, f -> "碎片 ${i + 1}: ${f.content}" }.joinToString("\n---\n")
        val pref = instruction?.let { "\n6. 用户整理偏好：$it\n" } ?: ""
        val prompt = "以下是该主题下收集的【全部原始碎片流】（带编号）：\n\n$content\n\n----------------\n\n【任务要求】：\n1. 请基于“全部原始碎片流”重新整理这份笔记。\n2. 你可以完全打散旧的结构，根据碎片之间的逻辑关系重新组织大纲。\n3. 合并重复的观点，将零散的信息点串联成通顺的段落。\n4. 使用 Markdown 格式输出（包含一级标题、二级标题、列表等）。\n5. 确保没有遗漏重要的碎片信息。\n6. 在每个知识点或关键句末尾标注来源碎片编号，格式【#编号】或【#编号1,#编号2】。$pref\n请直接输出重构后的 Markdown 内容："
        return streamGenerate(prompt, settings, systemPrompt, onToken, onReasoning)
    }

    suspend fun generateStudyPlan(
        noteContent: String,
        settings: AISettings,
        deadline: String? = null,
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit = {}
    ): String {
        val systemPrompt = "你是一个教育规划者。请根据提供的笔记创建一个学习计划。"
        val extra = if (!deadline.isNullOrBlank()) "\n\n【用户约束】\n- 完成时长：${deadline.trim()}\n- 请按该时长拆分为每日/每周任务，标注里程碑与检查点；输出用 Markdown 表格或列表呈现。" else ""
        val prompt = "笔记:\n$noteContent$extra\n\n请根据这些笔记创建一个包含学习目标和复习问题的结构化学习计划。"
        return streamGenerate(prompt, settings, systemPrompt, onToken, onReasoning)
    }

    suspend fun chatWithPage(
        pageContent: String,
        question: String,
        settings: AISettings,
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        images: List<String> = emptyList(),
        onWarning: (String) -> Unit = {},
        conversationHistory: List<Pair<String, String>> = emptyList(),
        pageTitle: String = ""
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = "你是一个有用的AI导师。请严格根据提供的上下文（包括文字与图片）回答用户的问题。回答要简洁明了。"

        // 从 markdown 文本里剥离 data:image base64 占位，避免长 base64 把真正的文本上下文淹没；
        // 同时让模型知道图片位置已通过多模态消息单独附上。
        val cleanedContent = stripDataImagesForChat(pageContent, images.size)
        val titleLine = if (pageTitle.isNotBlank()) "页面标题：${pageTitle.trim()}\n" else ""
        val context = buildString {
            append(titleLine)
            append("页面正文：\n")
            append(cleanedContent.ifBlank { "（本页无文字内容，仅含图片）" })
        }

        val endpoint = resolveEndpoint(settings.baseUrl)
        val isResponses = endpoint.endsWith("/responses", true)
        val supportsVision = ProviderRegistry.supportsVision(settings.provider, settings.modelName)
        val strategy = RequestStrategyRegistry.get(settings.provider)
        if (images.isNotEmpty() && !supportsVision) {
            onWarning("当前模型 ${settings.modelName} 不支持图片输入，已忽略 ${images.size} 张图片。")
        }

        val historyMessages = mutableListOf<ChatMessage>()
        // 历史轮次仅用纯文本上下文（一次的图片不重复发送），节约 token 并避免供应商对历史消息重复图片的限制。
        for ((q, a) in conversationHistory) {
            historyMessages.add(ChatMessage("user", "问题：$q"))
            historyMessages.add(ChatMessage("assistant", a))
        }
        // 当前轮：文字上下文 + 图片附件
        val currentPrompt = "$context\n\n问题：$question\n\n请基于以上页面内容（含图片）作答。"
        val currentUserContent = buildUserContent(currentPrompt, images, isResponses, supportsVision)
        historyMessages.add(ChatMessage("user", currentUserContent))

        val allMessages = listOf(ChatMessage("system", systemPrompt)) + historyMessages
        val body = strategy.buildChatRequestBody(settings, allMessages, stream = true)
        val request = buildRequest(endpoint, body, strategy, settings)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string() ?: ""
                val detail = parseError(text)?.takeIf { it.isNotBlank() }
                throw Exception(
                    buildString {
                        append("请求失败: ${response.code}")
                        if (detail != null) append(" - ").append(detail)
                    }
                )
            }
            val source = response.body?.source() ?: throw Exception("AI 响应为空")
            val acc = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ").trim()
                if (payload == "[DONE]") continue
                try {
                    val obj = gson.fromJson(payload, com.lingji.app.data.remote.models.ChatResponse::class.java)
                    val choice = obj.choices?.firstOrNull()
                    val delta = choice?.delta
                    val reasoning = choice?.reasoning_content
                        ?: delta?.reasoning_content
                    val deltaText = delta?.content?.let { if (it is String) it else "" } ?: ""
                    if (settings.enableThinking && reasoning != null && reasoning.isNotEmpty()) {
                        onReasoning(reasoning)
                    }
                    if (deltaText.isNotEmpty()) {
                        onToken(deltaText)
                        acc.append(deltaText)
                    }
                } catch (_: Exception) {}
            }
            acc.toString()
        }
    }

    /**
     * 将 markdown 里的 data:image base64 图片块替换为简短占位（[图片1]…），
     * 防止数万字符的 base64 把真正的文本上下文淹没。其它 markdown 文字保持不变。
     */
    private fun stripDataImagesForChat(content: String, imageCount: Int): String {
        if (imageCount == 0 && !content.contains("data:image", ignoreCase = true)) return content.trim()
        val regex = Regex("!\\[[^\\]]*]\\(data:image/[^)]+\\)")
        var index = 0
        return regex.replace(content) {
            index += 1
            "[图片$index]"
        }.trim()
    }

    private fun buildRequest(
        endpoint: String,
        body: ChatRequest,
        strategy: RequestStrategy,
        settings: AISettings
    ): Request {
        val builder = Request.Builder()
            .url(endpoint)
            .post(gson.toJson(body).toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
        strategy.applyHeaders(builder, settings.apiKey)
        return builder.build()
    }

    private fun parseError(text: String): String? {
        return runCatching {
            gson.fromJson(text, ChatError::class.java).message
        }.getOrNull() ?: text.takeIf { it.isNotBlank() }
    }

    companion object {
        private val gson = Gson()

        fun sanitizeOutput(text: String): String {
        if (text.isBlank()) return ""
        try {
            val obj = gson.fromJson(text, Map::class.java)
            listOf("final_answer", "final", "answer", "output", "output_text", "response", "content", "text").forEach { key ->
                val value = obj[key]
                if (value is String) return value.trim()
            }
        } catch (_: Exception) {
        }
        var cleaned = text.trim()
        listOf(
            Regex("<final_answer>(.*?)</final_answer>", RegexOption.DOT_MATCHES_ALL),
            Regex("<final>(.*?)</final>", RegexOption.DOT_MATCHES_ALL),
            Regex("<answer>(.*?)</answer>", RegexOption.DOT_MATCHES_ALL),
            Regex("<output>(.*?)</output>", RegexOption.DOT_MATCHES_ALL)
        ).forEach { re ->
            re.find(cleaned)?.let { return it.groupValues[1].replace(Regex("</?[^>]+>"), "").trim() }
        }
        listOf(
            Regex("(?:最终答案|答案)[:：]\\s*([\\s\\S]*)", RegexOption.IGNORE_CASE),
            Regex("Final\\s*Answer[:：]?\\s*([\\s\\S]*)", RegexOption.IGNORE_CASE),
            Regex("Answer[:：]?\\s*([\\s\\S]*)", RegexOption.IGNORE_CASE),
            Regex("Output[:：]?\\s*([\\s\\S]*)", RegexOption.IGNORE_CASE)
        ).forEach { re ->
            re.find(cleaned)?.groupValues?.get(1)?.let { return it.trim() }
        }
        val header = Regex("^#\\s+.+\$", RegexOption.MULTILINE).find(cleaned)
        if (header != null) {
            val h = header.value
            val first = cleaned.indexOf(h)
            val second = cleaned.indexOf(h, first + h.length)
            if (second > first) {
                val a = cleaned.substring(first, second).trim()
                val b = cleaned.substring(second).trim()
                if (a == b) return a
            }
        }
        if (cleaned.length % 2 == 0) {
            val half = cleaned.substring(0, cleaned.length / 2).trim()
            val other = cleaned.substring(cleaned.length / 2).trim()
            if (half == other) return half
        }
        return stripFence(cleaned)
        }

        private fun stripFence(input: String): String {
        var out = input.trim()
        listOf(
            Regex("^```[a-zA-Z0-9_-]*\\s*\$", RegexOption.MULTILINE) to Regex("```\\s*\$", RegexOption.MULTILINE),
            Regex("^~~~[a-zA-Z0-9_-]*\\s*\$", RegexOption.MULTILINE) to Regex("~~~\\s*\$", RegexOption.MULTILINE)
        ).forEach { (start, end) ->
            if (start.containsMatchIn(out) && end.containsMatchIn(out)) {
                val first = out.indexOf('\n')
                if (first != -1) {
                    val endMatch = end.find(out)
                    if (endMatch != null) {
                        val endIdx = out.lastIndexOf(endMatch.value)
                        if (endIdx > first) {
                            out = out.substring(first + 1, endIdx).trim()
                        }
                    }
                }
            }
        }
        return out
    }
}
}
