package com.lingji.app.util

import android.content.Context
import android.content.res.Configuration
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * 是否接着物理键盘（蓝牙/USB 外接键盘，或二合一本的磁吸键盘）。
 *
 * 用途：AI 对话结束后是否把焦点还给输入框。
 * - 有物理键盘：焦点还回去，用户可以直接继续敲字，软键盘不会弹出；
 * - 没有物理键盘：不还焦点，避免软键盘自动弹起把用户正在看的回答挡住。
 */
fun Context.hasHardwareKeyboard(): Boolean {
    // 1. 系统配置：只有真的接了物理键盘，keyboard 才不是 KEYBOARD_NOKEYS。
    //    部分设备该字段为 UNDEFINED，所以只在明确"被隐藏"时否定。
    val config = resources.configuration
    if (config.keyboard != Configuration.KEYBOARD_NOKEYS &&
        config.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    ) {
        return true
    }
    // 2. 兜底扫描输入设备：个别蓝牙键盘不会触发 Configuration 重建。
    //    只认 KEYBOARD_TYPE_ALPHABETIC 的实体设备——手机内部的按键节点（音量键等）
    //    是 NON_ALPHABETIC，虚拟键盘的 keyboardType 为 NONE，都不会误判成外接键盘。
    return InputDevice.getDeviceIds().any { id ->
        val device = InputDevice.getDevice(id) ?: return@any false
        !device.isVirtual &&
            device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
            (device.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
    }
}

/**
 * 物理键盘是否接入的可组合版本：键盘插拔会触发 Configuration 变更，
 * 借此重新求值，无需自己注册广播接收器。
 */
@Composable
fun rememberHasHardwareKeyboard(): Boolean {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return remember(configuration, context) { context.hasHardwareKeyboard() }
}
