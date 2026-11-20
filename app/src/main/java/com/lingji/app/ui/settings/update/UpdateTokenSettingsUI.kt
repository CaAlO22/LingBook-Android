package com.lingji.app.ui.settings.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lingji.app.R
import com.lingji.app.ui.components.SettingsOutlinedTextField
import com.lingji.app.ui.viewmodel.UpdateViewModel

/**
 * 设置页「更新源访问令牌」分组内容。
 *
 * 匿名调用 GitHub / Gitee 接口会被按出口 IP 限流（GitHub 60 次/小时，Gitee 也是 IP 级），
 * 填自己的令牌后额度按账号计算，可大幅降低「检查更新失败」的概率。
 * 令牌默认只写入本机 SharedPreferences；只有开启「云同步访问令牌」后才会随云同步上传到用户自己的服务器。
 */
@Composable
fun UpdateTokenSettingsContent(
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val githubToken by viewModel.githubToken.collectAsState()
    val giteeToken by viewModel.giteeToken.collectAsState()
    var showGithubToken by rememberSaveable { mutableStateOf(false) }
    var showGiteeToken by rememberSaveable { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.update_token_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 16.dp)
    )

    SettingsOutlinedTextField(
        value = githubToken,
        onValueChange = viewModel::setGithubToken,
        label = { Text(stringResource(R.string.update_github_token)) },
        placeholder = { Text("ghp_...") },
        singleLine = true,
        visualTransformation = if (showGithubToken) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { showGithubToken = !showGithubToken }) {
                Icon(
                    imageVector = if (showGithubToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(R.string.cd_toggle_token_visibility)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
    Text(
        text = stringResource(R.string.update_token_hint_github),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )

    SettingsOutlinedTextField(
        value = giteeToken,
        onValueChange = viewModel::setGiteeToken,
        label = { Text(stringResource(R.string.update_gitee_token)) },
        placeholder = { Text("gitee_...") },
        singleLine = true,
        visualTransformation = if (showGiteeToken) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { showGiteeToken = !showGiteeToken }) {
                Icon(
                    imageVector = if (showGiteeToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(R.string.cd_toggle_token_visibility)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    )
    Text(
        text = stringResource(R.string.update_token_hint_gitee),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )

    val hasGithub = githubToken.isNotBlank()
    val hasGitee = giteeToken.isNotBlank()
    val statusText = when {
        hasGithub && hasGitee -> stringResource(R.string.update_token_status_both)
        hasGithub -> stringResource(R.string.update_token_status_github)
        hasGitee -> stringResource(R.string.update_token_status_gitee)
        else -> stringResource(R.string.update_token_status_none)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = if (hasGithub || hasGitee) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
        )
        if (hasGithub || hasGitee) {
            TextButton(
                onClick = {
                    viewModel.setGithubToken("")
                    viewModel.setGiteeToken("")
                    showGithubToken = false
                    showGiteeToken = false
                }
            ) {
                Text(
                    text = stringResource(R.string.update_token_clear),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
