package com.lingji.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 长按选取文本时保持输入法收起，短按仍然正常弹出。
 *
 * 为什么需要它：value 版 `BasicTextField`（`CoreTextField`）没有 `KeyboardOptions.showKeyboardOnFocus`
 * 支持 —— 该开关只被新实现 `BasicTextField(state: TextFieldState)` 读取，value 版的
 * `PlatformTextInputService.startInput(...)` 签名里根本没有 `KeyboardOptions`（已核对
 * foundation 1.7.5 字节码）。长按进入选择模式时会先请求焦点，焦点一到就启动输入会话，
 * 输入法随之弹出：既盖住正在选的正文，又改变键盘 inset 触发宿主的「光标跟随」滚一屏。
 *
 * 做法：在 [PointerEventPass.Initial] 阶段**旁路观察**指针事件（不消费，不影响文本选择、
 * 光标落位与选择手柄拖拽），按手势类型分别处理：
 *  - 短按：下一帧主动 `show()`，保证「点一下还能打字」。字段原本没焦点时系统也会自动弹，
 *    这里主要覆盖「长按收过键盘后再次点击同字段」的情况（焦点没变，系统不会自动再弹）。
 *  - 长按且手势开始时字段**没有**焦点：下一帧 `hide()`，稍后再兜一次，
 *    压掉 `CoreTextField` 获焦时那次自动弹出。
 *  - 长按但字段本来就聚焦、键盘已开（打字中途选词）：不动，避免平白把键盘收走。
 *
 * 两个实现要点：
 *  - 焦点状态用 [rememberUpdatedState] 读取，不能把 `focused` 当 `pointerInput` 的 key：
 *    长按瞬间焦点从 false 变 true，handler 会被重启，正在进行的手势直接丢失。
 *  - `show()/hide()` 一律 `launch` 到页面级 scope 并等一帧再发：先让 `CoreTextField`
 *    处理完本次事件（请求焦点、启动输入会话），否则命令会先于它的自动弹出，白做。
 */
@Composable
fun Modifier.imeSelectionBehavior(
    enabled: Boolean = true,
    focused: Boolean
): Modifier {
    if (!enabled) return this

    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val focusedState = rememberUpdatedState(focused)
    val sendKeyboardCommand: (Boolean) -> Unit = remember(keyboard, scope) {
        { show ->
            scope.launch {
                withFrameNanos { }
                if (show) {
                    keyboard?.show()
                } else {
                    keyboard?.hide()
                    // 焦点刚落定时系统可能还在排 showSoftInput，再收一次以压掉闪烁。
                    delay(IME_HIDE_RETRY_MS)
                    keyboard?.hide()
                }
            }
        }
    }

    return remember(enabled, keyboard, scope) {
        Modifier.pointerInput(Unit) {
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var maxDistance = 0f
                var longPressed = false
                var upTime = down.uptimeMillis
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    maxDistance = max(maxDistance, (change.position - down.position).getDistance())
                    upTime = change.uptimeMillis
                    if (!longPressed && maxDistance <= touchSlop &&
                        change.uptimeMillis - down.uptimeMillis >= longPressTimeout
                    ) {
                        longPressed = true
                        if (!focusedState.value) sendKeyboardCommand(false)
                    }
                    if (!change.pressed) break
                }
                val isTap = !longPressed && maxDistance <= touchSlop &&
                    upTime - down.uptimeMillis < longPressTimeout
                if (isTap) sendKeyboardCommand(true)
            }
        }
    }
}

/** 收起键盘后的补刀间隔：覆盖系统把 showSoftInput 排到下一帧之后才执行的情况。 */
private const val IME_HIDE_RETRY_MS = 120L
