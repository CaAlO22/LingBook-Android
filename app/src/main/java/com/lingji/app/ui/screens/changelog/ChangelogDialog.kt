package com.lingji.app.ui.screens.changelog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingji.app.R
import com.lingji.app.domain.model.ChangelogEntry
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogDismissButton

/**
 * 历代更新日志弹窗。
 *
 * 从新到旧列出每个版本改了什么，随包内置（见 [com.lingji.app.data.repository.ChangelogRepository]），
 * 离线可用。当前安装的版本会带一个「当前」标记。
 */
@Composable
fun ChangelogDialog(
    entries: List<ChangelogEntry>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // 安装包的版本号：用于在列表里标出「当前」是哪一版
    val installedVersion = remember(context) {
        @Suppress("DEPRECATION")
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    LingjiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.changelog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (installedVersion.isNotBlank()) {
                        stringResource(R.string.changelog_current_version, installedVersion)
                    } else {
                        stringResource(R.string.changelog_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.changelog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(entries, key = { _, entry -> entry.version }) { index, entry ->
                            ChangelogVersionBlock(
                                entry = entry,
                                isInstalled = installedVersion.isNotBlank() && entry.version == installedVersion
                            )
                            if (index != entries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 14.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            LingjiDialogDismissButton(
                text = stringResource(R.string.changelog_close),
                onClick = onDismiss
            )
        }
    )
}

/** 单个版本：版本号 + 发布标记 + 日期，下面按分组列出改动条目。 */
@Composable
private fun ChangelogVersionBlock(
    entry: ChangelogEntry,
    isInstalled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "v${entry.version}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (isInstalled) {
                Spacer(modifier = Modifier.width(6.dp))
                VersionBadge(text = stringResource(R.string.changelog_badge_current))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = entry.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        entry.sections.forEach { section ->
            if (section.title.isNotBlank()) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }
            section.items.forEach { item ->
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    // 用一个小圆点代替项目符号，保持与正文基线对齐
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp, end = 6.dp)
                            .size(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}
