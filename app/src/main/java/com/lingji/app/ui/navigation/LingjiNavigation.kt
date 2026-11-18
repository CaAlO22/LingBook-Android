package com.lingji.app.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.RoundedCorner
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lingji.app.ui.components.AiRunningIsland
import com.lingji.app.ui.screens.FolderScreen
import com.lingji.app.ui.screens.FragmentSubjectScreen
import com.lingji.app.ui.viewmodel.UpdateViewModel
import com.lingji.app.ui.screens.NotebookSubjectScreen
import com.lingji.app.ui.screens.SettingsScreen
import com.lingji.app.ui.screens.SubjectGalleryScreen
import com.lingji.app.ui.screens.TodoSubjectScreen
import com.lingji.app.ui.viewmodel.SubjectViewModel

private const val NAV_TRANSITION_MS = 240
// 返回离场的目标缩放：缩到很小再淡出，让用户完整看到页面收成一张小卡片的过程
private const val POP_EXIT_SCALE = 0.08f
// 返回收缩时长
private const val POP_EXIT_SCALE_MS = 240
// 离场页淡出时长：卡片足够小，可以留出更长的淡出时间，避免页面被移除时的硬切
private const val POP_EXIT_FADE_MS = 160
// 进入页从点击处放大到原尺寸的起始比例：从一张很小的卡片长满屏幕
private const val ENTER_START_SCALE = 0.08f
// 进入页缩放时长：非线性放大（由快转慢），卡片越大速度越慢
private const val ENTER_SCALE_MS = 240
// 进入页淡入时长：起点足够小，淡入可以稍长一些，过程更连贯
private const val ENTER_FADE_MS = 160
// 预测式返回拖到底时，小卡片保留的不透明度
private const val PREDICTIVE_BACK_ALPHA = 0.6f
// 打开时背景缩小到的比例，关闭时背景从该比例放大回来
private const val BACKGROUND_SCALE = 0.8f
// 应用内返回键的退出终点：左上角返回键的位置（归一化坐标）
private val BACK_EXIT_ORIGIN = TransformOrigin(0.08f, 0.09f)
// 手势返回的退出终点：手指对侧的中点——右侧边缘滑入收向左中，左侧边缘滑入收向右中
private val LEFT_MIDDLE_ORIGIN = TransformOrigin(0f, 0.5f)
private val RIGHT_MIDDLE_ORIGIN = TransformOrigin(1f, 0.5f)
// 一次点击/按下在多长时间内视为触发了本次转场
private const val PRESS_ANCHOR_FRESH_MS = 1200L
// 信息岛：非线性速度——卡片越大速度越慢，最慢为线性速度的 0.5 倍。
// 二次曲线让速度在 1.5 倍与 0.5 倍线性速度之间平滑变化，平均仍为 1 倍，总时长不变。
// 放大：f(t) = 1.5t - 0.5t²，速度由 1.5 降到 0.5（卡片越大越慢）
private val GrowEasing = Easing { t -> 1.5f * t - 0.5f * t * t }
// 缩小：与放大对称，f(t) = 0.5t + 0.5t²，速度由 0.5 升到 1.5（卡片越大越慢）
private val ShrinkEasing = Easing { t -> 0.5f * t + 0.5f * t * t }

/**
 * 信息岛：转场锚点
 *
 * 记录最近一次按下的归一化坐标（0f..1f），用于：
 * - 进入动画以用户点击处为缩放起点；
 * - 区分返回来源：有新鲜按下记录视为应用内返回键，否则视为系统手势/按键返回。
 * 系统返回手势（含预测式返回）通常不向应用透传触摸事件，此时没有新鲜按下记录，按从右侧滑动处理。
 */
private object NavPressAnchor {
    @Volatile private var downAt = 0L
    @Volatile private var downX = 0.5f
    @Volatile private var downY = 0.5f

    fun record(x: Float, y: Float) {
        downX = x
        downY = y
        downAt = SystemClock.uptimeMillis()
    }

    /** 返回 [PRESS_ANCHOR_FRESH_MS] 内最近一次按下的归一化坐标；过期或从未按下时返回 null。 */
    fun recent(): Offset? {
        if (SystemClock.uptimeMillis() - downAt > PRESS_ANCHOR_FRESH_MS) return null
        return Offset(downX, downY)
    }

    /** 最近一次按下的时间戳，用于判断返回手势是否发生在这次按下之后。 */
    fun lastDownAt(): Long = downAt
}

/**
 * 信息岛：返回手势起始边
 *
 * NavHost 自带的预测式返回只消费 BackEvent 的进度，不读取起始边。
 * 因此在窗口的 OnBackInvokedDispatcher 上以 PRIORITY_OVERLAY 注册一个动画回调，
 * 先于 navigation-compose 收到 BackEvent：记录起始边后原样转发给 OnBackPressedDispatcher，
 * 既保留 NavHost 原有的预测动画与返回行为，又能让退出终点跟随手势方向。
 */
private object NavBackGesture {
    @Volatile private var startedAt = 0L
    @Volatile private var swipeEdge = BackEventCompat.EDGE_RIGHT

    fun record(edge: Int) {
        swipeEdge = edge
        startedAt = SystemClock.uptimeMillis()
    }

    fun clear() {
        startedAt = 0L
    }

    /**
     * 最近一次手势开始晚于 [after] 时返回其起始边，否则说明这次返回不是手势发起的。
     *
     * 不在手势提交（onBackInvoked）时清理：转场在返回栈变化后的重组中才创建，
     * 清理早于创建会让快速滑动丢掉起始边。记录靠 [after] 自然失效——
     * 应用内返回键按下会刷新按下时间，下一次按下之后的转场不会再命中旧手势。
     */
    fun edgeStartedAfter(after: Long): Int? = if (startedAt > after) swipeEdge else null
}

/** 从 Compose 的 Context 链上找到宿主 Activity。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 信息岛：在根容器以 Initial pass 观察按下位置，不消费事件、不影响子级点击。 */
private fun Modifier.trackPressAnchor(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Press) continue
            val change = event.changes.firstOrNull() ?: continue
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            if (width <= 0f || height <= 0f) continue
            NavPressAnchor.record(
                x = (change.position.x / width).coerceIn(0f, 1f),
                y = (change.position.y / height).coerceIn(0f, 1f)
            )
        }
    }
}

/**
 * 信息岛：页面圆角
 *
 * 读取设备屏幕的物理圆角半径（Android 12+）。页面按此半径裁剪后，
 * 转场缩放时圆角会跟着卡片一起等比缩小，小卡片看起来就是一块“迷你屏幕”。
 * 部分设备不暴露圆角信息或返回 0，此时不做裁剪。
 */
private fun deviceCornerRadiusDp(context: Context): Dp {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0.dp
    val display = context.display ?: return 0.dp
    return runCatching {
        val positions = intArrayOf(
            RoundedCorner.POSITION_TOP_LEFT,
            RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_LEFT,
            RoundedCorner.POSITION_BOTTOM_RIGHT
        )
        val radiusPx = positions.toList().mapNotNull { display.getRoundedCorner(it)?.radius }
            .filter { it > 0 }
            .maxOrNull() ?: return 0.dp
        (radiusPx / context.resources.displayMetrics.density).dp
    }.getOrDefault(0.dp)
}

/** 信息岛：按设备圆角裁剪页面，确保缩放后小卡片仍保持屏幕的圆角形状。 */
@Composable
private fun DeviceRoundedPage(radius: Dp, content: @Composable () -> Unit) {
    val modifier = if (radius > 0.dp) {
        Modifier.fillMaxSize().clip(RoundedCornerShape(radius))
    } else {
        Modifier.fillMaxSize()
    }
    Box(modifier = modifier) { content() }
}

@Composable
fun LingjiNavigation(updateViewModel: UpdateViewModel) {
    val navController = rememberNavController()
    val viewModel: SubjectViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    // 信息岛：是否手势导航——手势返回没有应用内按下记录，用于决定返回终点
    val context = LocalContext.current
    val hasGestureNav = remember(context) {
        runCatching {
            Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0)
        }.getOrDefault(0) == 2
    }
    // 信息岛：预测式返回只在 Android 14+ 由系统按手势进度逐帧 seek 转场
    val supportsPredictiveBack = Build.VERSION.SDK_INT >= 34
    // 信息岛：设备物理圆角，用于页面按比例缩小时保持屏幕形状
    val deviceCornerRadius = remember(context) { deviceCornerRadiusDp(context) }
    // 信息岛：拦截平台返回事件只为读取手势起始边，随后原样转发，不改变返回行为。
    // 仅在存在上一级时注册，避免影响根页面退出应用时的系统预测动画。
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    val currentEntry by navController.currentBackStackEntryAsState()
    val canPopBack = currentEntry != null && navController.previousBackStackEntry != null
    DisposableEffect(activity, canPopBack) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null && canPopBack) {
            val backPressedDispatcher = activity.onBackPressedDispatcher
            val invokedDispatcher = activity.onBackInvokedDispatcher
            val callback = object : OnBackAnimationCallback {
                override fun onBackStarted(backEvent: BackEvent) {
                    NavBackGesture.record(backEvent.swipeEdge)
                    backPressedDispatcher.dispatchOnBackStarted(BackEventCompat(backEvent))
                }

                override fun onBackProgressed(backEvent: BackEvent) {
                    backPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(backEvent))
                }

                override fun onBackCancelled() {
                    NavBackGesture.clear()
                    backPressedDispatcher.dispatchOnBackCancelled()
                }

                override fun onBackInvoked() {
                    // 不在这里清理起始边：popExitTransition 会在返回栈变化后的重组中读取它
                    backPressedDispatcher.onBackPressed()
                }
            }
            invokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback
            )
            onDispose { invokedDispatcher.unregisterOnBackInvokedCallback(callback) }
        } else {
            onDispose { }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "gallery",
            modifier = Modifier
                .fillMaxSize()
                .trackPressAnchor(),
            // 页面切换动效：进入页从用户点击处放大淡入；背景页原地整体缩小
            enterTransition = {
                // 信息岛：进入动画锚点——用户点哪里，页面就从哪里放大淡入
                val press = NavPressAnchor.recent()
                val origin = press ?: Offset(0.5f, 0.5f)
                scaleIn(
                    animationSpec = tween(ENTER_SCALE_MS, easing = GrowEasing),
                    initialScale = ENTER_START_SCALE,
                    transformOrigin = TransformOrigin(origin.x, origin.y)
                ) + fadeIn(tween(ENTER_FADE_MS, easing = GrowEasing))
            },
            exitTransition = {
                // 信息岛：打开动画的背景退场——上一级原地整体缩小（不下移、不横移），
                // 形成“被新页面从上方/前方压下去”的层级感。
                scaleOut(
                    animationSpec = tween(NAV_TRANSITION_MS, easing = ShrinkEasing),
                    targetScale = BACKGROUND_SCALE,
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                )
            },
            popEnterTransition = {
                // 信息岛：关闭动画的背景入场——上一级从缩小状态原地放大回原尺寸（上浮），
                // 与打开时背景缩小形成对称；预测式返回拖动时该过程随手势进度同步推进。
                // 与前景页的收缩同步，背景由快到慢放大，整个返回过程中都能看到“上浮”
                scaleIn(
                    animationSpec = tween(NAV_TRANSITION_MS, easing = GrowEasing),
                    initialScale = BACKGROUND_SCALE,
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                )
            },
            popExitTransition = {
                // 信息岛：返回终点按来源区分——
                // 系统手势返回收向手指对侧的中点：右侧滑入→左侧中间，左侧滑入→右侧中间；
                // 应用内返回键收向左上角返回键的位置，强化“从哪来、回哪去”的动线。
                val gestureEdge = NavBackGesture.edgeStartedAfter(NavPressAnchor.lastDownAt())
                val fromGesture = gestureEdge != null || (hasGestureNav && NavPressAnchor.recent() == null)
                val origin = when {
                    gestureEdge == BackEventCompat.EDGE_LEFT -> RIGHT_MIDDLE_ORIGIN
                    gestureEdge == BackEventCompat.EDGE_RIGHT -> LEFT_MIDDLE_ORIGIN
                    // 老系统拿不到起始边，按常见的右侧滑入处理
                    fromGesture -> LEFT_MIDDLE_ORIGIN
                    else -> BACK_EXIT_ORIGIN
                }
                val scale = scaleOut(
                    animationSpec = tween(POP_EXIT_SCALE_MS, easing = ShrinkEasing),
                    targetScale = POP_EXIT_SCALE,
                    transformOrigin = origin
                )
                // 真实手势返回才会收到 BackEvent，且只有 Android 14+ 会按进度逐帧 seek：
                // 拖到底的小卡片保持 60% 不透明度，其余返回仍完整淡出到 0。
                val predictive = supportsPredictiveBack && gestureEdge != null
                if (predictive) {
                    scale + fadeOut(
                        animationSpec = tween(POP_EXIT_SCALE_MS, easing = ShrinkEasing),
                        targetAlpha = PREDICTIVE_BACK_ALPHA
                    )
                } else {
                    scale + fadeOut(
                        tween(
                            durationMillis = POP_EXIT_FADE_MS,
                            delayMillis = POP_EXIT_SCALE_MS - POP_EXIT_FADE_MS,
                            easing = ShrinkEasing
                        )
                    )
                }
            }
        ) {
            composable("gallery") {
                DeviceRoundedPage(deviceCornerRadius) {
                    SubjectGalleryScreen(
                        viewModel = viewModel,
                        onSubjectClick = { id ->
                            viewModel.setCurrentSubject(id)
                            navController.navigate("subject/$id")
                        },
                        onFolderClick = { folderId ->
                            navController.navigate("folder/$folderId")
                        },
                        onOpenSettings = { navController.navigate("settings") }
                    )
                }
            }
            composable(
                "subject/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                val subjectId = backStackEntry.arguments?.getString("id") ?: return@composable
                val subject = viewModel.uiState.value.subjects.find { it.id == subjectId }
                    ?: return@composable
                DeviceRoundedPage(deviceCornerRadius) {
                    when (subject.type) {
                        com.lingji.app.domain.model.SubjectType.NOTEBOOK ->
                            NotebookSubjectScreen(
                                viewModel = viewModel,
                                subject = subject,
                                onBack = { navController.popBackStack() }
                            )
                        com.lingji.app.domain.model.SubjectType.TODO ->
                            TodoSubjectScreen(
                                viewModel = viewModel,
                                subject = subject,
                                onBack = { navController.popBackStack() }
                            )
                        else ->
                            FragmentSubjectScreen(
                                viewModel = viewModel,
                                subject = subject,
                                onBack = { navController.popBackStack() }
                            )
                    }
                }
            }
            composable(
                "folder/{folderId}",
                arguments = listOf(navArgument("folderId") { type = NavType.StringType })
            ) { backStackEntry ->
                val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
                DeviceRoundedPage(deviceCornerRadius) {
                    FolderScreen(
                        viewModel = viewModel,
                        folderId = folderId,
                        onSubjectClick = { id ->
                            viewModel.setCurrentSubject(id)
                            navController.navigate("subject/$id")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("settings") {
                DeviceRoundedPage(deviceCornerRadius) {
                    SettingsScreen(
                        viewModel = viewModel,
                        updateViewModel = updateViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        AiRunningIsland(
            visible = uiState.isProcessing,
            title = uiState.processingMessage ?: "AI 运行中…",
            lines = uiState.aiIslandLines,
            onStop = { viewModel.stopAiProcessing() },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
