// HorizontalPager 在 Compose 1.6 仍标记为 ExperimentalFoundationApi；
// 这里只在页签切换和预览翻页两处使用，行为稳定，文件级 opt-in 即可。
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.papermill.app.feature

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papermill.app.core.design.PaperColors
import com.papermill.app.core.design.PaperDimens
import com.papermill.app.core.store.SavedPaper
import com.papermill.app.core.update.UpdateChecker
import com.papermill.app.domain.content.ContentCatalog
import com.papermill.app.domain.content.CurriculumBank
import com.papermill.app.domain.content.TextbookCatalog
import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.CustomMode
import com.papermill.app.domain.generate.CustomSheet
import com.papermill.app.domain.content.ExamBank
import com.papermill.app.domain.generate.Density
import com.papermill.app.domain.generate.ExamFamily
import com.papermill.app.domain.generate.MathPoint
import com.papermill.app.domain.generate.GeneratorRegistry
import com.papermill.app.domain.generate.FooterSize
import com.papermill.app.domain.generate.GradeProfiles
import com.papermill.app.domain.generate.LineGap
import com.papermill.app.domain.generate.PageOrientation
import com.papermill.app.domain.generate.PaperKind
import com.papermill.app.domain.generate.PaperTemplates
import com.papermill.app.domain.generate.QuestionType
import com.papermill.app.domain.generate.Subject
import com.papermill.app.domain.generate.Term
import com.papermill.app.domain.generate.Tilt
import com.papermill.app.domain.profile.Gender
import com.papermill.app.domain.profile.Student
import com.papermill.app.domain.reference.PracticeData
import com.papermill.app.domain.reference.ReferenceData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 页面框架 v2。
 *
 * 与 v1 最大的区别：**主操作不再放在滚动内容的末尾**。
 * 结构是「顶栏 → 可滚动内容 → 固定操作条 → 固定底部导航」，
 * 用户随便滚到哪，出卷 / 打印永远在拇指够得到的位置。
 */
/**
 * 底部玻璃 dock 占位高度（外 padding 6 + 内 padding 5 + 胶囊 36 + 5 + 6 = 58dp）。
 *
 * dock 现在是浮层，不占布局位；内容区要按这个高度留出底部内边距，
 * 否则滚到最底时最后一行会被 dock 压住。
 *
 * v0.18.0 连同胶囊一起压矮：42dp 的胶囊里约 10dp 是纯留白，压到 36dp 后
 * 整条从 70dp 降到 58dp，一屏能多露一行内容 —— 这是用户要的「高度不用那么高」。
 */
private val DOCK_SPACE = 58.dp

/** 页内可滚动内容需要额外留出的底部空间：操作条 + dock + 呼吸余量 */
private val BOTTOM_SAFE = DOCK_SPACE + PaperDimens.ActionBarH + 16.dp

/** 底部有固定操作条的页面：提示条要浮在操作条之上，不能压住按钮 */
private val STICKY_BAR_SCREENS = setOf(
    Screen.HOME, Screen.CREATE, Screen.PREVIEW, Screen.LOCAL_PDF, Screen.MISTAKES,
)

/**
 * 页签切换的「淡出 → 换页 → 淡入」（Material 3 的 fade-through）—— v0.18.4。
 *
 * 用户原话：「点击 tab 栏的按钮的时候，页面不要一下子就出来，要有平滑的过渡效果」。
 * 改之前点 dock 是两种硬切：
 *  - 跨页（首页 → 更多）：直接 `scrollToPage`，一帧之内整页换掉，就是「刷一下就出来」；
 *  - 相邻页：`animateScrollToPage` 扫过 240ms，但那是**整页横移**，中途三页同时在场、
 *    几十张带阴影的卡片要现场量测+栅格化，反而更容易掉帧，看着像「闪一下」。
 *
 * 现在统一走 fade-through，三段各自有活干：
 *  1. 淡出 [outMs] 90ms —— 旧页真的「退场」，眼睛知道发生了什么；
 *  2. [swap] 在 **alpha = 0 的那一帧**执行 —— 新页就在这段全透明的时间里完成组合和
 *     首帧栅格化，硬切被彻底藏起来（这也是它能顶掉「扫过中间页」的原因）；
 *  3. 淡入 [inMs] 220ms，同时从下方 10dp 归位 —— 只淡不变位会显得「浮」，加一点位移
 *     就有了「新的一页升上来」的空间感。
 *
 * 手指横滑**不走这里**：那条路要跟手，pager 原生滑动才是对的。
 */
private suspend fun fadeThrough(
    alpha: Animatable<Float, AnimationVector1D>,
    rise: Animatable<Float, AnimationVector1D>,
    risePx: Float,
    outMs: Int = 90,
    inMs: Int = 220,
    swap: suspend () -> Unit,
) {
    alpha.animateTo(0f, tween(outMs, easing = FastOutLinearInEasing))
    // 换页动作必须落在 alpha = 0 上：漏在这一步之前，用户就会看见一次硬切
    swap()
    rise.snapTo(risePx)
    coroutineScope {
        launch { rise.animateTo(0f, tween(inMs, easing = FastOutSlowInEasing)) }
        launch { alpha.animateTo(1f, tween(inMs, easing = LinearOutSlowInEasing)) }
    }
}

@Composable
fun PaperMillRoot(state: AppState) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.importBackup(uri)
    }
    // 本地 PDF：用 OpenDocument 而不是 GetContent，拿到的 Uri 才能稳定读到流
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { state.openLocalPdf(uri) }
    }

    // 返回键：先 App 内逐层退（预览 → 来时的页面 → 首页），最后在首页再按一次才退出。
    // 旧版没接管返回键，在预览页按一下就直接回桌面，卷子和浏览位置全丢。
    // 具体处理在下面的 goBack 里（要等过渡用的 Animatable 建好；v0.18.4 起
    // 返回也走同一套淡入淡出，只给页签做、返回还是硬切，两套节奏看着就散）。

    // 提示条自动收起：切页后不再挂着上一条「已生成 N 题」的旧消息
    LaunchedEffect(state.toast) {
        if (state.toast.isNotEmpty()) {
            delay(6000)
            state.clearToast()
        }
    }

    // 页签集合由用户设置决定（v0.18.0）：可见的那几个才进 pager，
    // 顺序也就是 dock 的顺序，两处共用一个列表，不会再出现「点了跳到隔壁」。
    val tabs = state.dockTabs
    val isTabScreen = tabs.any { it.screen == state.screen }

    // 页签做成可左右滑动的 pager，滑动落页后同步 state。
    //
    // `key(tabs)` 很关键：页签集合一变（用户在「更多」里关掉一个），
    // 旧页码在新集合里指的可能是**另一页** —— 实测关掉「试卷库」后，
    // 页码 5 越界、pager 回弹到 4，回弹过程中 settledPage 短暂报出 3，
    // 顶栏标题就变成了「课本」，底下的内容还是「更多」。
    // 直接按新集合重建 pager，初始页取「当前页面在新集合里的位置」，
    // 页码与页面从一开始就是一致的，也就没有回弹这回事。
    val pager = key(tabs) {
        rememberPagerState(
            initialPage = tabs.indexOf(state.navTab).coerceAtLeast(0),
        ) { tabs.size }
    }

    // ============================================================
    // 页面切换的过渡（v0.18.4）
    // ============================================================
    // alpha 作用在「内容 + 顶栏 + 底部操作条」这一整块上，dock 不参与 ——
    // dock 是常驻导航，它跟着晃反而像整屏在闪。
    // 用 Animatable 而不是 animateFloatAsState：这里要**先淡出、换页、再淡入**，
    // 得能手动推进，声明式的 animateFloatAsState 排不出这个顺序。
    val contentAlpha = remember { Animatable(1f) }
    val contentRise = remember { Animatable(0f) }
    val risePx = with(LocalDensity.current) { 10.dp.toPx() }
    // 连点保护：上一段过渡还没走完又点了一个页签，先取消旧的再开新的，
    // 否则两个协程抢同一个 Animatable，先启动的那个会被取消在半路，
    // alpha 停在小数上 —— 整页半透明，像卡死了。
    var tabJob by remember { mutableStateOf<Job?>(null) }

    /**
     * 返回（返回键 / 顶栏圆形按钮共用一份）。
     *
     * 首页按返回仍是「再按一次退出」的老逻辑，而且**不淡出**：
     * 那是退出确认，画面闪一下再亮回来反而像误触。
     * 其余页面退回时走同一套 fade-through，跟点 dock 换页签一个节奏。
     */
    val goBack: () -> Unit = {
        if (state.screen == Screen.HOME) {
            if (state.exitArmed) {
                activity?.finish()
            } else {
                state.exitArmed = true
                state.toast = "再按一次返回即退出"
                scope.launch {
                    delay(2000)
                    state.exitArmed = false
                }
            }
        } else {
            // 正在切页签时又按返回：让新的这段接管（旧协程被取消，alpha 从当前值接着走）
            tabJob?.cancel()
            tabJob = scope.launch {
                fadeThrough(contentAlpha, contentRise, risePx) { state.back() }
            }
        }
    }
    BackHandler { goBack() }

    // ============================================================
    // 记住上次看到哪儿
    // ============================================================
    // 每个页签一个独立的 LazyListState：pager 会把离屏页销毁重建，
    // 用 remember 在页内建的话，滑回来就回到顶部了。
    // 按 NavTab.entries 全量建，而不是按当前可见的那几个 ——
    // 隐藏再显示时滚动位置要还在，键也不能因为可见集合变化而缺失。
    val tabStates = remember { NavTab.entries.associateWith { LazyListState() } }

    LaunchedEffect(Unit) {
        state.restoreSessionOnce()
        // 复原要等 restoreSessionOnce 读完盘，所以放在它后面滚；
        // 用 scrollToItem 而不是构造初始值，因为这份 session 是异步才到的
        state.restoredSession.scrolls.forEach { (key, pos) ->
            val tab = runCatching { NavTab.valueOf(key) }.getOrNull() ?: return@forEach
            tabStates[tab]?.let { st ->
                runCatching { st.scrollToItem(pos.getOrElse(0) { 0 }, pos.getOrElse(1) { 0 }) }
            }
        }
    }

    // 滚动即记录（只写内存）；停下来 1.2 秒再落一次盘。
    // 只靠 onStop 落盘不够：从最近任务里划掉、或者系统直接杀进程都不一定走
    // onStop，那时候位置就白记了。滚停后落一次，最多丢最后 1 秒的位置。
    LaunchedEffect(Unit) {
        snapshotFlow {
            // 用 Triple 而不是 intArrayOf：数组没有结构化 equals，
            // distinctUntilChanged 会认为「每帧都是新值」，滚动停止后还在空转；
            // Triple 有结构相等，静止时不再发射，也就不会反复重启那 1.2 秒的计时。
            NavTab.entries.map { k ->
                val st = tabStates.getValue(k)
                Triple(k.name, st.firstVisibleItemIndex, st.firstVisibleItemScrollOffset)
            }
        }
            .distinctUntilChanged()
            .collect { list ->
                list.forEach { (key, index, offset) -> state.noteScroll(key, index, offset) }
                delay(1200)
                state.persistSession()
            }
    }

    // 切后台 / 退出时把「我在哪」写盘
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.persistSession()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ============================================================
    // 高亮与 pager 的三段联动（用 snapshotFlow 而不是 LaunchedEffect(key)）
    // ============================================================
    // 踩过的坑：把 key 设成 pager.currentPage 时，带动画的跳页本身会让 currentPage
    // 中途变化 → 触发 goTab → screen 变 → 那个 effect 被重启 → 正在跑的
    // animateScrollToPage 被协程取消 → pager 卡在两页中间不动。
    // snapshotFlow 挂在 LaunchedEffect(Unit) 里，永不重启，也就不会互相取消。

    // ① 手指拖动时高亮立刻跟随（滑过半页就切），这就是用户要的「跟着一起动」。
    // key 用 pager：页签集合变化会重建 pager，旧实例上的监听必须跟着换掉
    //
    // ⚠️ ① ② 里都不许用外面那个 `isTabScreen`：本 effect 只在 pager 重建时重启，
    // 捕获的值会一直停在启动那一刻。从「参数页 / 打印帮助」冷启动进来时它是 false，
    // 之后横滑页签，① ② 会全部命中 false 的早退 —— 顶栏标题不跟着变、navTab 不更新，
    // 只有内容在滑。判断放在收集时做，拿的是当下这一帧的真实值。
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            val onTab = state.dockTabs.any { it.screen == state.screen }
            if (pager.isScrollInProgress && onTab) {
                val target = tabs.getOrNull(page) ?: return@collect
                if (state.navTab != target) state.goTab(target)
            }
        }
    }

    // ② 落页后校正一次：手指滑到位、或程序跳页落地时，把 navTab 对齐到真实页
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val onTab = state.dockTabs.any { it.screen == state.screen }
            if (onTab) {
                val target = tabs.getOrNull(page) ?: return@collect
                if (state.navTab != target) state.goTab(target)
            }
        }
    }

    // ③ 兜底对齐：navTab 变了就把 pager 挪到对应页（静默瞬切，不做动画）。
    // 点 dock 那条路已经在点击处自己做完过渡并挪好 pager（见 fadeThrough），
    // 真正会走到这里的是「子页面 → 页签」：那时 pager 这一帧才重新进入组合，
    // 点击处不硬来，交给这里对齐 —— 反正此时 alpha ≈ 0，看不见这次跳页。
    // 手指正在滑的时候不发指令，否则松手那刻 settle 还没走完就被抢走。
    LaunchedEffect(pager) {
        snapshotFlow { state.navTab }.collect { tab ->
            // 这里**不能**用外面捕获的 isTabScreen：本 effect 只在 pager 重建时重启，
            // 捕获值会一直停在启动那一刻。从「参数页」冷启动进来时它是 false，
            // 之后点 dock 会永远命中下面这个 return —— 顶栏标题变了、内容还是首页，
            // pager 一动不动，用户看到的就是「点了没反应」。
            if (pager.isScrollInProgress) return@collect
            if (tabs.none { it.screen == state.screen }) return@collect
            val idx = tabs.indexOf(tab)
            if (idx < 0 || pager.currentPage == idx) return@collect
            pager.scrollToPage(idx)
        }
    }

    // ④ 「更多」页的数字要随时反映最新状态：每次切回本页都重读一次。
    // 读盘在 IO 线程，不会卡帧；不能挂在 LazyColumn 的 item 里，那会被回收后反复重跑。
    LaunchedEffect(state.screen) {
        if (state.screen == Screen.MORE) state.refreshListsAsync()
    }

    val (title, subtitle) = screenTitle(state)
    // 页签页不显示返回箭头（现代 App 惯例），子页面才显示
    val onBack: (() -> Unit)? = if (isTabScreen) null else goBack

    Box(Modifier.fillMaxSize().background(PaperColors.Background)) {
        // 内容层一路铺到屏幕底部：滚动内容会从 dock 背后穿过去，玻璃质感才透得出来
        Column(Modifier.fillMaxSize()) {
            // 顶栏跟着一起过渡，但**不淡到全透明**：留 15% 让标题始终在那儿，
            // 只压低存在感。全灭会在屏幕上开一个洞，反而显得突兀。
            Box(
                Modifier.graphicsLayer {
                    alpha = 0.15f + 0.85f * contentAlpha.value
                }
            ) {
                AppTopBar(
                    title = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    // 只有首页（无返回键）标题居中 + 主色；有返回键的页面居中会和圆形按钮撞
                    centered = state.screen == Screen.HOME,
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    // 逐帧读 alpha / 位移（lambda 版 graphicsLayer 只重跑绘制，不重组内容）：
                    // 36 张卡的列表在重组一次就是几毫秒，绝不能挂在动画帧上
                    .graphicsLayer {
                        alpha = contentAlpha.value
                        translationY = contentRise.value
                    }
            ) {
                if (isTabScreen) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        // 预先组合相邻页：默认只组合当前页，滑到新页时才现场搭整页内容
                        // （工具页有 36 张卡），首帧就会卡一下；多挂一页换顺滑。
                        // Compose 1.6 里这个参数叫 beyondBoundsPageCount（1.7 才改名）。
                        beyondBoundsPageCount = 1,
                    ) { page ->
                        // 页签可以在「更多」里随时增删，pager 的页数与 tabs 长度不一定
                        // 同一帧对齐（少一个时 pageCount 先变、当前页后夹），
                        // 这里取不到就直接不排 —— 宁可空一帧，也不要 ArrayIndexOutOfBounds
                        val tab = tabs.getOrNull(page) ?: return@HorizontalPager
                        // 用 LazyColumn 而不是 Column + verticalScroll：
                        // 原来「打印纸」页一次性组合 36 张带 elevation 阴影的卡，
                        // 滑动时前后两页同时在场，首帧要摆上百个阴影节点 —— 这就是卡顿的主因。
                        // 改成按需组合后，屏幕上始终只有可见的那几行。
                        LazyColumn(
                            state = tabStates.getValue(tab),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = PaperDimens.ScreenPad,
                                end = PaperDimens.ScreenPad,
                                top = 2.dp,
                                // 让出底部浮层：否则滚到底时最后一行被 dock 压住
                                bottom = BOTTOM_SAFE,
                            ),
                        ) {
                            when (tab) {
                                NavTab.HOME -> homeScreenItems(state, scope)
                                NavTab.REFERENCE -> referenceScreenItems(state, scope)
                                NavTab.MISTAKES -> mistakesScreenItems(state)
                                NavTab.HISTORY -> historyScreenItems(
                                    state, scope,
                                    onImport = { importLauncher.launch("*/*") },
                                )
                                // 课本页本身就是「一串卡片」，内部没有嵌套滚动，整块塞进一个
                                // item 是安全的；它的单元列表最多几十行，一次性组合也无所谓。
                                NavTab.TEXTBOOK -> item { TextbookScreen(state, scope) }
                                NavTab.MORE -> moreScreenItems(
                                    state, scope,
                                    onImport = { importLauncher.launch("*/*") },
                                    onPickPdf = { pdfLauncher.launch(arrayOf("application/pdf")) },
                                )
                            }
                            item { Spacer(Modifier.height(20.dp)) }
                        }
                    }
                } else {
                    val scroll = rememberScrollState()
                    LaunchedEffect(state.screen) {
                        // 只有「从盘里复原到参数页」这一次回到原位，其余一律从顶部开始
                        val restore = if (state.screen == Screen.CREATE) state.takeCreateScrollRestore() else null
                        scroll.scrollTo(restore ?: 0)
                    }
                    LaunchedEffect(Unit) {
                        snapshotFlow { scroll.value }.collect {
                            if (state.screen == Screen.CREATE) state.noteCreateScroll(it)
                        }
                    }
                    // 子页面入场也淡一下（v0.18.4）：点「自定义」「打印帮助」一样是整页换，
                    // 页签平滑了、这里还是硬切，两套节奏看着就散。
                    // 从 35% 淡上来而不是 0 —— 0 会露出白底，像加载失败；
                    // 起始值就是**首帧**的值（不是动画起点），所以不会有「先满后空」的闪。
                    var entered by remember(state.screen) { mutableStateOf(false) }
                    val enterAlpha by animateFloatAsState(
                        targetValue = if (entered) 1f else 0.35f,
                        animationSpec = tween(200, easing = LinearOutSlowInEasing),
                        label = "subPageEnter",
                    )
                    LaunchedEffect(state.screen) { entered = true }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = enterAlpha }
                            .verticalScroll(scroll)
                            .padding(horizontal = PaperDimens.ScreenPad)
                    ) {
                        Spacer(Modifier.height(2.dp))
                        when (state.screen) {
                            Screen.CREATE -> CreateScreen(state, scope)
                            Screen.PREVIEW -> PreviewScreen(state, scope, activity)
                            Screen.HELP -> HelpScreen(state)
                            Screen.STUDENTS -> StudentsScreen(state, scope)
                            Screen.LOCAL_PDF -> LocalPdfScreen(state) {
                                pdfLauncher.launch(arrayOf("application/pdf"))
                            }
                            else -> Unit
                        }
                        Spacer(Modifier.height(BOTTOM_SAFE))
                    }
                }
            }
        }

        // 浮层：操作条固定在 dock 上方，dock 本身贴底。
        // 提示条**不在**这个 Column 里（见下面）：它一出现就把按钮整体顶上去，是「抖一下」的来源。
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            // 操作条也跟着过渡：首页有「自定义 / 直接出卷」，切到页签页整条会消失，
            // 不跟着淡掉就是一次硬切 —— 上下两头同时跳，过渡就白做了。
            // 只给这一条 Column 上 alpha，提示条是它的兄弟节点，不受影响。
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = contentAlpha.value }
            ) {
            when (state.screen) {
                Screen.HOME -> StickyBar {
                    SecondaryButton(
                        "自定义",
                        modifier = Modifier.weight(1f),
                    ) { state.screen = Screen.CREATE }
                    PrimaryButton(
                        "直接出卷",
                        enabled = !state.busy && state.totalQuestions > 0,
                        modifier = Modifier.weight(1.25f),
                    ) { scope.launch { state.generate() } }
                }
                Screen.CREATE -> StickyBar {
                    PrimaryButton(
                        "生成试卷并预览",
                        enabled = !state.busy && state.totalQuestions > 0,
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { state.generate() } }
                }
                Screen.PREVIEW -> StickyBar {
                    PrimaryButton(
                        "系统打印",
                        enabled = !state.busy,
                        modifier = Modifier.weight(1.3f),
                    ) {
                        if (activity != null) scope.launch { state.print(activity) }
                        else state.toast = "拿不到 Activity，无法调起打印"
                    }
                    SecondaryButton("保存", modifier = Modifier.weight(1f)) { state.savePdf() }
                    SecondaryButton("分享", modifier = Modifier.weight(1f)) { state.sharePdf() }
                }
                Screen.LOCAL_PDF -> StickyBar {
                    PrimaryButton(
                        "系统打印",
                        enabled = !state.busy && (state.localPdf?.pages ?: 0) > 0,
                        modifier = Modifier.weight(1.2f),
                    ) {
                        if (activity != null) scope.launch { state.printLocalPdf(activity) }
                        else state.toast = "拿不到 Activity，无法调起打印"
                    }
                    SecondaryButton("换一个文件", modifier = Modifier.weight(1f)) {
                        pdfLauncher.launch(arrayOf("application/pdf"))
                    }
                }
                Screen.MISTAKES -> {
                    val pending = state.mistakes.count { it.mastery != "MASTERED" }
                    StickyBar {
                        PrimaryButton(
                            "生成错题重练卷（$pending 题）",
                            enabled = !state.busy && pending > 0,
                            modifier = Modifier.weight(1f),
                        ) { scope.launch { state.generateMistakePaper() } }
                    }
                }
                else -> Unit
            }
            Spacer(Modifier.height(DOCK_SPACE))
            }

            // 提示条浮在操作条上方，不参与排版：
            // 原来它和操作条同在一条 Column 里，弹一条「已生成 32 题」会把
            // 底部的两个按钮整体往上顶 60dp，6 秒后再落回来 —— 就是用户说的
            // 「点某个会滑动，还会自己抖动一下」。
            val barH = if (state.screen in STICKY_BAR_SCREENS) PaperDimens.ActionBarH else 0.dp
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = DOCK_SPACE + barH + 4.dp),
            ) {
                ToastBar(state.toast) { state.clearToast() }
            }
        }

        FloatingDock(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter),
            // 连续页位置：拖动时这里是 0.37 这种小数，不是整数页码。
            // dock 拿它做平移，胶囊才会跟着手指一起走。
            // 用 lambda 而不是直接求值：读状态的动作被推迟到 dock 内部，
            // 逐帧变化只会让 dock 自己失效，不会把整个页面（含 pager 内容）重组一遍。
            position = {
                pager.currentPage + pager.currentPageOffsetFraction
            },
        ) { tab ->
            // 点的是当前页签：什么都不做。连淡出都不能有 —— 否则原地闪一下，
            // 用户会以为「点了出错了」。
            if (isTabScreen && tab == state.navTab) return@FloatingDock
            // 连点：取消上一段（新的这段从当前 alpha 接着走，视觉上是连续的）
            tabJob?.cancel()
            tabJob = scope.launch {
                fadeThrough(contentAlpha, contentRise, risePx) {
                    // 换页本身：state 先变（顶栏标题、操作条、内容一起换），
                    // 再把 pager 挪到对应页。这一步在 alpha = 0 里执行，看不见。
                    state.goTab(tab)
                    // 子页面 → 页签时 pager 这一帧才重新进入组合，直接 scrollToPage
                    // 有可能撞上「还没测量」，交给 ③ 的兜底对齐更稳。
                    if (isTabScreen) {
                        val idx = tabs.indexOf(tab)
                        if (idx >= 0 && pager.currentPage != idx) pager.scrollToPage(idx)
                    }
                }
            }
        }

        // 批改面板压在整棵界面上（v0.18.0）：预览页和试卷库都要能开它，
        // 所以放在根节点只挂一份，不在两个页面各写一遍
        state.grading?.let { session ->
            GradingDialog(
                session = session,
                onDismiss = { state.grading = null },
                onConfirm = { state.finishGrading(it) },
            )
        }

        // 加题选题型的面板（v0.18.2）。和批改面板一样挂在根节点 ——
        // 触发按钮在预览页的 LazyColumn 里，挂在那儿会随 item 滚出屏幕而销毁
        if (state.addingQuestion) {
            AddQuestionDialog(
                types = state.addableTypes(),
                existing = state.paper?.sections
                    ?.associate { it.type to it.questions.size }
                    .orEmpty(),
                busy = state.busy,
                onDismiss = { state.addingQuestion = false },
                onPick = { type ->
                    state.addingQuestion = false
                    scope.launch { state.appendQuestion(type) }
                },
            )
        }
    }
}

/**
 * 「批改记错题」面板（v0.18.0）。
 *
 * 用户的原话是「孩子做完了哪些是错题如何快速记录下来」——
 * 旧流程是「进预览页 → 展开题目清单 → 一题一题勾 checkbox → 点加入错题本」，
 * 一张 20 题的卷子要点二十多下，还都是小方块，家长一边看纸一边戳屏幕很难对准。
 *
 * 这里给两条路，按题量自己选：
 *  - 题少（十来道）：直接点数字格子，一格一题，手指粗也不容易点错；
 *  - 题多：在输入框里敲「3 7 12-15」，照着自己用笔在卷子上标的号念一遍就行。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradingDialog(
    session: GradingSession,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
) {
    // 面板重开时清空：上一张卷勾的号不能带到这张卷上
    var picked by remember(session.paperId, session.title) { mutableStateOf(emptySet<Int>()) }
    var typed by remember(session.paperId, session.title) { mutableStateOf("") }
    var hint by remember(session.paperId, session.title) { mutableStateOf("") }
    val valid = remember(session) { session.items.map { it.no }.toSet() }

    val toggle: (Int) -> Unit = { no ->
        picked = if (no in picked) picked - no else picked + no
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PaperColors.Surface)
                .padding(18.dp),
        ) {
            Text("批改记错题", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PaperColors.OnSurface)
            Spacer(Modifier.height(3.dp))
            Text(
                "${session.title} · ${session.items.size} 题",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // 题号网格：一屏放得下就直接点，放不下就自己滚
            Box(Modifier.fillMaxWidth().heightIn(max = 268.dp).verticalScroll(rememberScrollState())) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    session.items.forEach { item ->
                        val on = item.no in picked
                        Box(
                            Modifier
                                .width(42.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (on) PaperColors.ErrorContainer else PaperColors.SurfaceAlt
                                )
                                .clickable { toggle(item.no) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${item.no}",
                                fontSize = PaperDimens.LabelSp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) PaperColors.Error else PaperColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Field3(
                value = typed,
                onValueChange = { typed = it; hint = "" },
                placeholder = "也可以直接敲题号：3 7 12-15",
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hint.ifEmpty { if (typed.isBlank()) "" else "点右边套用，会累加到上面已点的题号上" },
                    fontSize = PaperDimens.TinySp,
                    color = if (hint.isEmpty()) PaperColors.Muted else PaperColors.Error,
                    modifier = Modifier.weight(1f),
                    lineHeight = 15.sp,
                )
                MiniAction("套用题号") {
                    val parsed = parseNumberList(typed, valid)
                    if (parsed.isEmpty()) {
                        hint = "没读出题号，写成 3 7 12-15 这样试试"
                    } else {
                        picked = picked + parsed
                        typed = ""
                        hint = ""
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SecondaryButton("取消", modifier = Modifier.weight(1f)) { onDismiss() }
                PrimaryButton(
                    if (picked.isEmpty()) "加入错题本" else "加入错题本（${picked.size} 题）",
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.weight(1.3f),
                ) { onConfirm(picked) }
            }
            if (session.items.any { it.no > 0 }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "只会存题目和答案，不会拍照、不会上传；同一个题号重复记只算「又错一次」。",
                    fontSize = PaperDimens.TinySp,
                    color = PaperColors.Muted,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

/**
 * 「加一题」选题型面板（v0.18.2）。
 *
 * 用户的原话是「生成的卷子可以自定义删减添加」—— 删和换原来就有，
 * 「加」却只能加最后一题那种题型。想给数学卷补两道应用题、而卷子末尾是口算，
 * 就只能回参数页重来。
 *
 * 这里只列当前年级 + 科目**真能出题**的题型（跟参数页同一套判断），
 * 并且标出「卷里已有 N 题」：加进已有的大题还是新起一道，用户点之前就该知道。
 */
@Composable
private fun AddQuestionDialog(
    types: List<QuestionType>,
    existing: Map<QuestionType, Int>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPick: (QuestionType) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PaperColors.Surface)
                .padding(18.dp),
        ) {
            Text(
                "加一题",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = PaperColors.OnSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "选一种题型。卷里已有这道大题就并进去，没有就新起一道排在最后。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))

            if (types.isEmpty()) {
                Text(
                    "当前年级和科目下没有可加的题型，回参数页换个年级试试。",
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 330.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.forEach { type ->
                            val had = existing[type] ?: 0
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PaperColors.SurfaceAlt)
                                    .clickable(enabled = !busy) { onPick(type) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        type.label,
                                        fontSize = PaperDimens.BodySp,
                                        fontWeight = FontWeight.Medium,
                                        color = PaperColors.OnSurface,
                                    )
                                    Text(
                                        when {
                                            had > 0 -> "卷里已有 $had 题 · 再加 1 题"
                                            type.answerLines > 0 -> "需书写过程 · 新起一道大题"
                                            else -> "新起一道大题"
                                        },
                                        fontSize = PaperDimens.TinySp,
                                        color = PaperColors.OnSurfaceVariant,
                                    )
                                }
                                Text(
                                    "＋1",
                                    fontSize = PaperDimens.LabelSp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PaperColors.Primary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SecondaryButton("取消", modifier = Modifier.fillMaxWidth()) { onDismiss() }
        }
    }
}

/**
 * 把「3 7 12-15」解析成题号集合。
 *
 * 分隔符任由用户敲：空格、逗号、顿号、中文逗号都认 ——
 * 家长多半是照着自己卷子上用笔标的号念，标点是什么随手就来。
 * 区间只认横杠（半角、全角、波浪号都收），越界的号直接丢掉而不是报错：
 * 敲错一个号不该让整次输入白费。
 *
 * v0.18.3 起是 internal：「批量删题」也用这套口径。两处各写一份，
 * 迟早出现「判分认 12-15、删题不认」。
 */
internal fun parseNumberList(raw: String, valid: Set<Int>): Set<Int> {
    val out = mutableSetOf<Int>()
    raw.split(' ', ',', '，', '、', ';', '；', '\n', '\t').filter { it.isNotBlank() }.forEach { token ->
        val m = Regex("^(\\d+)\\s*[-~～－—]\\s*(\\d+)$").find(token.trim())
        if (m != null) {
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null) {
                for (n in minOf(a, b)..maxOf(a, b)) if (n in valid) out += n
            }
        } else {
            token.trim().toIntOrNull()?.let { if (it in valid) out += it }
        }
    }
    return out
}

private fun screenTitle(state: AppState): Pair<String, String> = when (state.screen) {
    Screen.HOME -> "卷子" to "选年级科目，30 秒出一张能打印的卷子"
    Screen.CREATE -> "自由组合" to
        "${GradeProfiles.gradeLabel(state.grade)}" +
        (if (state.term == Term.BOTH) "" else Term.label(state.term)) +
        " · ${state.totalQuestions} 题 · 预计 ${state.estimatedPages} 页 ${state.orientation.label}"
    Screen.PREVIEW -> if (state.isReference) {
        // 速查表可能排到 2 页，副标题必须读真实页数（原来写死「1 页」和卡片数字打架）
        "打印预览" to "${state.pdf?.pageCount ?: 0} 页 A4 · 与打印结果完全一致"
    } else {
        "试卷预览" to "共 ${state.paper?.questions?.size ?: 0} 题 · ${state.pdf?.pageCount ?: 0} 页 ${state.orientation.label}"
    }
    Screen.REFERENCE -> "速查表与打印纸" to
        "${ReferenceData.sheets.size} 张速查表 · ${PracticeData.sheets.size} 张练习纸 · ${PracticeData.themeSheets.size} 张主题识字纸"
    Screen.MISTAKES -> "错题本" to "数据只存在这台手机"
    Screen.MORE -> "更多" to "数据 / 打印 / 更新 / 关于"
    Screen.TEXTBOOK -> "课本" to "按单元出卷 · 教材原文在线阅读"
    Screen.HISTORY -> "试卷库" to "最近的卷子 · 可重新打开或改参数再出一张"
    Screen.HELP -> "打印帮助" to "在家打印与远程打印两种方式"
    Screen.LOCAL_PDF -> "打印本地 PDF" to (state.localPdf?.let { "${it.pages} 页 · ${it.name}" } ?: "选一个 PDF")
    Screen.STUDENTS -> "学生档案" to "点名字 = 年级 + 学期 + 科目一次就位，直接出卷"
}

/**
 * Hero 卡里的三个数据小块：题量 / 页数 / 是否自动补满。
 *
 * 三块等宽铺满整行（调用方传 weight），文字水平居中 —— 原来宽度跟着字走，
 * 三块宽窄不一、还都贴在左边，红框那一条右边空一大截。
 */
@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.82f))
    }
}

@Composable
private fun Divider() = Box(
    Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(PaperColors.Outline)
)

// ------------------------------------------------------------------ 首页：一屏完成选择 + 出卷

private fun LazyListScope.homeScreenItems(state: AppState, scope: CoroutineScope) {
    val presets = PaperTemplates.forSelection(state.grade, state.subjects).take(8)
    val subjectLabel = state.subjects.joinToString(" + ") { it.label }

    // 给谁出卷（v0.19.0）：比「多少题几页纸」更靠前的问题
    item {
        StudentStrip(state, scope)
        Spacer(Modifier.height(12.dp))
    }

    // Hero：一屏说清「给谁出卷、多少题、几张纸」，渐变是整页的视觉锚点
    item {
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(PaperColors.GradientStart, PaperColors.GradientEnd)
                    )
                )
                .padding(20.dp),
        ) {
            Column {
                Text(
                    "${GradeProfiles.gradeLabel(state.grade)}" +
                        (if (state.term == Term.BOTH) "" else Term.label(state.term)) +
                        " · $subjectLabel",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "共 ${state.totalQuestions} 题 · 预计 ${state.estimatedPages} 页 A4 · ${state.answerMode.label}",
                    fontSize = PaperDimens.CaptionSp,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroStat("${state.totalQuestions}", "道题", Modifier.weight(1f))
                    HeroStat("${state.estimatedPages}", "页 A4", Modifier.weight(1f))
                    HeroStat(if (state.autoFillPage) "开" else "关", "自动补满", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable { scope.launch { state.generateDaily() } }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "每日一练 · 今天这套，明天自动换新",
                        fontSize = PaperDimens.LabelSp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Text("›", fontSize = 20.sp, color = Color.White)
                }
            }
        }
    }

    // 这里原来有一张「自由组合出题」特色卡片，和底栏的「自定义」指向同一个页面
    // （都只是 state.screen = Screen.CREATE）。两个入口上下相隔不到一屏，属于纯重复：
    // 删掉卡片，把入口统一交给常驻的底栏按钮 —— 它是动作区，滚动到哪里都能点到；
    // 卡片只是把它又写了一遍，还白占了首屏纵向空间，把「年级 / 科目」挤到下一屏。
    // 「49 种题型随便挑」这句话没丢，挪到了参数页 03 题型库的标题里。

    item {
        Spacer(Modifier.height(12.dp))
        SectionLabel("01", "年级", "小学 1–6 / 初中 7–9")
        Spacer(Modifier.height(8.dp))
        CompactChipGrid(
            items = (1..9).toList(),
            perRow = 6,
            label = { GradeProfiles.gradeLabel(it) },
            selected = { it == state.grade },
            onClick = { state.selectGrade(it) },
        )
    }

    item {
        Spacer(Modifier.height(10.dp))
        // 学期（v0.19.0）：课标题库的知识点分上下册，选错了抽出来的就是还没学的单元。
        // 只影响真带学期数据的资源，所以右边把边界写清楚，不做「点了没反应」的假开关。
        SectionLabel("01b", "这学期", "影响课标题库的单元")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("上册", state.term == Term.FIRST) { state.selectTerm(Term.FIRST) }
            ChoiceChip("下册", state.term == Term.SECOND) { state.selectTerm(Term.SECOND) }
            ChoiceChip("全学年", state.term == Term.BOTH) { state.selectTerm(Term.BOTH) }
        }
    }

    item {
        Spacer(Modifier.height(12.dp))
        SectionLabel("02", "科目", "可多选 · 灰色未开课")
        Spacer(Modifier.height(8.dp))
        CompactChipGrid(
            items = Subject.values().toList(),
            perRow = 5,
            label = { it.label },
            selected = { it in state.subjects },
            enabled = { it.availableFor(state.grade) },
            onClick = { state.toggleSubject(it) },
        )
    }

    item {
        Spacer(Modifier.height(12.dp))
        SectionLabel("03", "选个卷子", "点一下直接出卷")
        Spacer(Modifier.height(8.dp))
    }
    // 每行两张卡各自一个 item：8 张卷子不再一次性全组合
    presets.chunked(2).forEach { row ->
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = PaperDimens.GapSmall),
                horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
            ) {
                row.forEach { preset ->
                    PresetCard(
                        title = preset.label,
                        desc = preset.desc,
                        meta = "${preset.wanted.values.sum()} 题 · 出卷",
                        onClick = {
                            state.useTemplate(preset.wanted)
                            scope.launch { state.generate() }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    // 专项卷：不依赖当前勾选的科目，点一下自动切科目并直接出卷。
    // 低年级没开英语，按年级隐藏对应卡片。
    item {
        Spacer(Modifier.height(12.dp))
        SectionLabel("04", "专项卷", "英语 · 音标语法 · 课本同步 · 主题识字")
        Spacer(Modifier.height(8.dp))
    }

    val specials = ArrayList<Triple<String, String, String>>(4)
    if (state.grade >= 3) {
        specials.add(
            Triple(
                "英语趣味练",
                "单词拼装 + 连词成句 + 选词填空，句子自编",
                "${PaperTemplates.englishFunCounts.values.sum()} 题 · 直接出卷",
            )
        )
        specials.add(
            Triple(
                "音标语法卷",
                "音标辨认 + 看音标写单词 + 语法选择",
                "${PaperTemplates.ipaGrammarCounts.values.sum()} 题 · 直接出卷",
            )
        )
    }
    specials.add(
        Triple(
            "课本同步卷",
            "课本生字注音 + 核心单词默写，按年级抽",
            "${PaperTemplates.textbookCounts.values.sum()} 题 · 直接出卷",
        )
    )
    if (state.grade <= 6) {
        specials.add(
            Triple(
                "主题识字卷",
                "超市 / 学校 / 交通…看拼音写词 + 归类",
                "${PaperTemplates.themeLiteracyCounts.values.sum()} 题 · 直接出卷",
            )
        )
    }

    specials.chunked(2).forEach { row ->
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = PaperDimens.GapSmall),
                horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
            ) {
                row.forEach { (title, desc, metaText) ->
                    PresetCard(
                        title = title,
                        desc = desc,
                        meta = metaText,
                        onClick = {
                            scope.launch {
                                when (title) {
                                    "英语趣味练" -> state.generateForSubject(
                                        Subject.ENGLISH, PaperTemplates.englishFunCounts,
                                    )
                                    "音标语法卷" -> state.generateForSubject(
                                        Subject.ENGLISH, PaperTemplates.ipaGrammarCounts,
                                    )
                                    "课本同步卷" -> state.generateForSubjects(
                                        listOf(Subject.CHINESE, Subject.ENGLISH),
                                        PaperTemplates.textbookCounts,
                                    )
                                    else -> state.generateForSubject(
                                        Subject.CHINESE, PaperTemplates.themeLiteracyCounts,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    item {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${PracticeData.themeSheets.size} 张主题识字纸：识字卡描红 / 默写格，点开即打印",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            MiniAction("去看看") { state.goTab(NavTab.REFERENCE) }
        }
    }

    if (state.savedPapers.isNotEmpty()) {
        item {
            Spacer(Modifier.height(PaperDimens.Gap))
            Spacer(Modifier.height(PaperDimens.Gap))
            CardSection(title = "最近的卷子") {
                state.savedPapers.take(2).forEachIndexed { i, p ->
                    if (i > 0) Divider()
                    ListRow(
                        p.title,
                        "${GradeProfiles.gradeLabel(p.grade)} · ${p.questions.size} 题",
                        trailing = "重开 ›",
                    ) { scope.launch { state.reopen(p) } }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 参数页：默认只露题量

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateScreen(state: AppState, scope: CoroutineScope) {
    val signature = buildString {
        append(state.grade).append('|')
        append(state.subjects.joinToString(",") { it.name }).append('|')
        append(state.counts.entries.joinToString(",") { "${it.key.name}:${it.value}" }).append('|')
        append(state.difficulty).append('|').append(state.columns).append('|')
        append(state.answerMode.name).append('|').append(state.showNameRow).append('|')
        append(state.orientation.name).append('|')
        // 自动补满开关也算参数：切换后要立刻重排，让用户当场看到「页面真的满了」
        append(state.autoFillPage).append('|')
        // v0.17.0 的卷面定制也必须进签名：不然改了行距 / 页脚 / 标题排版，
        // 实时预览还是上一张图，用户会以为「这个开关坏了」
        append(state.headerOneLine).append('|').append(state.showCreatedAt).append('|')
        append(state.footerMode).append('|').append(state.footerCustom).append('|')
        append(state.lineGap.name).append('|')
        // v0.18.1 的三个新字段也必须进签名，否则调了页码 / 页脚高度 / 自定义行距，
        // 上面的缩略图还是上一张
        append(state.lineGapCustom).append('|')
        append(state.showPageNo).append('|').append(state.footerSize.name).append('|')
        // v0.18.3：页数上限会改变正文压缩比，缩略图必须跟着重排
        append(state.targetPages).append('|')
        // v0.22.0：页边距与正文字号直接改纸张几何，缩略图必须重排
        append(state.margin).append('|').append(state.bodySize).append('|')
        // v0.23.0：知识点专项整块换掉出题来源，签名不进就会「勾了没反应」
        append(state.points.joinToString(",") { it.name }).append('|')
        append(state.pointCount).append('|')
        // v0.19.0：学期 / 自编题 / 卷头姓名都会改变纸上内容，缩略图必须跟着重排
        append(state.term).append('|').append(state.activeStudentId).append('|')
        append(state.visibleCustomSheets.joinToString(";") {
            "${it.id}:${it.mode.name}:${it.raw.hashCode()}:${it.columns}:${it.answerLines}:" +
                "${it.shuffle}:${it.count}:${it.points}:${it.title}"
        }).append('|')
        append(state.titleOverride).append('|').append(state.previewSeed)
    }
    LaunchedEffect(signature) {
        delay(350)
        state.refreshLivePreview()
    }

    CardSection(title = "实时预览", hint = "改参数自动刷新 · 与打印结果一致") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bmp = state.livePreview
            val landscape = state.orientation == PageOrientation.LANDSCAPE
            Box(
                Modifier
                    .width(if (landscape) 136.dp else 96.dp)
                    .height(if (landscape) 96.dp else 136.dp)
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(1.dp, PaperColors.Outline, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "实时预览首页",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("试排中…", fontSize = PaperDimens.CaptionSp, color = PaperColors.Muted)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "最后一页正文 ${state.liveFillPercent}%",
                    fontSize = PaperDimens.BodySp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaperColors.OnSurface,
                )
                Spacer(Modifier.height(6.dp))
                val fill = state.liveFillPercent
                val autoExtra = state.liveAutoExtra
                when {
                    // 补满真的生效：直接说清楚补了几题、现在几页
                    autoExtra > 0 && fill >= 84 -> Text(
                        "自动补满加了 $autoExtra 题 · 共 ${state.livePages} 页，最后一张纸已经排满。",
                        fontSize = PaperDimens.CaptionSp,
                        color = PaperColors.Success,
                        lineHeight = 16.sp,
                    )
                    // 开了补满还是不满：可能是题库见底，也可能只是「再加一组就顶到下一页」，
                    // 这里无法区分（实时预览只试排几轮），所以不要断言「题库不够」
                    autoExtra > 0 -> Text(
                        "自动补满加了 $autoExtra 题，最后一页到 $fill% —— 想更满可以再加几题或换题型。",
                        fontSize = PaperDimens.CaptionSp,
                        color = PaperColors.Accent,
                        lineHeight = 16.sp,
                    )
                    // 还没试排完（fill 仍是 0）：别急着报「已经排满」——
                    // 用户会看到「最后一页正文 0%」和「已经排满」两句自相矛盾的话
                    fill <= 0 -> Text(
                        "正在试排，稍等一下…",
                        fontSize = PaperDimens.CaptionSp,
                        color = PaperColors.Muted,
                    )
                    state.autoFillPage -> Text(
                        "自动补满已开启，当前题量已经排满，不需要再加。",
                        fontSize = PaperDimens.CaptionSp,
                        color = PaperColors.Success,
                        lineHeight = 16.sp,
                    )
                    state.liveSuggestExtra > 0 -> Text(
                        "建议再加约 ${state.liveSuggestExtra} 题，或开启「自动补满整页」由 App 代劳。",
                        fontSize = PaperDimens.CaptionSp,
                        color = PaperColors.Accent,
                        lineHeight = 16.sp,
                    )
                    else -> Text(
                        "页面排得比较满，不会浪费纸。",
                        fontSize = PaperDimens.CaptionSp,
                        color = PaperColors.Success,
                    )
                }
                Spacer(Modifier.height(10.dp))
                MiniAction("换一套看看") { state.shufflePreviewSeed() }
            }
        }
    }

    // ============================================================
    // 页脚那行字（v0.18.0 从「卷面设置」折叠区提出来）
    // ============================================================
    // 用户问「卷子左下角的文字怎么自定义」，而它原来埋在
    // 「04 卷面设置」→ 展开 → 中间那一组的第二颗 chip 后面，
    // 找得到才怪。这行字每张卷子都会印，属于高频定制项，
    // 所以提到和实时预览并排的位置，不展开也能改。
    Spacer(Modifier.height(16.dp))
    CardSection(title = "页脚那行字", hint = "印在每页左下角") {
        Field3(
            value = if (state.footerMode == 1) state.footerCustom else "",
            onValueChange = {
                state.footerCustom = it
                // 一动手打字就切到「自定义」：不然用户敲了半天，纸上还是默认那句
                if (state.footerMode != 1) state.footerMode = 1
                state.saveSettings()
            },
            placeholder = "例如：三（2）班　周末练习　做完请家长签字",
        )
        Spacer(Modifier.height(9.dp))
        SettingRow("用哪种") {
            // v0.22.0：默认印「学生名 · 每日一练」—— 用户每天给孩子打一张，
            // 署名正好分清是谁的卷子；不选学生档案时那句就只剩「每日一练」
            ChoiceChip("学生名 · 每日一练", state.footerMode == 0) {
                state.footerMode = 0
                state.saveSettings()
            }
            ChoiceChip("我写的", state.footerMode == 1) {
                state.footerMode = 1
                state.saveSettings()
            }
            ChoiceChip("不印", state.footerMode == 2) {
                state.footerMode = 2
                state.saveSettings()
            }
        }
        Spacer(Modifier.height(9.dp))
        SettingRow("生成时间") {
            // 页脚整条不印时这两颗点不动：时间本来就没地方印
            ChoiceChip("印在右侧", state.showCreatedAt, enabled = state.footerMode != 2) {
                state.showCreatedAt = true
                state.saveSettings()
            }
            ChoiceChip("不印", !state.showCreatedAt, enabled = state.footerMode != 2) {
                state.showCreatedAt = false
                state.saveSettings()
            }
        }
        Spacer(Modifier.height(9.dp))
        // v0.18.1：页码。以前只有「第 2 页起」的页眉里有页码，第一页没有 ——
        // 一张两页的卷子打印完分开放，首页根本看不出是第几页。
        SettingRow("页码") {
            ChoiceChip("第 N/M 页", state.showPageNo, enabled = state.footerMode != 2) {
                state.showPageNo = true
                state.saveSettings()
            }
            ChoiceChip("不印", !state.showPageNo, enabled = state.footerMode != 2) {
                state.showPageNo = false
                state.saveSettings()
            }
        }
        Spacer(Modifier.height(9.dp))
        // 页脚高度：直接换正文可用高度，所以它不只是外观参数
        SettingRow("页脚高度") {
            FooterSize.entries.forEach { size ->
                ChoiceChip(size.label, state.footerSize == size, enabled = state.footerMode != 2) {
                    state.footerSize = size
                    state.saveSettings()
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            when {
                state.footerMode == 2 ->
                    "页脚整条不印：文字、时间、页码、分隔线一起去掉，正文能多吃 26pt（约一行题）。"
                state.footerMode == 1 && state.footerCustom.isBlank() ->
                    "还没写内容 —— 现在纸上印的是空白页脚，写一句话，或者选「只印时间/页码」。"
                else -> "左边是你写的字，右边依次是生成时间和页码（第 N/M 页），每页都印。"
            },
            fontSize = PaperDimens.TinySp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 15.sp,
        )
        if (state.footerMode != 2) {
            Spacer(Modifier.height(4.dp))
            Text(
                "页脚高度「${state.footerSize.label}」：占 ${state.footerSize.height}pt 纸面，正文就少这么多。" +
                    when (state.footerSize) {
                        FooterSize.SHORT -> "想让正文多排一行就选它。"
                        FooterSize.NORMAL -> "常规高度，小字清楚又不抢位置。"
                        FooterSize.TALL -> "字更大更好认，但正文少约半行。"
                    },
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Muted,
                lineHeight = 15.sp,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionLabel("01", "自由组合", "共 ${state.totalQuestions} 题 · 预计 ${state.estimatedPages} 页")
    Spacer(Modifier.height(8.dp))
    CardSection {
        // 三个一键动作：不想自己算配比就点「智能配比」，凑一页刚刚好
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction("智能配比", enabled = !state.busy) { state.smartCompose() }
            MiniAction("换一套", enabled = !state.busy) { state.shufflePreviewSeed() }
            MiniAction(
                "清空",
                color = PaperColors.Error,
                enabled = state.counts.isNotEmpty(),
            ) { state.clearTypes() }
        }
        Spacer(Modifier.height(12.dp))

        if (state.selectedTypes.isEmpty()) {
            Text(
                "还没选题型。点「智能配比」自动凑一页，或者在下面搜索、逐个加入。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 17.sp,
            )
        }
        state.selectedTypes.forEach { type ->
            StepperRow(
                label = type.label,
                value = state.counts[type] ?: 0,
                // 说明要跟排版策略一致：竖式计算带答题线但会排成 3 栏，
                // 以前一律写「自动单栏」，实际印出来不是那样
                hint = when {
                    type.columns <= 1 -> "需书写过程 · 自动单栏"
                    type.answerLines > 0 -> "带答题线 · 自动 ${maxOf(type.columns, state.columns)} 栏"
                    else -> "客观题 · 按栏数排"
                },
                onMinus = { state.setCount(type, -1) },
                onPlus = { state.setCount(type, 1) },
                onMinusMore = { state.setCount(type, -5) },
                onPlusMore = { state.setCount(type, 5) },
                onRemove = { state.removeType(type) },
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionLabel("02", "出题偏好", "决定出什么题、出多少")
    Spacer(Modifier.height(8.dp))
    CardSection {
        Text(
            "题型倾向",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 7.dp),
        )
        // 七个 chip 一行放不下：用 FlowRow 自然折行，硬塞成两行 Row 会参差不齐
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
            verticalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
        ) {
            Tilt.values().forEach { t ->
                // 这一科压根没有该类题型时置灰：点了配比不会变的控件比不给点更糟
                val usable = state.canUseTilt(t)
                ChoiceChip(t.label, state.tilt == t, enabled = usable) {
                    state.applyPreference(newTilt = t)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            state.tilt.hint,
            fontSize = PaperDimens.TinySp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 15.sp,
        )
        val unusable = Tilt.values().filter { !state.canUseTilt(it) && it != Tilt.BALANCED }
        if (unusable.isNotEmpty()) {
            Text(
                "当前科目没有【" + unusable.joinToString("、") { it.label.removeSuffix("偏多") } + "】这类题，已置灰",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Muted,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Divider()
        // 11 个卷型一行塞不下：FlowRow 自然折行，两行也整齐
        SettingRowFlow("卷型") {
            PaperKind.values().forEach { k ->
                // 拼音只到六年级、小升初只在五六年级；其余按年级都能选
                val usable = k.fits(state.grade)
                ChoiceChip(k.label, state.kind == k, enabled = usable) {
                    state.applyPreference(newKind = k)
                }
            }
        }
        Text(
            state.kind.hint + "　当前配比：" +
                state.counts.entries.joinToString("、") { "${it.key.label} ${it.value}" },
            fontSize = PaperDimens.TinySp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 15.sp,
        )
        if (state.kind != PaperKind.ALL) {
            Text(
                "换了卷型会重算题型与题量；已经手动调过的题量会被覆盖，点卷型就是重新开始配比。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Muted,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Divider()
        // v0.25.0 真题库：从下载的公开试卷里切出来的原题，勾几类就排几个大题。
        // 跟知识点专项互斥 —— 两种都是「把考什么锁死」，同时开着会让人以为有一个没生效。
        SettingRowFlow("真题") {
            ChoiceChip(
                if (state.examBankReady) "用真题出卷" else "正在读取真题库…",
                state.exam,
            ) { if (state.examBankReady) state.chooseExam(!state.exam) }
        }
        if (state.exam) {
            SettingRowFlow("真题 · 要哪几类") {
                ExamFamily.entries.forEach { f ->
                    ChoiceChip(f.label, f in state.examFamilies) { state.toggleExamFamily(f) }
                }
            }
            SettingRowFlow("真题 · 每类几题") {
                listOf(3, 5, 8, 10, 15, 20).forEach { n ->
                    ChoiceChip("$n 题", state.examPer == n) { state.chooseExamPer(n) }
                }
            }
            val pool = ExamFamily.entries
                .filter { it in state.examFamilies }
                .sumOf { ExamBank.countOf(state.grade, state.subjects.map { it.code }.toSet(), it.types) }
            Text(
                if (pool == 0) {
                    // 英语单独说明：它的问题是「真题大多是听力题（没音频做不了）+ 带答案的太少」，
                    // 干巴巴一句「还没有真题」会让人以为自己选错了年级或科目。
                    if (state.subjects.size == 1 && state.subjects.first().code == "ENGLISH") {
                        "英语真题大多是听力题（没有音频做不了），能用的带答案真题太少 —— 这门建议用自编题。"
                    } else {
                        "${GradeProfiles.gradeLabel(state.grade)}${state.subjects.joinToString("、") { it.label }}还没有带答案的真题，换个年级或科目试试。"
                    }
                } else {
                    "当前年级与科目能抽 $pool 道带答案的真题，每类取 ${state.examPer} 道（同年级题不够就少给，不跨年级凑）。"
                },
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Muted,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        } else if (state.points.isEmpty()) {
            Text(
                "真题库是公开分享的期末/期中/单元卷切出来的原题，带答案。开了真题就只出真题，一个大类一个大题；" +
                    "关掉就回到按题型配比现场生成。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Muted,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Divider()
        // v0.23.0 知识点专项：前 15 个照试卷库「知识点导航」搬的，四~六年级是 v0.24.0 按
        // 同一套规则自己补的。勾了就是专项卷 —— 一个知识点一道大题、只考这一课，
        // 题型配比整块不参与。按年级分行渲染，加新知识点不用再动这里。
        MathPoint.entries.map { it.grade }.distinct().forEach { g ->
            SettingRowFlow("知识点 · ${GradeProfiles.gradeLabel(g)}") {
                MathPoint.ofGrade(g).forEach { p ->
                    ChoiceChip(p.label, p in state.points) { state.togglePoint(p) }
                }
            }
        }
        if (state.points.isEmpty()) {
            Text(
                "共 ${MathPoint.entries.size} 个专项：勾一个就是一份只练这一课的卷子（自动切到数学、年级跟着知识点走）。" +
                    "不勾就按上面的题型配比出常规卷。题量选得比题池大时会按实际能出的数量印，不会重复凑数。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Muted,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        } else {
            SettingRowFlow("每个知识点题量") {
                MathPoint.COUNTS.forEach { n ->
                    ChoiceChip("$n 题", state.pointCount == n) { state.choosePointCount(n) }
                }
            }
            Text(
                "知识点专项：${state.points.joinToString("、") { it.label }}　共 ${state.totalQuestions} 题" +
                    "（题型配比在本卷不生效）",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 15.sp,
            )
            Text(
                "清空知识点",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { state.clearPoints() },
            )
        }
    }

    // v0.6.0 题型库：搜索 + 按科目筛选，点「＋10」就能加进组合
    Spacer(Modifier.height(14.dp))
    SectionLabel("03", "题型库", "共 ${state.availableTypes.size} 种")
    Spacer(Modifier.height(8.dp))
    // 专项卷不出配比：这里必须说清楚，否则用户勾着题型再点知识点，会以为两边在打架
    if (state.points.isNotEmpty()) {
        Text(
            "当前是知识点专项（${state.points.size} 个知识点），下面的题型库本卷不参与；想混着来就先清空知识点。",
            fontSize = PaperDimens.TinySp,
            color = PaperColors.Accent,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(6.dp))
    }
    var filterSubject by remember { mutableStateOf<Subject?>(null) }
    val subjectOptions = state.availableTypes
        .mapNotNull { GeneratorRegistry.subjectOf(it) }
        .distinct()
    val pool = state.availableTypes.filter { type ->
        (filterSubject == null || GeneratorRegistry.subjectOf(type) == filterSubject) &&
            (state.typeQuery.isBlank() || type.label.contains(state.typeQuery.trim()))
    }

    CardSection {
        Field3(
            value = state.typeQuery,
            onValueChange = { state.typeQuery = it },
            placeholder = "搜索题型，比如「音标」「语法」",
            leadingIcon = Icons.Rounded.Search,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip("全部", filterSubject == null) { filterSubject = null }
            subjectOptions.forEach { s ->
                ChoiceChip(s.label, filterSubject == s) {
                    filterSubject = if (filterSubject == s) null else s
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (pool.isEmpty()) {
            Text(
                "没有匹配的题型，换个关键词试试。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
            )
        }
        pool.forEach { type ->
            val count = state.counts[type] ?: 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PaperColors.SurfaceAlt)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        type.label,
                        fontSize = PaperDimens.BodySp,
                        fontWeight = FontWeight.Medium,
                        color = PaperColors.OnSurface,
                    )
                    Text(
                        if (count > 0) {
                            "已加入 $count 题 · 再加 10 题"
                        } else if (type.columns <= 1) {
                            "需书写过程 · 加入后自动单栏"
                        } else if (type.answerLines > 0) {
                            "带答题线 · 加入后按 ${maxOf(type.columns, state.columns)} 栏排"
                        } else {
                            "客观题 · 按栏数排版"
                        },
                        fontSize = PaperDimens.TinySp,
                        color = PaperColors.OnSurfaceVariant,
                    )
                }
                MiniAction("＋10") { state.setCount(type, 10) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    Spacer(Modifier.height(16.dp))
    CustomSheetsCard(state)

    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().clickable { state.showAdvanced = !state.showAdvanced },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            SectionLabel("04", "卷面设置", if (state.showAdvanced) "收起" else "展开")
        }
    }
    if (state.showAdvanced) {
        Spacer(Modifier.height(8.dp))
        CardSection {
            SettingRow("纸张方向") {
                PageOrientation.values().forEach { o ->
                    ChoiceChip(o.shortLabel, state.orientation == o) {
                        scope.launch { state.setOrientation(o) }
                    }
                }
            }
            Text(
                "横版为 842×595pt 的真横页，系统打印框也会按 A4 横向匹配；" +
                    "内容区更宽、更矮，适合口算、默写这类短题。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 15.sp,
            )
            Divider()
            // v0.22.0 排版密度：一次把「页边距 + 字号 + 行距」摆好。
            // 三个滑杆分开调是给微调用的；用户心里那句话是「我要一张更紧的卷子」。
            SettingRowFlow("排版密度") {
                Density.values().forEach { d ->
                    ChoiceChip(d.label, state.density == d, enabled = d != Density.CUSTOM) {
                        state.applyDensity(d)
                    }
                }
            }
            Text(
                if (state.density == Density.CUSTOM) {
                    "你自己调的参数：" + "${state.margin.toInt()}pt 纸边 · ${state.bodySize.toInt()}pt 字 · " +
                        "行距「${state.lineGap.label}」。点上面任意一档就能回到预设。"
                } else {
                    state.density.hint
                },
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 15.sp,
            )
            Divider()
            // 页边距 / 字号：密度预设之外的两个单旋钮。
            // 12pt ≈ 4.2mm，已经贴到家用打印机不可打印边界，所以最小只给到 12。
            SettingRowFlow("页边距") {
                listOf(12f, 20f, 28f, 40f).forEach { m ->
                    ChoiceChip("${m.toInt()}pt", state.margin == m) {
                        state.margin = m
                        state.saveSettings()
                    }
                }
            }
            SettingRowFlow("正文字号") {
                listOf(9f, 10f, 11f, 12f).forEach { sz ->
                    ChoiceChip("${sz.toInt()}pt", state.bodySize == sz) {
                        state.bodySize = sz
                        state.saveSettings()
                    }
                }
            }
            Text(
                "收窄页边距只损失四周空白，缩字号却直接损失可读性 —— 想少印一页先动页边距。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 15.sp,
            )
            Divider()
            SettingRow("纸张利用") {
                ChoiceChip("自动补满整页", state.autoFillPage) { state.autoFillPage = true }
                ChoiceChip("严格按题量", !state.autoFillPage) { state.autoFillPage = false }
            }
            Text(
                "低于 84% 时自动补题再排版，避免只印半张纸。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
            )
            Divider()
            // v0.18.3：用户的原话是「可以选择试卷生成几页，这样的话题目可以根据
            // 试卷自动压缩显示的内容大小」。这是**上限**不是定值 —— 题少就自然少页，
            // 硬撑到 N 页只能靠放大字号或拉行距，两种都像排版出错。
            SettingRow("正文最多几页") {
                ChoiceChip("不限", state.targetPages == 0) {
                    state.targetPages = 0
                    state.saveSettings()
                }
                listOf(1, 2, 3, 4, 5, 6).forEach { n ->
                    ChoiceChip("$n 页", state.targetPages == n) {
                        state.targetPages = n
                        state.saveSettings()
                    }
                }
            }
            Text(
                "题多到超过这个页数时，App 会整体缩小字号把内容压进去（最多缩到七成）；" +
                    "压不进去就老实多印一页 —— 印一张看不清的卷子比多印一张纸糟。" +
                    "只约束正文，答案页另计。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 15.sp,
            )
            Divider()
            SettingRow("生成张数") {
                ChoiceChip("1 张", state.copies == 1) { state.copies = 1 }
                ChoiceChip("2 张（A / B 卷）", state.copies == 2) { state.copies = 2 }
            }
            Text(
                if (state.copies == 2) {
                    "两张卷的大题结构和每大题题数完全一致，只换题目 —— 同桌拿到不同的卷子，" +
                        "抄不了答案，批改时还能对号。两张都会存进试卷库。"
                } else {
                    "需要防抄时选 2 张：同一套结构出 A / B 两份，题目各不相同。"
                },
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 15.sp,
            )
            Divider()
            SettingRow("难度") {
                listOf(1 to "基础", 2 to "标准", 3 to "提高").forEach { (v, label) ->
                    ChoiceChip(label, state.difficulty == v) { state.difficulty = v }
                }
            }
            Divider()
            SettingRow("客观题栏数") {
                listOf(2, 3, 4).forEach { c ->
                    ChoiceChip("$c 栏", state.columns == c) { state.columns = c }
                }
            }
            Divider()
            SettingRow("答案页") {
                AnswerMode.values().forEach { m ->
                    ChoiceChip(m.label, state.answerMode == m) { state.answerMode = m }
                }
            }
            Divider()
            SettingRow("姓名 / 班级 / 日期 / 得分") {
                ChoiceChip("显示", state.showNameRow) {
                    state.showNameRow = true
                    state.saveSettings()
                }
                ChoiceChip("隐藏", !state.showNameRow) {
                    state.showNameRow = false
                    state.saveSettings()
                }
            }
            Text(
                "隐藏后省下 26pt 纸面高度（约一行题）。孩子在家做练习卷时，这四栏本来就没人填。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 15.sp,
            )
            Divider()
            SettingRow("标题排版") {
                ChoiceChip("两行", !state.headerOneLine) {
                    state.headerOneLine = false
                    state.saveSettings()
                }
                ChoiceChip("一行", state.headerOneLine) {
                    state.headerOneLine = true
                    state.saveSettings()
                }
            }
            Text(
                "「共 N 题 满分 M 分」挪到标题右边同一行，卷头少占 17pt。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
            )
            Divider()
            SettingRow("题间行距") {
                LineGap.entries.forEach { g ->
                    ChoiceChip(g.label, state.lineGap == g) {
                        state.lineGap = g
                        state.saveSettings()
                    }
                }
            }
            // 自定义行距：±1pt 微调。用户的原话是「不要那么宽，再窄一点，
            // 或者后台可以自定义」——三档预设总有一档不对味，就直接给数字。
            if (state.lineGap == LineGap.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "题与题之间空 ${state.lineGapCustom.toInt()} pt",
                        fontSize = PaperDimens.CaptionSp,
                        color = PaperColors.OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    MiniAction("−5", enabled = state.lineGapCustom > 0f) {
                        state.lineGapCustom = (state.lineGapCustom - 5f).coerceIn(0f, 24f)
                        state.saveSettings()
                    }
                    MiniAction("−1", enabled = state.lineGapCustom > 0f) {
                        state.lineGapCustom = (state.lineGapCustom - 1f).coerceIn(0f, 24f)
                        state.saveSettings()
                    }
                    MiniAction("+1", enabled = state.lineGapCustom < 24f) {
                        state.lineGapCustom = (state.lineGapCustom + 1f).coerceIn(0f, 24f)
                        state.saveSettings()
                    }
                    MiniAction("+5", enabled = state.lineGapCustom < 24f) {
                        state.lineGapCustom = (state.lineGapCustom + 5f).coerceIn(0f, 24f)
                        state.saveSettings()
                    }
                }
            }
            Text(
                // 数字直接读枚举，别写死 —— 上次调窄了上限，文案还留着旧数字，
                // 界面说「最多 11pt」而实际是 9pt，对不上账
                when (state.lineGap) {
                    LineGap.TIGHT ->
                        "题与题几乎贴着排（0~${LineGap.TIGHT.max.toInt()}pt），只留够分行的缝 —— 最省纸，适合纯口算。"
                    LineGap.COMPACT ->
                        "默认档。挤一点，页尾的空档会摊开补到行距上，最多到 ${LineGap.COMPACT.max.toInt()}pt。"
                    LineGap.NORMAL ->
                        "常规间距，页尾有空档时会撑开到 ${LineGap.NORMAL.max.toInt()}pt 铺满整页。"
                    LineGap.LOOSE ->
                        "题与题留得更开（最多 ${LineGap.LOOSE.max.toInt()}pt），方便圈画和写过程；页数可能多一张。"
                    LineGap.CUSTOM ->
                        "严格按你定的间距排，不再自动撑开 —— 选它就一定是你看到的那个宽度。"
                },
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 15.sp,
            )
            // 页脚那三组控件（文字 / 生成时间）已经提到上面的独立卡片里，
            // 这里不再重复放 —— 两处都能改同一份设置，用户会怀疑哪一处才是真的。
            Divider()
            Spacer(Modifier.height(10.dp))
            Field3(
                value = state.titleOverride ?: "",
                onValueChange = { state.titleOverride = it },
                placeholder = "留空则自动生成",
                label = "卷子标题（可选）",
            )        }
    } else {
        // 折叠态也把当前卷面配置说清楚，不展开就能确认
        Spacer(Modifier.height(6.dp))
        Text(
            "${state.columns} 栏 · ${state.orientation.shortLabel} · 纸边 ${state.margin.toInt()}pt · 字 ${state.bodySize.toInt()}pt · ${state.answerMode.label} · " +
                when (state.difficulty) { 1 -> "基础"; 3 -> "提高"; else -> "标准" } +
                " · " + if (state.autoFillPage) "自动补满整页" else "严格按题量",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            modifier = Modifier.padding(start = 30.dp),
        )
    }
}

// ------------------------------------------------------------------ 放大查看

/**
 * 全屏查看试卷：黑底 + 等比铺满 + 双指缩放，**点任意处退出**。
 *
 * 为什么不做成普通的「点开大图」：A4 一页的小字（括号、题号、单位）在缩略图里
 * 只有 5-6 个像素高，光放大到屏宽还是看不清，必须能双指再往里推。
 * 所以这里给了 1×~5× 的缩放，退出仍然只要点一下 —— 放大之后想退出的动作
 * 不该变成「先缩回 1 倍再点关闭」。
 *
 * v0.17.0：一整张卷子往往 2-3 页，以前放大后只能看当前这页，想看下一页得先退出、
 * 在缩略图里滑到那一页、再点进来。现在放大浮层里直接左右滑就能翻页，
 * 两侧也给了箭头（有些人不会想到「在这个黑屏上也能横滑」）。
 *
 * 手势分工是这里唯一的难点：
 *  - 1 倍时**单指横滑 = 翻页**（交给 HorizontalPager，我们不碰）；
 *  - 双指 = 缩放（自己吃掉事件，pager 的拖拽会被取消）；
 *  - 放大后**单指拖动 = 平移画面**（此时 pager 已关掉滚动，事件全归我们）。
 * 如果直接用 transformable，它会连单指拖拽一起吃掉，翻页就永远滑不动了，
 * 所以这里手写一个「只在双指 / 已放大时才消费事件」的手势。
 */
@Composable
private fun PageZoomDialog(
    pages: List<ImageBitmap>,
    initialPage: Int,
    onDismiss: () -> Unit,
) {
    if (pages.isEmpty()) return
    val count = pages.size
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(
        initialPage = initialPage.coerceIn(0, count - 1),
    ) { count }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 翻页后放大倍数必须归 1：上一页推到了 3 倍，翻过去新的一页从 3 倍开始
    // 会直接显示一块模糊的纸角，还以为这页印坏了
    LaunchedEffect(pager.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var event = awaitPointerEvent()
                        while (event.changes.any { it.pressed }) {
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                // 双指：先缩放，再按两指中点位移平移
                                val next = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                if (next <= 1.001f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = next
                                    offset += event.calculatePan()
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pressed == 1 && scale > 1.001f) {
                                // 已经放大了：单指拖动就是移动画面，不让它去翻页
                                offset += event.calculatePan()
                                event.changes.forEach { it.consume() }
                            }
                            event = awaitPointerEvent()
                        }
                    }
                },
        ) {
            HorizontalPager(
                state = pager,
                // 放大状态下交给手指平移画面，翻页要先把倍数缩回去
                userScrollEnabled = scale <= 1.001f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Image(
                    bitmap = pages[page],
                    contentDescription = "第 ${page + 1} 页",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        // 「点任意处退出」必须挂在**每一页的图片上**，不能挂在最外层 Box：
                        // 挂外层时它会在按下那一刻就吃掉 down 事件，滚动手势收不到起点，
                        // 横滑翻页整个失灵（实测滑了没反应）。挂在页内就等于
                        // 预览缩略图那一套「页内可点 + 外层可滑」，两者互不干扰。
                        .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
            }
            if (count > 1) {
                // 箭头：黑屏上横滑不是每个人都想得到，给个明摆着的出口
                ZoomArrow("‹", Alignment.CenterStart, enabled = pager.currentPage > 0) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }
                ZoomArrow("›", Alignment.CenterEnd, enabled = pager.currentPage < count - 1) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }
            Text(
                buildString {
                    append("第 ${pager.currentPage + 1} / $count 页")
                    if (scale > 1.001f) append(" · 已放大 ${"%.1f".format(scale)}×")
                    append(if (count > 1) " · 左右滑动翻页" else "")
                    append(" · 点任意处退出")
                },
                fontSize = PaperDimens.TinySp,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp),
            )
        }
    }
}

/** 放大浮层两侧的翻页箭头。到头的那一侧变淡但仍然占位，避免页面跟着左右晃 */
@Composable
private fun BoxScope.ZoomArrow(
    glyph: String,
    align: Alignment,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(align)
            .padding(horizontal = 12.dp)
            .width(44.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            // 深色半透明圆底：A4 纸面是白的，先前的「14% 白底 + 白箭头」
            // 正好压在纸上，等于隐形；黑底圆 + 白字在纸上、黑边上都看得见
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 26.sp,
            color = Color.White.copy(alpha = if (enabled) 0.95f else 0.3f),
        )
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Text(
        label,
        fontSize = PaperDimens.CaptionSp,
        color = PaperColors.OnSurfaceVariant,
        modifier = Modifier.padding(bottom = 7.dp, top = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall)) { content() }
    Spacer(Modifier.height(10.dp))
}

/** 同 [SettingRow]，但内容会自动折行 —— 11 个卷型硬塞一行会被挤出屏幕 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRowFlow(label: String, content: @Composable () -> Unit) {
    Text(
        label,
        fontSize = PaperDimens.CaptionSp,
        color = PaperColors.OnSurfaceVariant,
        modifier = Modifier.padding(bottom = 7.dp, top = 4.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
        verticalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
    ) { content() }
    Spacer(Modifier.height(10.dp))
}

// ------------------------------------------------------------------ 预览页：一次只看一页

@Composable
private fun PreviewScreen(
    state: AppState,
    scope: CoroutineScope,
    activity: Activity?,
) {
    val gp = state.paper
    val pdf = state.pdf
    val isRef = state.isReference
    val pct = state.lastContentFillPercent
    // 点开的是第几页；null = 没在放大
    var zoomPage by remember { mutableStateOf<Int?>(null) }

    CardSection {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    // 这里以前写死「1 页」，而速查表实际可能排到 2 页，副标题又单独报了真实页数，
                    // 同一张卡片里两个数字互相打脸。首行只说内容性质，页数交给下面一行。
                    if (isRef) {
                        "内置速查表 · 已自动排版"
                    } else {
                        "共 ${gp?.questions?.size ?: 0} 题"
                    },
                    fontSize = PaperDimens.BodySp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaperColors.OnSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "共 ${pdf?.pageCount ?: 0} 页 A4 · 与打印结果完全一致",
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                )
                // v0.18.3：为了压进指定页数缩过字号，必须说清楚 ——
                // 不说的话用户只会觉得「这版字怎么变小了」，像个 bug
                val shrink = pdf?.typeScale ?: 1f
                if (!isRef && shrink < 0.995f) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "为压进 ${state.targetPages} 页，正文字号缩到 ${(shrink * 100).toInt()}%",
                        fontSize = PaperDimens.TinySp,
                        color = PaperColors.Accent,
                    )
                } else if (!isRef && state.targetPages > 0 &&
                    (pdf?.contentPageCount ?: 0) > state.targetPages
                ) {
                    // 压不进就把话说透：用户设了 2 页却拿到 3 页，不解释等于「这个设置坏了」
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "题量太多，缩到最小也塞不进 ${state.targetPages} 页，已按原字号排" +
                            "${pdf?.contentPageCount} 页",
                        fontSize = PaperDimens.TinySp,
                        color = PaperColors.Accent,
                    )
                }
            }
            if (!isRef) {
                StatChip(
                    "末页正文 $pct%",
                    color = if (pct < 84) PaperColors.Accent else PaperColors.Success,
                    bg = if (pct < 84) PaperColors.AccentContainer else PaperColors.PrimaryContainer,
                )
            }
        }
        if (!isRef && state.finalSuggestExtra > 0) {
            Spacer(Modifier.height(9.dp))
            Text(
                // 三种情况三句话，别混：
                //  1) 按单元出卷 —— 这一单元的题就这么多，给「改成按整册」的出口；
                //  2) 题库抽干（科学一个年级只有三十几道）—— 再加也加不出来，说清上限在哪；
                //  3) 真还有空间 —— 才建议补满。
                when {
                    state.unitTopicIds.isNotEmpty() ->
                        "这一单元的题已经全部印上了，所以最后一页没排满 —— 想多练就点下面的「改成按整册出卷」。"
                    state.poolDrained ->
                        "这一年级能出的题已经全部印上了（共 ${state.paper?.questions?.size ?: 0} 道），" +
                            "最后一页没排满是题库上限 —— 想多练可以换年级或换科目。"
                    else ->
                        "最后一页还有空白，建议再加约 ${state.finalSuggestExtra} 题 —— 直接在下面点「补满这一页」即可。"
                },
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.Accent,
                lineHeight = 16.sp,
            )
        }
    }

    if (!isRef) {
        Spacer(Modifier.height(PaperDimens.Gap))
        CardSection(
            title = "这一页还有空白？",
            hint = "末页正文 $pct% · 加题后立刻重排，放不下会自动减量，绝不白多印一张纸",
        ) {
            // 按单元出的卷子：这一单元的题就在这么多，点「加题」也变不出新题来。
            // 与其让家长点空按钮，不如直接给一个真能加题的出口。
            if (state.unitTopicIds.isNotEmpty()) {
                Text(
                    "这一单元的题已经全部印上了。想再多练几道，就按整个学期出卷。",
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton("改成按整册出卷", enabled = !state.busy) {
                    scope.launch { state.widenToGrade() }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall)) {
                    PrimaryButton(
                        "补满这一页",
                        enabled = !state.busy && pct < 92,
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { state.fillLastPage() } }
                    SecondaryButton(
                        "加 ${state.extraAddCount} 题",
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { state.addQuestions(state.extraAddCount) } }
                }
                Spacer(Modifier.height(6.dp))
                StepperRow(
                    label = "自定义加题数量",
                    value = state.extraAddCount,
                    hint = "1–30 题，会先试排再减量",
                    onMinus = { state.changeExtraAdd(-1) },
                    onPlus = { state.changeExtraAdd(1) },
                    onMinusMore = { state.changeExtraAdd(-5) },
                    onPlusMore = { state.changeExtraAdd(5) },
                )
                Divider()
            }
            SettingRow("纸张方向（立即重排）") {
                PageOrientation.values().forEach { o ->
                    ChoiceChip(o.shortLabel, state.orientation == o) {
                        scope.launch { state.setOrientation(o) }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(PaperDimens.Gap))
    Segmented(
        items = listOf("A4 页面", if (isRef) "说明" else "题目清单"),
        selectedIndex = if (state.showQuestionList) 1 else 0,
        onSelect = { state.showQuestionList = it == 1 },
    )
    Spacer(Modifier.height(PaperDimens.Gap))

    if (!state.showQuestionList) {
        if (state.previews.isNotEmpty()) {
            // 放大浮层：放 composition 里任意位置都行（Dialog 自开窗口），
            // 放这里是为了和「点哪一页」的索引共用同一份 previews
            zoomPage?.let { index ->
                PageZoomDialog(
                    pages = state.previews,
                    initialPage = index,
                    onDismiss = { zoomPage = null },
                )
            }
            val pageCount = state.previews.size
            val previewPager = rememberPagerState(
                initialPage = state.previewPage.coerceIn(0, pageCount - 1),
            ) { pageCount }
            // 手指滑到哪页，页码就跟着走；点下方圆点也能跳
            LaunchedEffect(previewPager.currentPage, previewPager.isScrollInProgress) {
                if (!previewPager.isScrollInProgress) state.previewPage = previewPager.currentPage
            }
            LaunchedEffect(state.previewPage) {
                if (previewPager.currentPage != state.previewPage) {
                    previewPager.animateScrollToPage(state.previewPage)
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val ratio = if (state.orientation == PageOrientation.LANDSCAPE) 0.74f else 1.42f
                // 按宽度算出来的高度约 527dp，一屏放不下，整页会被底栏切掉、圆点也被顶出去。
                // 这里再按屏高封顶到 52%，配合 ContentScale.Fit，整页 + 圆点 + 页码刚好一屏可见。
                val screenH = LocalConfiguration.current.screenHeightDp.dp
                val pageH = minOf(maxWidth * ratio, screenH * 0.52f)
                HorizontalPager(
                    state = previewPager,
                    pageSpacing = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageH),
                ) { page ->
                    val bmp = state.previews.getOrNull(page)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .shadow(6.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .padding(4.dp)
                            // 点一下放大看：缩略图只有屏幕一半高，想看清「竖式第 3 行
                            // 有没有印歪」「括号全角还是半角」根本不够
                            .clickable { zoomPage = page },
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "第 ${page + 1} 页",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.previews.indices.forEach { i ->
                    val on = i == state.previewPage
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (on) 20.dp else 7.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (on) PaperColors.Primary else PaperColors.OutlineStrong)
                            .clickable { state.previewPage = i }
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "第 ${state.previewPage + 1} / ${state.previews.size} 页 · 左右滑动翻页",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "点一下页面可放大查看",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
            // A/B 卷：两张的题量结构一致、题目不同，翻页看的是同一张，切卷看的是另一张
            if (state.batchSize > 1) {
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(0, 1).forEach { i ->
                        val label = if (i == 0) "A 卷" else "B 卷"
                        ChoiceChip(label, state.activeCopy == i) { state.selectCopy(i) }
                        Spacer(Modifier.width(PaperDimens.GapSmall))
                    }
                    Text(
                        "两张都已存入试卷库",
                        fontSize = PaperDimens.TinySp,
                        color = PaperColors.Muted,
                    )
                }
            }
        } else {
            CardSection { Text("还没有生成内容", fontSize = PaperDimens.BodySp) }
        }
        Spacer(Modifier.height(10.dp))
        if (!isRef) {
            // v0.18.0：批改入口提到这里。原来只有一句「到题目清单勾错题」的说明文字，
            // 家长根本不知道该点哪儿；给一颗正式的按钮，点开就是题号表。
            SecondaryButton("批改记错题", modifier = Modifier.fillMaxWidth()) {
                state.startGrading()
            }
            Text(
                "孩子做完后点上面这颗按钮，把错题的题号点一遍（或直接敲 3 7 12-15），" +
                    "它们就会进错题本；换题、删题、加题在下面的「题目清单」里。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
            // 图片导出是另一件事，和上面的批改不是一个话题，隔一条线分开
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            // 图片导出：奔图云打印小程序、微信里的打印号只收图片不收 PDF
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(
                    if (state.busy) "正在导出…" else "存成图片（每页一张 PNG）",
                    modifier = Modifier.weight(1f),
                ) { scope.launch { state.exportImages() } }
            }
            Text(
                "微信里的打印号、奔图云打印小程序多数只收图片；导出的图片在 " +
                    "Android/data/com.papermill.app/files/papers/images/ 里。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Muted,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    } else if (isRef) {
        CardSection(title = "关于这一页") {
            Text(
                "这一页由本 App 自行编排绘制：速查表只收基础事实与公式，练习纸和主题识字纸" +
                    "用 Canvas 自绘格子与词卡，词表拼音自编重录。可反复打印使用，" +
                    "不含任何第三方原题、PDF 或图片，也不需要答案页。",
                fontSize = PaperDimens.BodySp,
                color = PaperColors.OnSurface,
                lineHeight = 19.sp,
            )
        }
    } else {
        CardSection(
            title = "题目清单",
            hint = if (state.batchSelect) {
                "勾选要删的题 · 也可以直接敲题号"
            } else {
                "可单题换 / 删 / 加 · 勾完点「加入错题本」"
            },
        ) {
            // v0.18.2：原来只有「+ 在末尾加一题」，加进来的必然是最后一题的题型 ——
            // 想补两道应用题、卷子末尾是口算，就只能回参数页重来。
            // 现在点开能选题型，卷里没有这道大题就新起一道。
            //
            // v0.18.3：加「批量删题」。60 题的口算卷要删掉中间十来道重复的，
            // 一题一点「删这题」太累 —— 而且每删一次整卷重排、题号重编，点着点着眼花。
            var expr by remember { mutableStateOf("") }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
            ) {
                if (state.batchSelect) {
                    MiniAction("全选", enabled = !state.busy) { state.selectAllQuestions() }
                    MiniAction("清空选择") { state.clearSelection() }
                    MiniAction("完成") { state.exitBatchSelect() }
                } else {
                    MiniAction("＋ 加一题", enabled = !state.busy) { state.addingQuestion = true }
                    MiniAction("批量删题", color = PaperColors.Error, enabled = !state.busy) {
                        state.enterBatchSelect()
                    }
                }
            }
            if (state.batchSelect) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Field3(
                            value = expr,
                            onValueChange = { expr = it },
                            placeholder = "敲题号批量选，如 3 7 12-15",
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    MiniAction("选中", enabled = expr.isNotBlank()) {
                        if (state.selectByExpr(expr) > 0) expr = ""
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Divider()
            gp?.questions?.forEach { q ->
                // 同一个 checkbox 两种语义：平时勾「这道做错了」，批量模式下勾「要删掉」。
                // 分两个模式是刻意的 —— 勾好错题手滑点到删除，半张卷子就没了。
                val picked =
                    if (state.batchSelect) q.no in state.selectedNos else q.no in state.wrongNumbers
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (state.batchSelect && picked) {
                                Modifier.background(
                                    PaperColors.Error.copy(alpha = 0.06f),
                                    RoundedCornerShape(10.dp),
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = picked,
                            onCheckedChange = {
                                if (state.batchSelect) state.toggleSelect(q.no) else state.toggleWrong(q.no)
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${q.no}. ${q.stem}",
                                fontSize = PaperDimens.CaptionSp,
                                color = PaperColors.OnSurface,
                                lineHeight = 16.sp,
                            )
                            Text(
                                "答案：${q.answer}",
                                fontSize = PaperDimens.TinySp,
                                color = PaperColors.OnSurfaceVariant,
                            )
                        }
                    }
                    // 批量模式下不给单题按钮：一行一个「删这题」，用户会分不清
                    // 到底哪条路才是「我刚勾的那些」
                    if (!state.batchSelect) {
                        Row(
                            Modifier.padding(start = 46.dp),
                            horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
                        ) {
                            MiniAction("换一题", enabled = !state.busy) {
                                scope.launch { state.replaceQuestion(q.no) }
                            }
                            MiniAction(
                                "删这题",
                                color = PaperColors.Error,
                                enabled = !state.busy,
                            ) { scope.launch { state.removeQuestion(q.no) } }
                        }
                    }
                }
                Divider()
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.batchSelect) {
                        "已选 ${state.selectedNos.size} 题"
                    } else {
                        "已勾 ${state.wrongNumbers.size} 题"
                    },
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.batchSelect) {
                    MiniAction(
                        "删除选中",
                        color = PaperColors.Error,
                        enabled = state.selectedNos.isNotEmpty() && !state.busy,
                    ) { scope.launch { state.removeQuestions(state.selectedNos) } }
                } else {
                    MiniAction("加入错题本") { state.addWrongToMistakes() }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 速查表：2 列网格

private fun LazyListScope.referenceScreenItems(state: AppState, scope: CoroutineScope) {
    item {
        CardSection {
            Text(
                "点任意一张即可生成 1 页 A4 PDF，用底部「系统打印」或「分享」远程打印。" +
                    "速查表只收基础事实与公式；练习纸与主题识字纸按词数 / 格数铺满整页，不浪费纸。",
                fontSize = PaperDimens.BodySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item { SectionLabel("01", "速查表", "${ReferenceData.sheets.size} 张 · 事实与公式") }
    ReferenceData.grouped.forEach { (group, list) ->
        item {
            Spacer(Modifier.height(PaperDimens.GapSmall))
            Text(
                group,
                fontSize = PaperDimens.LabelSp,
                fontWeight = FontWeight.SemiBold,
                color = PaperColors.OnSurface,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
            )
        }
        sheetRows(
            keyPrefix = "ref-$group",
            items = list,
            titleOf = { it.title },
            descOf = { it.subtitle },
            metaOf = { "A4 · 立即打印" },
            onClick = { sheet -> scope.launch { state.renderReference(sheet) } },
        )
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item { SectionLabel("02", "练习纸", "${PracticeData.sheets.size} 张 · 格子铺满整页") }
    // v0.15.0：9 张涨到 22 张后不再平铺，按用途分 5 组显示（和上面速查表的分组样式一致）
    PracticeData.sheetGroups.forEach { (group, list) ->
        item {
            Spacer(Modifier.height(PaperDimens.GapSmall))
            Text(
                group,
                fontSize = PaperDimens.LabelSp,
                fontWeight = FontWeight.SemiBold,
                color = PaperColors.OnSurface,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
            )
        }
        sheetRows(
            keyPrefix = "practice-$group",
            items = list,
            titleOf = { it.title },
            descOf = { it.subtitle },
            metaOf = { "1 页 ${it.orientation.shortLabel}" },
            onClick = { sheet -> scope.launch { state.renderPractice(sheet) } },
        )
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item {
        SectionLabel("03", "主题识字纸", "${PracticeData.themeSheets.size} 张 · 6 大生活场景 × 描红 / 默写")
    }
    item {
        Spacer(Modifier.height(8.dp))
        Text(
            "每个主题把人物、物品、设施的词做成一页：识字卡印浅灰范字供描红，" +
                "默写格只给拼音、田字格留空。词表自编、拼音逐条校对，按词数自动铺满一页。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 17.sp,
        )
    }
    item { Spacer(Modifier.height(8.dp)) }
    sheetRows(
        keyPrefix = "theme",
        items = PracticeData.themeSheets,
        titleOf = { it.title },
        descOf = { it.subtitle },
        metaOf = { "1 页 竖版 · 立即打印" },
        onClick = { sheet -> scope.launch { state.renderPractice(sheet) } },
    )

    item {
        Spacer(Modifier.height(PaperDimens.Gap))
        Text(
            "练习纸版式参考 namishu/printables（MIT），全部用本 App 自绘线条重新实现，" +
                "未打包其 PDF 与字体；田字格、米字格、坐标纸等为通行基础版式，自行编排。" +
                "主题识字只借鉴「拼音在上、汉字在下」的通用卡式版式，词表与拼音均为自编重录。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 17.sp,
        )
    }
}

/**
 * 速查表 / 练习纸共用的 2 列卡片网格（Lazy 版：每行一个 item，滑到哪才组合哪几行）。
 *
 * [keyPrefix] 不能省：同一个 LazyColumn 里会被调用十几次（速查表 5 组 + 练习纸 5 组 +
 * 主题识字），不给前缀的话每组的行号都从 0 开始，`key = "row-0"` 会重复，
 * LazyColumn 直接抛「key 重复」。而给了 key 之后，滑到一半再滑回来能复用
 * 已组合的行，不必整屏重建。
 */
private fun <T> LazyListScope.sheetRows(
    keyPrefix: String,
    items: List<T>,
    titleOf: (T) -> String,
    descOf: (T) -> String,
    metaOf: (T) -> String,
    onClick: (T) -> Unit,
) {
    items.chunked(2).forEachIndexed { rowIndex, row ->
        item(key = "$keyPrefix-row-$rowIndex") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = PaperDimens.GapSmall),
                horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
            ) {
                row.forEach { entry ->
                    PresetCard(
                        title = titleOf(entry),
                        desc = descOf(entry),
                        meta = metaOf(entry),
                        onClick = { onClick(entry) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ 错题本

private fun LazyListScope.mistakesScreenItems(state: AppState) {
    val filtered = state.mistakes.filter {
        state.masteryFilter == "ALL" || it.mastery == state.masteryFilter
    }
    val pending = state.mistakes.count { it.mastery != "MASTERED" }

    item {
        CardSection {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${state.mistakes.size} 条", fontSize = PaperDimens.BodySp, modifier = Modifier.weight(1f))
                StatChip("待练 $pending 条", color = PaperColors.Accent, bg = PaperColors.AccentContainer)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "全部", "NEW" to "未练", "PRACTICED" to "练过", "MASTERED" to "已掌握")
                    .forEach { (k, label) ->
                        ChoiceChip(label, state.masteryFilter == k) { state.masteryFilter = k }
                    }
            }
        }
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }

    // 一条错题一个 item：错题攒到几十条时不会再一次性铺满整页
    filtered.forEach { m ->
        // key 用错题 id：切换「全部 / 未练 / 练过 / 已掌握」时列表内容整批变化，
        // 没 key 的话 Compose 按位置比对，会把「第 3 条」当成同一条改内容 ——
        // 整屏重组重绘，筛选看起来就是卡一下
        item(key = "mistake-${m.id}") {
            CardSection(
                title = "第 ${m.sourceNo} 题 · ${m.topic}",
                // 题型要印中文名：这里原来直接把枚举名吐出来，界面上写的是「三年级 · ORAL」
                hint = "${GradeProfiles.gradeLabel(m.grade)} · " +
                    runCatching { QuestionType.valueOf(m.type).label }.getOrDefault(m.type),
            ) {
                Text(m.stem, fontSize = PaperDimens.BodySp, color = PaperColors.OnSurface, lineHeight = 18.sp)
                Spacer(Modifier.height(5.dp))
                Text("答案：${m.answer}", fontSize = PaperDimens.CaptionSp, color = PaperColors.OnSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall)) {
                    ChoiceChip("状态：${masteryLabel(m.mastery)}", false) {}
                    ChoiceChip("标记已掌握", false) { state.setMastery(m.id, "MASTERED") }
                    ChoiceChip("重新待练", false) { state.setMastery(m.id, "NEW") }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (filtered.isEmpty()) {
        item {
            CardSection(title = "还没有错题") {
                Text(
                    "批改完回到「试卷库」，点那张卷子的「批改记错题」，把错题号点一遍就会进这里；" +
                        "也可以从试卷预览页点「批改记错题」。",
                    fontSize = PaperDimens.BodySp,
                    color = PaperColors.OnSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    item {
        Text(
            "已掌握的题不参与组卷；同一知识点最多取 3 题。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
        )
    }
}

private fun masteryLabel(m: String) = when (m) {
    "NEW" -> "未练"; "PRACTICED" -> "练过"; "MASTERED" -> "已掌握"; else -> m
}

// ------------------------------------------------------------------ 更多

private fun LazyListScope.moreScreenItems(
    state: AppState,
    scope: CoroutineScope,
    onImport: () -> Unit,
    onPickPdf: () -> Unit,
) {
    // 内置内容：数字全部当场从数据源算，不写死文案 —— 题库扩容后这里自动跟着变
    item { ContentSummaryCard(state.bankSize) }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item {
        CardSection(
            title = "底部 tab 栏",
            hint = "关掉的入口会从底部暂时消失，随时能在这里打开",
        ) {
            ListRow(
                "半透明玻璃",
                if (state.dockGlass) "已开启：滚动内容会从 tab 栏背后透出来"
                else "已关闭：不透明实底（默认），文字最清楚",
                trailing = if (state.dockGlass) "开" else "关",
            ) { state.updateDockGlass(!state.dockGlass) }

            // 每个页签一行开关；「更多」是这套设置的入口，关了就再也进不来，所以固定住
            NavTab.entries.forEach { tab ->
                Divider()
                val on = tab in state.dockTabs
                val fixed = tab == NavTab.MORE
                ListRow(
                    tab.label,
                    when {
                        fixed -> "设置入口，固定显示"
                        on -> "显示在 tab 栏上"
                        else -> "已隐藏 · 这个页面暂时进不去"
                    },
                    trailing = if (fixed) "固定" else if (on) "显示" else "隐藏",
                ) {
                    state.toggleDockTab(tab)?.let { state.toast = it }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "至少保留两个页签。隐藏不是删除：试卷库、课本里的内容都还在，" +
                    "重新打开就能看到。",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.Muted,
                lineHeight = 15.sp,
            )
        }
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item {
        CardSection(title = "数据") {
            // 学生档案入口（v0.19.0）：首页那条学生栏是主入口，
            // 这里补一条 —— 首页页签被用户关掉时，档案不能就此摸不着
            ListRow(
                "学生档案",
                if (state.students.isEmpty()) "还没建档案 · 建了就能点名字直接出卷"
                else "${state.students.size} 位学生 · 点名字按他的年级和学期出卷",
            ) { state.screen = Screen.STUDENTS }
            Divider()
            // 试卷库已经是页签时就别再摆一行重复入口；
            // 被隐藏时这里补一条，免得数据彻底摸不到
            if (NavTab.HISTORY !in state.dockTabs) {
                ListRow("试卷库", "${state.savedPapers.size} 张卷子") {
                    state.toggleDockTab(NavTab.HISTORY)
                    state.goTab(NavTab.HISTORY)
                }
                Divider()
            }
            ListRow("分享备份文件", "导出 JSON，含试卷库与错题本") { state.shareBackup() }
            Divider()
            ListRow("从备份恢复", "选择之前导出的 JSON") { onImport() }
        }
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item {
        CardSection(title = "打印与更新") {
            if (NavTab.TEXTBOOK !in state.dockTabs) {
                ListRow("课本", "按单元出卷 · 教材原文在线阅读") {
                    state.toggleDockTab(NavTab.TEXTBOOK)
                    state.goTab(NavTab.TEXTBOOK)
                }
                Divider()
            }
            ListRow("打印手机里的 PDF", "老师发的卷子 / 自己下的材料，直接打") { onPickPdf() }
            Divider()
            ListRow("打印帮助", "在家 / 远程两种方式") { state.screen = Screen.HELP }
            Divider()
            ListRow(
                "检查更新",
                state.updateInfo.ifEmpty { "当前版本 v${state.versionName} · 点击检查 GitHub Release" },
                trailing = if (state.busy) "…" else "›",
            ) { scope.launch { state.checkUpdate() } }
            val release = state.updateRelease
            if (release != null) {
                Divider()
                ListRow("打开 Release 页面", release.name) { state.openUrl(release.pageUrl) }
                // 只有真的有新版才给「下载并安装」；已经是最新版时点它是白下 3MB
                val newer = UpdateChecker.isNewer(release.version, state.versionName)
                if (release.apkUrl != null && newer) {
                    Divider()
                    if (state.updateDownloading) {
                        // 3MB 在慢网下要几十秒，只写「正在下载」会让人以为卡死
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "正在下载 ${state.updatePercent}%",
                                fontSize = PaperDimens.BodySp,
                                color = PaperColors.OnSurface,
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(PaperColors.SurfaceAlt)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(state.updatePercent / 100f)
                                        .fillMaxHeight()
                                        .background(PaperColors.GradientStart)
                                )
                            }
                        }
                    } else {
                        ListRow(
                            "下载并安装 v${release.version}",
                            "App 内下载 · 下完直接调系统安装器",
                            trailing = "↓",
                        ) { scope.launch { state.downloadUpdate() } }
                    }
                    val saved = state.updateSavedApk ?: state.cachedUpdateApk()
                    if (saved != null && !state.updateDownloading) {
                        Divider()
                        // 授权页回来点这里：不用重下，安装包还在
                        ListRow("安装已下载的 v${release.version}", "已经下好了，就差点一下") {
                            state.installApk(saved)
                        }
                    }
                }
            }
            Divider()
            ListRow("GitHub 仓库", UpdateChecker.REPO) { state.openUrl(UpdateChecker.REPO_URL) }
        }
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item {
        CardSection(title = "关于") {
            Text(
                "除课标题库外，题目全部由本 App 程序生成，或按知识点自编" +
                    "（内置 ${state.bankSize} 道选择 / 判断题）。App 自身内容不抓取、不收录任何教辅、" +
                    "题库或教材原文；外部练习网站只借鉴题型规则，" +
                    "不打包其原始 PDF / 图片 / 原题。速查表只收基础事实与公式，" +
                    "练习纸版式参考 namishu/printables（MIT，声明随包放在 assets/licenses）。",
                fontSize = PaperDimens.BodySp,
                color = PaperColors.OnSurface,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(10.dp))
            // CC BY-NC 4.0 要求署名，这一段不能删；商业化前要先删 assets/taxonomy
            Text(
                "「知识点填空」「知识点选择」的题目来自开源题库 cn-math-taxonomy" +
                    "（xiaoyang111222/cn-math-taxonomy），许可 CC BY-NC 4.0：" +
                    "允许署名使用、不允许商业使用。这是 App 里唯一一批第三方题库内容" +
                    "（含部分课文配套题），已裁剪为 1–9 年级五科，并剔除依赖配图的题。" +
                    "完整许可文本见 assets/licenses/CN_MATH_TAXONOMY_LICENSE.txt。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "v${state.versionName} · 检查更新是唯一需要联网的动作，出卷、排版、打印全程离线可用。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
            )
        }
    }
}

/**
 * 「内置内容」总览卡。
 *
 * 用户问过「内置了什么要在 App 里有说明」—— 与其写一段会过期的描述，
 * 不如把每一项的条数都从数据源实时算出来，再加一句人话解释这批内容是什么、来源边界在哪。
 */
@Composable
private fun ContentSummaryCard(bankSize: Int) {
    val entries = ContentCatalog.entries(bankSize)
    val (typeCount, sheetCount, wordCount) = ContentCatalog.headline(bankSize)

    CardSection(title = "内置内容", hint = "数字实时统计，随版本自动更新，不是写死的") {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HeadStat(typeCount, "出卷题型", Modifier.weight(1f))
            HeadStat(sheetCount, "可打印纸张", Modifier.weight(1f))
            HeadStat(wordCount, "内置字词音标", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        entries.forEachIndexed { i, e ->
            if (i > 0) Divider()
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.label,
                        fontSize = PaperDimens.BodySp,
                        fontWeight = FontWeight.Medium,
                        color = PaperColors.OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    StatChip(e.size)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    e.note,
                    fontSize = PaperDimens.TinySp,
                    color = PaperColors.OnSurfaceVariant,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun HeadStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PaperColors.PrimaryContainer)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PaperColors.Primary)
        Text(label, fontSize = 10.5.sp, color = PaperColors.OnSurfaceVariant, maxLines = 1)
    }
}

// ------------------------------------------------------------------ 试卷库

// ------------------------------------------------------------------ 课本

/**
 * 课本页：两个视图。
 *
 * 「按单元」用课标题库里每个知识点的章节字段聚合出教材单元，
 * 点某个单元的「出卷」就只考这一单元 —— 孩子学到哪一课就练哪一课。
 * 「教材原文」只是链接清单：那个仓库没有 LICENSE、教材著作权属出版社，
 * App 不持有也不转发任何一页 PDF，点了用浏览器去原站看。
 */

@Composable
private fun TextbookScreen(state: AppState, scope: CoroutineScope) {
    var mode by remember { mutableStateOf(0) }
    var g by remember { mutableStateOf(state.grade) }
    var sub by remember { mutableStateOf(state.subject) }

    // 换年级后当前科目可能这一年不开课（物理八年级才有），自动落到开课的科目
    LaunchedEffect(g) {
        if (!sub.availableFor(g)) {
            sub = Subject.entries.firstOrNull { it.availableFor(g) } ?: Subject.MATH
        }
    }

    val units = remember(g, sub) { CurriculumBank.units(sub, g) }
    val counts = remember(sub) { CurriculumBank.countsByTopic(sub) }
    val books = remember(g, sub) { TextbookCatalog.books(g, sub) }

    CardSection {
        Text(
            "单元目录直接取自课标题库的章节划分，所以「单元 → 知识点 → 题目」始终对得上："
                + "点某个单元的「出卷」，出来的卷子只考这一单元。",
            fontSize = PaperDimens.BodySp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Segmented(listOf("按单元出卷", "教材原文"), mode, onSelect = { mode = it })
    }

    Spacer(Modifier.height(PaperDimens.Gap))
    SectionLabel("01", "年级与科目", "${g} 年级 · ${sub.label}")
    Spacer(Modifier.height(PaperDimens.GapSmall))
    CompactChipGrid(
        items = (1..9).toList(),
        perRow = 5,
        label = { GradeProfiles.gradeLabel(it) },
        selected = { it == g },
        onClick = { g = it },
    )
    Spacer(Modifier.height(PaperDimens.GapSmall))
    CompactChipGrid(
        items = Subject.entries.toList(),
        perRow = 5,
        label = { it.label },
        selected = { it == sub },
        enabled = { it.availableFor(g) },
        onClick = { sub = it },
    )

    Spacer(Modifier.height(PaperDimens.Gap))
    if (mode == 0) {
        UnitList(state, scope, g, sub, units, counts)
    } else {
        BookList(state, g, sub, books)
    }
}

@Composable
private fun UnitList(
    state: AppState,
    scope: CoroutineScope,
    grade: Int,
    subject: Subject,
    units: List<com.papermill.app.domain.content.CurricUnit>,
    counts: Map<String, Pair<Int, Int>>,
) {
    if (units.isEmpty()) {
        SectionLabel("02", "单元目录", "暂无")
        Spacer(Modifier.height(8.dp))
        Text(
            "${GradeProfiles.gradeLabel(grade)}${subject.label}还没有配套的单元划分数据，"
                + "可换其它科目或年级看看。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 17.sp,
        )
        return
    }

    listOf("上" to "上册", "下" to "下册").forEach { (key, label) ->
        val group = units.filter { it.semester == key }
        if (group.isEmpty()) return@forEach
        SectionLabel("02", label, "${group.size} 个单元")
        Spacer(Modifier.height(8.dp))
        group.forEach { unit ->
            var blank = 0
            var choice = 0
            unit.topics.forEach { t ->
                val c = counts[t.id]
                if (c != null) {
                    blank += c.first
                    choice += c.second
                }
            }
            UnitCard(unit, blank, choice) {
                scope.launch { state.generateUnitPaper(unit) }
            }
            Spacer(Modifier.height(PaperDimens.GapSmall))
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        "题量按单元里实际有多少题封顶（选择最多 20、填空最多 16），凑不够就少印几道，"
            + "不会拿别的单元的题来充数。",
        fontSize = PaperDimens.TinySp,
        color = PaperColors.OnSurfaceVariant,
        lineHeight = 15.sp,
    )
}

@Composable
private fun UnitCard(
    unit: com.papermill.app.domain.content.CurricUnit,
    blank: Int,
    choice: Int,
    onPaper: () -> Unit,
) {
    val total = blank + choice
    CardSection {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                unit.name,
                fontSize = PaperDimens.BodySp,
                fontWeight = FontWeight.Bold,
                color = PaperColors.OnSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            Spacer(Modifier.width(10.dp))
            MiniAction(if (total > 0) "出卷" else "暂无题", enabled = total > 0, onClick = onPaper)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatChip("${unit.topics.size} 个知识点")
            Spacer(Modifier.width(6.dp))
            StatChip("$total 道题")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            unit.topics.joinToString(" · ") { it.name },
            fontSize = PaperDimens.TinySp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 15.sp,
            maxLines = 2,
            style = TextStyle(lineBreak = LineBreak.Paragraph),
        )
    }
}

@Composable
private fun BookList(
    state: AppState,
    grade: Int,
    subject: Subject,
    books: List<com.papermill.app.domain.content.TextbookEntry>,
) {
    SectionLabel("02", "教材原文", "${TextbookCatalog.size} 册 · 只放链接")
    Spacer(Modifier.height(8.dp))
    CardSection {
        Text(
            "TapXWorld/ChinaTextbook 收录了人教 / 统编版的全部教材扫描件。"
                + "那个仓库没有开源许可、教材版权属于各出版社，所以本 App 只存链接："
                + "点开用浏览器去原仓库看或下载，App 不分发也不缓存任何一页教材。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 17.sp,
        )
    }
    Spacer(Modifier.height(PaperDimens.GapSmall))

    if (books.isEmpty()) {
        CardSection {
            Text(
                "${GradeProfiles.gradeLabel(grade)}${subject.label}没有收录教材（"
                    + "英语从三年级起、物理从八年级起、化学只有九年级）。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 17.sp,
            )
        }
        return
    }

    CardSection {
        books.forEachIndexed { i, b ->
            if (i > 0) Divider()
            ListRow(
                title = b.title,
                subtitle = "${b.termLabel} · ${b.sizeMb} MB · ${b.publisher}"
                    + if (b.parts > 1) " · 分 ${b.parts} 段需合并" else "",
                trailing = "打开 ›",
            ) { state.openUrl(b.url) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "教材是整本扫描件，不是练习纸。要练某一课，用左边「按单元出卷」。",
        fontSize = PaperDimens.TinySp,
        color = PaperColors.OnSurfaceVariant,
        lineHeight = 15.sp,
    )
}

// ------------------------------------------------------------------ 本地 PDF

/**
 * 打印用户自己选的 PDF。
 *
 * 这个页面存在的意义就是「版权干净地满足打印需求」：
 * 老师发的卷子、家长群里的 PDF、自己下的教材 —— 都是用户自己的副本，
 * App 只负责把它交给系统打印框架，不复制、不内置、不外传。
 */
@Composable
private fun LocalPdfScreen(state: AppState, onPick: () -> Unit) {
    val doc = state.localPdf
    if (doc == null) {
        CardSection(title = "选一个 PDF") {
            Text(
                "从手机里挑一个 PDF 直接打印：老师发的卷子、家长群的文件、自己下载的材料。",
                fontSize = PaperDimens.BodySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("选择 PDF", onClick = onPick)
        }
        return
    }

    CardSection {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    doc.name,
                    fontSize = PaperDimens.BodySp,
                    fontWeight = FontWeight.Bold,
                    color = PaperColors.OnSurface,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    StatChip("${doc.pages} 页")
                    Spacer(Modifier.width(6.dp))
                    StatChip(if (doc.landscape) "横版" else "竖版")
                }
            }
        }
    }

    Spacer(Modifier.height(PaperDimens.Gap))
    if (doc.preview != null) {
        CardSection(hint = "首页预览 · 尺寸按屏幕缩小，打印按原尺寸") {
            Image(
                bitmap = doc.preview,
                contentDescription = "PDF 首页预览",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, PaperColors.SurfaceAlt, RoundedCornerShape(10.dp)),
                contentScale = ContentScale.FillWidth,
            )
        }
    }

    Spacer(Modifier.height(PaperDimens.Gap))
    CardSection(title = "说明") {
        Text(
            "这个文件只在手机的临时目录里过一手，App 不上传、不内置、不外传；" +
                "退出后随时可被系统清理。要换成别的文件，点底部「换一个文件」。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 17.sp,
        )
    }
}

/**
 * 试卷库（v0.18.0 从「更多」里的子页面提升为独立页签）。
 *
 * 每张卷子给三个动作，对应三种真实场景：
 *  - 打开：原样再看一遍 / 再打印一次（题目就是当时那批）；
 *  - 改参数：拿它当模板再出一张新的（改了题量、加了题型才有意义）；
 *  - 批改：孩子做完了，照着一题一题把错号点出来 → 进错题本。
 * 第三个是这次新加的：原来要「进预览 → 展开题目清单 → 逐题勾 → 点加入」四步，
 * 现在一点就开面板。
 */
private fun LazyListScope.historyScreenItems(
    state: AppState,
    scope: CoroutineScope,
    onImport: () -> Unit,
) {
    item {
        CardSection(title = "备份", hint = "换手机 / 重装前先用它") {
            Row(horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall)) {
                SecondaryButton("分享备份", modifier = Modifier.weight(1f)) { state.shareBackup() }
                SecondaryButton("从备份恢复", modifier = Modifier.weight(1f)) { onImport() }
            }
        }
    }

    if (state.savedPapers.isEmpty()) {
        item { Spacer(Modifier.height(PaperDimens.Gap)) }
        item {
            CardSection(title = "还没有生成过卷子") {
                Text(
                    "回「出卷」挑一张卷子，生成后会自动存到这里。",
                    fontSize = PaperDimens.BodySp,
                    color = PaperColors.OnSurfaceVariant,
                )
            }
        }
        return
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item { SectionLabel("01", "最近的卷子", "${state.savedPapers.size} 张 · 题目快照永久保留") }

    state.savedPapers.forEach { p ->
        item(key = "paper-${p.id}") {
            Spacer(Modifier.height(PaperDimens.GapSmall))
            PaperHistoryCard(
                paper = p,
                busy = state.busy,
                onOpen = { scope.launch { state.reopen(p) } },
                onEdit = { state.editSaved(p) },
                onGrade = { state.startGrading(p) },
            )
        }
    }
}

@Composable
private fun PaperHistoryCard(
    paper: SavedPaper,
    busy: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onGrade: () -> Unit,
) {
    val subjectLabel = runCatching { Subject.valueOf(paper.subject).label }.getOrDefault(paper.subject)
    CardSection {
        Text(
            paper.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = PaperColors.OnSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${GradeProfiles.gradeLabel(paper.grade)} · $subjectLabel · ${paper.questions.size} 题 · " +
                stampOf(paper.createdAt),
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction("打开", enabled = !busy) { onOpen() }
            MiniAction("改参数", enabled = !busy) { onEdit() }
            MiniAction("批改记错题", enabled = !busy) { onGrade() }
        }
    }
}

/** 「MM-dd HH:mm」：卷子列表只需要看出是哪天哪会儿出的，不印年份 */
private fun stampOf(ts: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(ts))

// ------------------------------------------------------------------ 打印帮助

@Composable
private fun HelpScreen(state: AppState) {
    CardSection(title = "① 在家打印（手机和打印机同一 WiFi）") {
        HelpLine("1", "第一次：手机装「奔图打印」App，或在 系统设置 → 打印 里启用 Mopria 打印服务。")
        HelpLine("2", "在 App 里点底部「系统打印」，打印框中选择奔图 M1。")
        HelpLine("3", "纸张选 ISO A4，份数按需，点打印。")
        Text(
            "原理：奔图 M1 支持 AirPrint / Mopria，Android 自带打印框架就能找到它，不需要奔图 SDK。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 17.sp,
        )
    }

    Spacer(Modifier.height(PaperDimens.Gap))
    CardSection(
        title = "系统打印服务状态",
        hint = if (state.hasPrintService) "已检测到打印服务" else "未检测到，可能需要在系统设置里启用",
    ) {
        SecondaryButton("打开系统打印设置") { state.openPrintSettings() }
    }

    Spacer(Modifier.height(PaperDimens.Gap))
    CardSection(title = "② 在外面 / 不在同一网络时远程打印") {
        HelpLine("1", "点预览页底部「分享」，把 PDF 发到微信（发给「文件传输助手」即可）。")
        HelpLine("2", "微信里长按该 PDF → 用其他应用打开 → 选「奔图云打印」小程序。")
        HelpLine("3", "小程序里选已绑定的 M1，确认打印。")
        Text(
            "说明：奔图云打印没有公开 API，开放平台 SDK 需企业资质审核，本 App 不能直连云端；" +
                "远程打印这一步需在小程序里完成，App 只负责把排版好的 PDF 交出去。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun HelpLine(no: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(no, fontSize = PaperDimens.LabelSp, color = PaperColors.Primary, fontWeight = FontWeight.SemiBold)
        Text("  $text", fontSize = PaperDimens.LabelSp, color = PaperColors.OnSurface, lineHeight = 18.sp)
    }
}

// ==================================================================
// v0.19.0 学生档案 / 学期 / 自编题
// ==================================================================

/**
 * 首页最上面那条「给谁出卷」。
 *
 * 为什么放在 Hero 之前：Hero 说的是「这张卷多少题、几页纸」，
 * 而「这是谁做的卷」是更靠前的问题 —— 顺序反了，用户得先读一堆数字才知道给谁。
 */
@Composable
private fun StudentStrip(state: AppState, scope: CoroutineScope) {
    CardSection(
        title = "给谁出卷",
        hint = "点名字 = 换到他的年级、学期和科目，并直接出卷",
    ) {
        if (state.students.isEmpty()) {
            Text(
                "先建一份学生档案：填姓名、年级、这学期是上册还是下册。" +
                    "以后点一下名字，年级和学期自动就位，不用每次重选。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton("＋ 添加学生") { state.screen = Screen.STUDENTS }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.students.forEach { s ->
                    StudentPill(
                        student = s,
                        active = s.id == state.activeStudentId,
                        onClick = { scope.launch { state.useStudent(s.id) } },
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "当前：" + (state.activeStudent?.let { "${it.name} · ${it.gradeLabel}" }
                        ?: "未选学生") + " · ${state.totalQuestions} 题",
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                MiniAction("管理") { state.screen = Screen.STUDENTS }
            }
        }
    }
}

/** 一个学生胶囊：头像字 + 名字 + 「三上」这样的短标签 */
@Composable
private fun StudentPill(student: Student, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(92.dp)
            .clip(shape)
            .background(if (active) PaperColors.PrimaryContainer else PaperColors.SurfaceAlt)
            .then(
                if (active) {
                    Modifier.border(1.2.dp, PaperColors.Primary.copy(alpha = 0.45f), shape)
                } else Modifier
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(30.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(50))
                .background(if (active) PaperColors.Primary else PaperColors.Muted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                student.initial,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            student.name,
            fontSize = PaperDimens.LabelSp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) PaperColors.Primary else PaperColors.OnSurface,
            maxLines = 1,
        )
        Text(
            student.shortLabel,
            fontSize = PaperDimens.TinySp,
            color = PaperColors.OnSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 学生档案页：增删改 + 一键出卷 */
@Composable
private fun StudentsScreen(state: AppState, scope: CoroutineScope) {
    var editing by remember { mutableStateOf<Student?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Student?>(null) }

    CardSection(title = "学生档案", hint = "数据只存在这台手机，不上传") {
        Text(
            "一个孩子一份档案：姓名、年级、这学期上册还是下册、常做的科目。" +
                "点「出卷」就按他的配置直接生成一张卷子，卷头还会印上名字。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton("＋ 添加学生") { creating = true }
    }

    if (state.students.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        CardSection {
            Text(
                "还没有学生档案。",
                fontSize = PaperDimens.BodySp,
                fontWeight = FontWeight.SemiBold,
                color = PaperColors.OnSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "不建也能用：参数页照样能选年级出卷，只是每次都要重选一遍。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
            )
        }
    }

    state.students.forEach { s ->
        Spacer(Modifier.height(12.dp))
        val active = s.id == state.activeStudentId
        CardSection(
            title = if (active) "${s.name}（当前）" else s.name,
            hint = "${s.gradeLabel} · ${s.gender.label} · ${s.subjectLabel}" +
                if (s.note.isNotBlank()) " · ${s.note}" else "",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAction("出卷") { scope.launch { state.useStudent(s.id) } }
                MiniAction("编辑") { editing = s }
                MiniAction("删除", color = PaperColors.Error) { deleting = s }
            }
        }
    }

    if (creating || editing != null) {
        StudentDialog(
            state = state,
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }
    deleting?.let { s ->
        ConfirmDialog(
            title = "删除「${s.name}」的档案？",
            message = "档案和他的自编题会一起删掉，已经生成的卷子和错题不受影响。",
            danger = true,
            onCancel = { deleting = null },
            onConfirm = {
                state.deleteStudent(s.id)
                deleting = null
            },
        )
    }
}

/** 新建 / 编辑学生：姓名、性别、年级、学期、科目 */
@Composable
private fun StudentDialog(state: AppState, initial: Student?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var grade by remember { mutableStateOf(initial?.grade ?: state.grade) }
    var term by remember { mutableStateOf(initial?.term ?: state.term) }
    var gender by remember { mutableStateOf(initial?.gender ?: Gender.UNSET) }
    var subjects by remember {
        val src = initial?.subjects ?: listOf(state.subject)
        val g = initial?.grade ?: state.grade
        mutableStateOf(src.filter { it.availableFor(g) }.ifEmpty { listOf(Subject.MATH) })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(PaperColors.Surface)
                .padding(18.dp)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8f).dp),
        ) {
            Text(
                if (initial == null) "添加学生" else "编辑学生",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PaperColors.OnSurface,
            )
            Spacer(Modifier.height(10.dp))
            // 中间滚、按钮钉底：小屏手机 + 展开的科目网格会把「保存」顶出屏幕
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Field3(
                value = name,
                onValueChange = { if (it.length <= 12) name = it },
                label = "姓名",
                placeholder = "例如 小明",
            )
            Spacer(Modifier.height(12.dp))
            Text("性别", fontSize = PaperDimens.CaptionSp, color = PaperColors.OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Gender.MALE, Gender.FEMALE).forEach { g ->
                    ChoiceChip(g.label, gender == g) { gender = if (gender == g) Gender.UNSET else g }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("年级", fontSize = PaperDimens.CaptionSp, color = PaperColors.OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            CompactChipGrid(
                items = (1..9).toList(),
                perRow = 5,
                label = { GradeProfiles.gradeLabel(it) },
                selected = { it == grade },
                onClick = { g ->
                    grade = g
                    // 换年级后原来的科目可能没开课（英语三年级才有、物理八年级才有）
                    subjects = subjects.filter { it.availableFor(g) }.ifEmpty { listOf(Subject.MATH) }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "这学期",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("上册", term == Term.FIRST) { term = Term.FIRST }
                ChoiceChip("下册", term == Term.SECOND) { term = Term.SECOND }
                ChoiceChip("全学年", term == Term.BOTH) { term = Term.BOTH }
            }
            Spacer(Modifier.height(12.dp))
            Text("常做的科目（可多选）", fontSize = PaperDimens.CaptionSp, color = PaperColors.OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            CompactChipGrid(
                items = Subject.entries.toList(),
                perRow = 5,
                label = { it.label },
                selected = { it in subjects },
                enabled = { it.availableFor(grade) },
                onClick = { s ->
                    subjects = if (s in subjects) {
                        (subjects - s).ifEmpty { listOf(s) }
                    } else subjects + s
                },
            )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("取消", modifier = Modifier.weight(1f)) { onDismiss() }
                PrimaryButton(
                    "保存",
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1.2f),
                ) {
                    state.upsertStudent(
                        Student(
                            id = initial?.id ?: state.newStudentId(),
                            name = name.trim(),
                            grade = grade,
                            term = term,
                            gender = gender,
                            subjects = subjects,
                            note = initial?.note.orEmpty(),
                        )
                    )
                    onDismiss()
                }
            }
        }
    }
}

/** 通用确认框：删除这类不可撤销的动作都过一遍 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    danger: Boolean = false,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PaperColors.Surface)
                .padding(18.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PaperColors.OnSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("取消", modifier = Modifier.weight(1f)) { onCancel() }
                if (danger) {
                    Box(Modifier.weight(1f)) {
                        DangerButton("删除", onConfirm)
                    }
                } else {
                    PrimaryButton("确定", modifier = Modifier.weight(1f)) { onConfirm() }
                }
            }
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(PaperDimens.ActionBtnH)
            .clip(RoundedCornerShape(16.dp))
            .background(PaperColors.ErrorContainer)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = PaperDimens.BodySp, fontWeight = FontWeight.Bold, color = PaperColors.Error)
    }
}

/**
 * 多行输入框。
 *
 * 没有复用 [Field3]：它的 decorationBox 是「一行、垂直居中」的布局，
 * 粘 20 行词表进去会变成一个居中滚动的小窗口，看不出自己写了多少行。
 */
@Composable
private fun MultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    height: Dp,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontSize = PaperDimens.BodySp, color = PaperColors.OnSurface),
        cursorBrush = SolidColor(PaperColors.Primary),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(PaperColors.SurfaceAlt)
            .padding(12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontSize = PaperDimens.BodySp,
                        color = PaperColors.Muted,
                        lineHeight = 20.sp,
                    )
                }
                inner()
            }
        },
    )
}

/** 参数页里的「自编题」卡片：自己的词表 / 算式 / 题目 */
@Composable
private fun CustomSheetsCard(state: AppState) {
    var editing by remember { mutableStateOf<CustomSheet?>(null) }
    var creating by remember { mutableStateOf(false) }

    CardSection(
        title = "自编题",
        hint = "自己的词表 / 算式 / 题目，原样排进卷子",
    ) {
        Text(
            "题库再全也覆盖不到「老师今天发的 20 个词」。这里可以把自己的内容粘进来，" +
                "选一种排法就变成一道大题。同一段里重复的行会自动去掉。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(10.dp))

        if (state.visibleCustomSheets.isEmpty()) {
            Text(
                "还没有自编题。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.Muted,
            )
        } else {
            state.visibleCustomSheets.forEach { sheet ->
                val n = CustomSheet.validCount(sheet)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            sheet.title.ifBlank { sheet.mode.label },
                            fontSize = PaperDimens.BodySp,
                            fontWeight = FontWeight.Medium,
                            color = PaperColors.OnSurface,
                        )
                        Text(
                            "${sheet.mode.label} · 会印 $n 道" +
                                if (sheet.studentId.isNotEmpty()) " · 仅这位学生" else " · 共用",
                            fontSize = PaperDimens.TinySp,
                            color = PaperColors.OnSurfaceVariant,
                        )
                    }
                    MiniAction("改") { editing = sheet }
                    Spacer(Modifier.width(6.dp))
                    MiniAction("删", color = PaperColors.Error) { state.deleteCustomSheet(sheet.id) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SecondaryButton("＋ 添加自编题") { creating = true }
    }

    if (creating || editing != null) {
        CustomSheetDialog(
            state = state,
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }
}

/** 自编题编辑框：内容 + 排法（模式 / 栏数 / 答题线 / 打乱 / 只取前 N 条） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomSheetDialog(state: AppState, initial: CustomSheet?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var mode by remember { mutableStateOf(initial?.mode ?: CustomMode.CALC) }
    var raw by remember { mutableStateOf(initial?.raw.orEmpty()) }
    var columns by remember { mutableStateOf(initial?.columns ?: 2) }
    var answerLines by remember { mutableStateOf(initial?.answerLines ?: 1) }
    var shuffle by remember { mutableStateOf(initial?.shuffle ?: true) }
    var limit by remember { mutableStateOf(initial?.count ?: 0) }
    var studentId by remember { mutableStateOf(initial?.studentId ?: "") }

    // 实时算「这批内容最终会印几道」——用户最关心的就是这个数字
    val draft = CustomSheet(
        id = initial?.id ?: "tmp",
        studentId = studentId,
        title = title,
        mode = mode,
        raw = raw,
        columns = columns,
        answerLines = answerLines,
        shuffle = shuffle,
        count = limit,
    )
    val valid = CustomSheet.validCount(draft)
    val totalLines = draft.rawLines().size
    val dup = totalLines - draft.uniqueLines().size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(22.dp))
                .background(PaperColors.Surface)
                .padding(18.dp)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.78f).dp),
        ) {
            Text(
                if (initial == null) "添加自编题" else "编辑自编题",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PaperColors.OnSurface,
            )
            Spacer(Modifier.height(10.dp))

            // 只有中间这块滚；「取消 / 保存」钉在弹窗底部 ——
            // 十个设置项排下来必然超过一屏，按钮跟着内容滚走等于让人找不到出口。
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Field3(
                value = title,
                onValueChange = { if (it.length <= 16) title = it },
                label = "大题名称（可空）",
                placeholder = "例如 本周生词默写",
            )
            Spacer(Modifier.height(12.dp))

            Text("怎么排", fontSize = PaperDimens.CaptionSp, color = PaperColors.OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomMode.entries.forEach { m ->
                    ChoiceChip(m.label, mode == m) {
                        mode = m
                        // 换模式时把答题线 / 栏数调到该模式的合理默认值：
                        // 挖空和整句题干挤进半栏会折成竖条，只能整行
                        answerLines = m.answerLines
                        columns = if (m == CustomMode.PLAIN || m == CustomMode.BLANK) 1 else 2
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                mode.hint,
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(10.dp))

            Text(
                "内容（一行一道）",
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            MultilineField(
                value = raw,
                onValueChange = { raw = it },
                placeholder = mode.sample,
                height = 150.dp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    totalLines == 0 -> "粘进内容后这里会显示能出几道题"
                    valid == 0 -> "这 ${totalLines} 行都没能变成题：检查一下格式（见上面的说明）"
                    dup > 0 -> "${totalLines} 行，去掉 $dup 条重复 → 会印 $valid 道"
                    limit > 0 && valid < totalLines -> "${totalLines} 行，只取前 $valid 道"
                    else -> "${totalLines} 行，会印 $valid 道"
                },
                fontSize = PaperDimens.TinySp,
                color = if (valid == 0 && totalLines > 0) PaperColors.Error else PaperColors.OnSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            SettingRow("一行几栏") {
                listOf(1, 2, 3, 4).forEach { c ->
                    ChoiceChip("$c 栏", columns == c) { columns = c }
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingRow("答题线") {
                ChoiceChip("不留", answerLines == 0) { answerLines = 0 }
                ChoiceChip("1 条", answerLines == 1) { answerLines = 1 }
                ChoiceChip("2 条", answerLines == 2) { answerLines = 2 }
                ChoiceChip("3 条", answerLines == 3) { answerLines = 3 }
            }
            Spacer(Modifier.height(8.dp))
            SettingRow("顺序") {
                ChoiceChip("打乱", shuffle) { shuffle = true }
                ChoiceChip("照原顺序", !shuffle) { shuffle = false }
            }
            Spacer(Modifier.height(8.dp))
            SettingRow("取几条") {
                ChoiceChip("全部", limit == 0) { limit = 0 }
                listOf(10, 20, 30).forEach { n ->
                    ChoiceChip("$n", limit == n) { limit = n }
                }
            }
            if (state.activeStudent != null) {
                Spacer(Modifier.height(8.dp))
                SettingRow("给谁用") {
                    ChoiceChip("共用", studentId.isEmpty()) { studentId = "" }
                    ChoiceChip("只给${state.activeStudent!!.name}",
                        studentId == state.activeStudent!!.id) {
                        studentId = state.activeStudent!!.id
                    }
                }
            }

            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("取消", modifier = Modifier.weight(1f)) { onDismiss() }
                PrimaryButton(
                    "保存",
                    enabled = raw.isNotBlank() && valid > 0,
                    modifier = Modifier.weight(1.2f),
                ) {
                    state.upsertCustomSheet(
                        draft.copy(id = initial?.id ?: state.newCustomId())
                    )
                    onDismiss()
                }
            }
        }
    }
}
