package com.lingji.app.domain.model

/**
 * 一份更新日志（对应一个已发布或正在开发的版本）。
 *
 * @param version 版本号，不带前缀 v，例如 "2.0.0c"
 * @param date 发布日期 yyyy-MM-dd
 * @param unreleased 该版本在生成数据时尚未正式发布（自测包）。这只是生成时的数据标记，
 *   界面上不做区分——版本一旦装到手机上就是「当前」，避免发版后安装包仍写着「未发布」。
 * @param sections 按「新功能 / 优化 / 修复」分组的内容
 */
data class ChangelogEntry(
    val version: String,
    val date: String,
    val unreleased: Boolean = false,
    val sections: List<ChangelogSection>
)

/**
 * 更新日志里的一个分组。
 *
 * @param title 分组标题，早期版本没有分组时为空白
 */
data class ChangelogSection(
    val title: String,
    val items: List<String>
)
