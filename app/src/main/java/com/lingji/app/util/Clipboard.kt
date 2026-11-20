package com.lingji.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.lingji.app.R

/**
 * 复制纯文本到系统剪贴板并给出短提示。
 * 待办 / 碎片等列表的长按复制统一走这里，避免各页面重复实现。
 */
fun Context.copyPlainText(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    Toast.makeText(this, getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
}
