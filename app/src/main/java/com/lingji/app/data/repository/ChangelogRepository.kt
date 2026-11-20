package com.lingji.app.data.repository

import android.content.Context
import com.lingji.app.R
import com.lingji.app.domain.model.ChangelogEntry
import com.lingji.app.domain.model.ChangelogSection
import org.json.JSONArray

/**
 * 历代更新日志。
 *
 * 数据来自 `res/raw/changelog.json`（脚本 `tools/gen_changelog.py` 生成：抓取各版本
 * GitHub Release 的发布说明，并补一条当前开发版本的提交摘要），全部随包内置，
 * 不依赖网络——离线也能查看历史版本改了什么。
 */
object ChangelogRepository {

    /** 读取全部更新日志，按版本从新到旧排列；数据缺失时返回空列表，不影响设置页其它功能。 */
    fun load(context: Context): List<ChangelogEntry> = runCatching {
        val json = context.resources.openRawResource(R.raw.changelog)
            .bufferedReader()
            .use { it.readText() }
        parse(json)
    }.getOrDefault(emptyList())

    internal fun parse(json: String): List<ChangelogEntry> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val version = entry.optString("version").trim()
            if (version.isEmpty()) return@mapNotNull null
            val sections = entry.optJSONArray("sections").toSections()
            if (sections.isEmpty()) return@mapNotNull null
            ChangelogEntry(
                version = version,
                date = entry.optString("date"),
                unreleased = entry.optBoolean("unreleased", false),
                sections = sections
            )
        }
    }

    private fun JSONArray?.toSections(): List<ChangelogSection> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val section = optJSONObject(index) ?: return@mapNotNull null
            val items = section.optJSONArray("items").toStringList()
            if (items.isEmpty()) return@mapNotNull null
            ChangelogSection(
                title = section.optString("title"),
                items = items
            )
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length())
            .mapNotNull { optString(it).takeIf { text -> text.isNotBlank() } }
    }
}
