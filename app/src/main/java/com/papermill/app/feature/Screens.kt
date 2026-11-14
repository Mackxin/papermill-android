// HorizontalPager 在 Compose 1.6 仍标记为 ExperimentalFoundationApi；
// 这里只在页签切换和预览翻页两处使用，行为稳定，文件级 opt-in 即可。
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.papermill.app.feature

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papermill.app.core.design.PaperColors
import com.papermill.app.core.design.PaperDimens
import com.papermill.app.core.update.UpdateChecker
import com.papermill.app.domain.content.ContentCatalog
import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.GeneratorRegistry
import com.papermill.app.domain.generate.GradeProfiles
import com.papermill.app.domain.generate.PageOrientation
import com.papermill.app.domain.generate.PaperTemplates
import com.papermill.app.domain.generate.Subject
import com.papermill.app.domain.reference.PracticeData
import com.papermill.app.domain.reference.ReferenceData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 页面框架 v2。
 *
 * 与 v1 最大的区别：**主操作不再放在滚动内容的末尾**。
 * 结构是「顶栏 → 可滚动内容 → 固定操作条 → 固定底部导航」，
 * 用户随便滚到哪，出卷 / 打印永远在拇指够得到的位置。
 */
/** 底部 4 个页签对应的页面；其余 Screen 是压在页签之上、带返回键的子页面 */
private val TAB_SCREENS = setOf(Screen.HOME, Screen.REFERENCE, Screen.MISTAKES, Screen.MORE)
private val TAB_ORDER = listOf(NavTab.HOME, NavTab.REFERENCE, NavTab.MISTAKES, NavTab.MORE)

/**
 * 底部玻璃 dock 占位高度（外 padding 8 + 内 padding 6 + 胶囊 42 + 6 + 8 = 70dp）。
 *
 * dock 现在是浮层，不占布局位；内容区要按这个高度留出底部内边距，
 * 否则滚到最底时最后一行会被 dock 压住。
 */
private val DOCK_SPACE = 70.dp

/** 页内可滚动内容需要额外留出的底部空间：操作条 + dock + 呼吸余量 */
private val BOTTOM_SAFE = DOCK_SPACE + PaperDimens.ActionBarH + 16.dp

@Composable
fun PaperMillRoot(state: AppState) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.importBackup(uri)
    }

    // 返回键：先 App 内逐层退（预览 → 来时的页面 → 首页），最后在首页再按一次才退出。
    // 旧版没接管返回键，在预览页按一下就直接回桌面，卷子和浏览位置全丢。
    BackHandler {
        if (!state.back()) {
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
        }
    }

    // 提示条自动收起：切页后不再挂着上一条「已生成 N 题」的旧消息
    LaunchedEffect(state.toast) {
        if (state.toast.isNotEmpty()) {
            delay(6000)
            state.clearToast()
        }
    }

    // 四个页签做成可左右滑动的 pager，滑动落页后同步 state
    val pager = rememberPagerState(
        initialPage = TAB_ORDER.indexOf(state.navTab).coerceAtLeast(0),
    ) { TAB_ORDER.size }

    // ============================================================
    // 高亮与 pager 的三段联动（用 snapshotFlow 而不是 LaunchedEffect(key)）
    // ============================================================
    // 踩过的坑：把 key 设成 pager.currentPage 时，带动画的跳页本身会让 currentPage
    // 中途变化 → 触发 goTab → screen 变 → 那个 effect 被重启 → 正在跑的
    // animateScrollToPage 被协程取消 → pager 卡在两页中间不动。
    // snapshotFlow 挂在 LaunchedEffect(Unit) 里，永不重启，也就不会互相取消。

    // ① 手指拖动时高亮立刻跟随（滑过半页就切），这就是用户要的「跟着一起动」
    LaunchedEffect(Unit) {
        snapshotFlow { pager.currentPage }.collect { page ->
            if (pager.isScrollInProgress && state.screen in TAB_SCREENS) {
                val target = TAB_ORDER[page]
                if (state.navTab != target) state.goTab(target)
            }
        }
    }

    // ② 落页后校正一次：程序动画途中被 ① 改掉的 navTab，在停下来时对齐到真实页
    LaunchedEffect(Unit) {
        snapshotFlow { pager.settledPage }.collect { page ->
            if (state.screen in TAB_SCREENS) {
                val target = TAB_ORDER[page]
                if (state.navTab != target) state.goTab(target)
            }
        }
    }

    // ③ 点 dock / 从子页面回来时把 pager 滑过去。
    // 手指正在滑的时候不发指令，否则松手那刻 settle 还没走完就被抢走。
    LaunchedEffect(Unit) {
        snapshotFlow { state.navTab }.collect { tab ->
            if (state.screen !in TAB_SCREENS || pager.isScrollInProgress) return@collect
            val idx = TAB_ORDER.indexOf(tab)
            if (idx >= 0 && pager.currentPage != idx) {
                pager.animateScrollToPage(
                    page = idx,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    // ④ 「更多」页的数字要随时反映最新状态：每次切回本页都重读一次。
    // 读盘在 IO 线程，不会卡帧；不能挂在 LazyColumn 的 item 里，那会被回收后反复重跑。
    LaunchedEffect(state.screen) {
        if (state.screen == Screen.MORE) state.refreshListsAsync()
    }

    val (title, subtitle) = screenTitle(state)
    // 页签页不显示返回箭头（现代 App 惯例），子页面才显示
    val onBack: (() -> Unit)? = if (state.screen in TAB_SCREENS) null else ({ state.back() })

    Box(Modifier.fillMaxSize().background(PaperColors.Background)) {
        // 内容层一路铺到屏幕底部：滚动内容会从 dock 背后穿过去，玻璃质感才透得出来
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                // 只有首页（无返回键）标题居中 + 主色；有返回键的页面居中会和圆形按钮撞
                centered = state.screen == Screen.HOME,
            )

            Box(Modifier.weight(1f)) {
                if (state.screen in TAB_SCREENS) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        // 预先组合相邻页：默认只组合当前页，滑到新页时才现场搭整页内容
                        // （工具页有 36 张卡），首帧就会卡一下；多挂一页换顺滑。
                        // Compose 1.6 里这个参数叫 beyondBoundsPageCount（1.7 才改名）。
                        beyondBoundsPageCount = 1,
                    ) { page ->
                        val tab = TAB_ORDER[page]
                        // 用 LazyColumn 而不是 Column + verticalScroll：
                        // 原来「打印纸」页一次性组合 36 张带 elevation 阴影的卡，
                        // 滑动时前后两页同时在场，首帧要摆上百个阴影节点 —— 这就是卡顿的主因。
                        // 改成按需组合后，屏幕上始终只有可见的那几行。
                        LazyColumn(
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
                                NavTab.MORE -> moreScreenItems(state, scope) {
                                    importLauncher.launch("*/*")
                                }
                            }
                            item { Spacer(Modifier.height(20.dp)) }
                        }
                    }
                } else {
                    val scroll = rememberScrollState()
                    LaunchedEffect(state.screen) { scroll.scrollTo(0) }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(horizontal = PaperDimens.ScreenPad)
                    ) {
                        Spacer(Modifier.height(2.dp))
                        when (state.screen) {
                            Screen.CREATE -> CreateScreen(state, scope)
                            Screen.PREVIEW -> PreviewScreen(state, scope, activity)
                            Screen.HISTORY -> HistoryScreen(state, scope) { importLauncher.launch("*/*") }
                            Screen.HELP -> HelpScreen(state)
                            else -> Unit
                        }
                        Spacer(Modifier.height(BOTTOM_SAFE))
                    }
                }
            }
        }

        // 浮层：提示条与操作条固定在 dock 上方，dock 本身贴底
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            ToastBar(state.toast) { state.clearToast() }

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

        FloatingDock(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { tab -> state.goTab(tab) }
    }
}

private fun screenTitle(state: AppState): Pair<String, String> = when (state.screen) {
    Screen.HOME -> "卷子" to "选年级科目，30 秒出一张能打印的卷子"
    Screen.CREATE -> "自由组合" to "${GradeProfiles.gradeLabel(state.grade)} · ${state.totalQuestions} 题 · 预计 ${state.estimatedPages} 页 ${state.orientation.label}"
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
    Screen.HISTORY -> "试卷库" to "只存题目快照，随时重开"
    Screen.HELP -> "打印帮助" to "在家打印与远程打印两种方式"
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
                    "${GradeProfiles.gradeLabel(state.grade)} · $subjectLabel",
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

    // 特色入口：自由组合。紧贴 Hero 下方，点整张卡进面板。
    // 这是本 App 区别于「点一下就出卷」的题库类 App 的核心 —— 题由你配，不由我们限死。
    item {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(Surface3.cardShadow, Surface3.shape)
                .clip(Surface3.shape)
                .background(PaperColors.Surface)
                .clickable { state.screen = Screen.CREATE }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "自由组合出题",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaperColors.OnSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    StatChip("特色", PaperColors.Accent, PaperColors.AccentContainer)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "${GeneratorRegistry.count} 种题型随便挑：智能配比、搜索、一键加题，题量自己定",
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                    lineHeight = 17.sp,
                )
            }
            Text("›", fontSize = 22.sp, color = PaperColors.Primary)
        }
    }

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
                    // 开了补满还是不满：是题库量不够，不是开关没生效
                    autoExtra > 0 -> Text(
                        "自动补满加了 $autoExtra 题，但题库不够了，最后一页只到 $fill% —— 再选一种题型就能补上。",
                        fontSize = PaperDimens.CaptionSp,
                        color = PaperColors.Accent,
                        lineHeight = 16.sp,
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
                hint = if (type.answerLines > 0) "需书写过程 · 自动单栏" else "客观题 · 按栏数排",
                onMinus = { state.setCount(type, -1) },
                onPlus = { state.setCount(type, 1) },
                onMinusMore = { state.setCount(type, -5) },
                onPlusMore = { state.setCount(type, 5) },
                onRemove = { state.removeType(type) },
            )
        }
    }

    // v0.6.0 题型库：搜索 + 按科目筛选，点「＋10」就能加进组合
    Spacer(Modifier.height(14.dp))
    SectionLabel("02", "题型库", "共 ${state.availableTypes.size} 种")
    Spacer(Modifier.height(8.dp))
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
                        } else if (type.answerLines > 0) {
                            "需书写过程 · 加入后自动单栏"
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
    Row(
        Modifier.fillMaxWidth().clickable { state.showAdvanced = !state.showAdvanced },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            SectionLabel("03", "卷面设置", if (state.showAdvanced) "收起" else "展开")
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
            SettingRow("姓名栏") {
                ChoiceChip("显示", state.showNameRow) { state.showNameRow = true }
                ChoiceChip("不显示", !state.showNameRow) { state.showNameRow = false }
            }
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
            "${state.columns} 栏 · ${state.orientation.shortLabel} · ${state.answerMode.label} · " +
                when (state.difficulty) { 1 -> "基础"; 3 -> "提高"; else -> "标准" } +
                " · " + if (state.autoFillPage) "自动补满整页" else "严格按题量",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            modifier = Modifier.padding(start = 30.dp),
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
                "最后一页还有空白，建议再加约 ${state.finalSuggestExtra} 题 —— 直接在下面点「补满这一页」即可。",
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
                            .padding(4.dp),
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
        } else {
            CardSection { Text("还没有生成内容", fontSize = PaperDimens.BodySp) }
        }
        Spacer(Modifier.height(10.dp))
        if (!isRef) {
            Text(
                "批改后到「题目清单」勾错题；也可以换题、删题、加题。",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
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
        CardSection(title = "题目清单", hint = "勾题进错题本 · 可单题换 / 删 / 加") {
            MiniAction("+ 在末尾加一题", enabled = !state.busy) {
                scope.launch { state.appendQuestion() }
            }
            Spacer(Modifier.height(8.dp))
            Divider()
            gp?.questions?.forEach { q ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = q.no in state.wrongNumbers,
                            onCheckedChange = { state.toggleWrong(q.no) },
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
                Divider()
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已勾 ${state.wrongNumbers.size} 题",
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                MiniAction("加入错题本") { state.addWrongToMistakes() }
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
            items = list,
            titleOf = { it.title },
            descOf = { it.subtitle },
            metaOf = { "A4 · 立即打印" },
            onClick = { sheet -> scope.launch { state.renderReference(sheet) } },
        )
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item { SectionLabel("02", "练习纸", "${PracticeData.sheets.size} 张 · 格子铺满整页") }
    item { Spacer(Modifier.height(PaperDimens.GapSmall)) }
    sheetRows(
        items = PracticeData.sheets,
        titleOf = { it.title },
        descOf = { it.subtitle },
        metaOf = { "1 页 ${it.orientation.shortLabel}" },
        onClick = { sheet -> scope.launch { state.renderPractice(sheet) } },
    )

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

/** 速查表 / 练习纸共用的 2 列卡片网格（Lazy 版：每行一个 item，滑到哪才组合哪几行） */
private fun <T> LazyListScope.sheetRows(
    items: List<T>,
    titleOf: (T) -> String,
    descOf: (T) -> String,
    metaOf: (T) -> String,
    onClick: (T) -> Unit,
) {
    items.chunked(2).forEach { row ->
        item {
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
        item {
            CardSection(
                title = "第 ${m.sourceNo} 题 · ${m.topic}",
                hint = "${GradeProfiles.gradeLabel(m.grade)} · ${m.type}",
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
                    "打印并批改后，到试卷预览的「题目清单」勾做错的题，就会进这里。",
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

private fun LazyListScope.moreScreenItems(state: AppState, scope: CoroutineScope, onImport: () -> Unit) {
    // 内置内容：数字全部当场从数据源算，不写死文案 —— 题库扩容后这里自动跟着变
    item { ContentSummaryCard(state.bankSize) }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item {
        CardSection(title = "外观") {
            ListRow(
                "半透明玻璃 tab 栏",
                if (state.dockGlass) {
                    "已开启：tab 栏半透明，滚动内容会从背后透出来"
                } else {
                    "已关闭：不透明实底（默认），文字最清楚"
                },
                trailing = if (state.dockGlass) "开" else "关",
            ) { state.updateDockGlass(!state.dockGlass) }
        }
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item {
        CardSection(title = "数据") {
            ListRow("试卷库", "${state.savedPapers.size} 张卷子") { state.screen = Screen.HISTORY }
            Divider()
            ListRow("分享备份文件", "导出 JSON，含试卷库与错题本") { state.shareBackup() }
            Divider()
            ListRow("从备份恢复", "选择之前导出的 JSON") { onImport() }
        }
    }

    item { Spacer(Modifier.height(PaperDimens.Gap)) }
    item {
        CardSection(title = "打印与更新") {
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
                if (release.apkUrl != null) {
                    Divider()
                    ListRow("下载新版 APK", release.version) { state.openUrl(release.apkUrl) }
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

@Composable
private fun HistoryScreen(state: AppState, scope: CoroutineScope, onImport: () -> Unit) {
    CardSection {
        Row(horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall)) {
            SecondaryButton("分享备份", modifier = Modifier.weight(1f)) { state.shareBackup() }
            SecondaryButton("从备份恢复", modifier = Modifier.weight(1f)) { onImport() }
        }
    }
    Spacer(Modifier.height(PaperDimens.Gap))
    state.savedPapers.forEach { p ->
        CardSection {
            ListRow(
                p.title,
                "${GradeProfiles.gradeLabel(p.grade)} · ${p.questions.size} 题 · ${p.counts.size} 种题型",
                trailing = "打开 ›",
            ) { scope.launch { state.reopen(p) } }
        }
        Spacer(Modifier.height(PaperDimens.GapSmall))
    }
    if (state.savedPapers.isEmpty()) {
        CardSection(title = "还没有生成过卷子") {
            Text("回「出卷」挑一张卷子，生成后会自动存到这里。", fontSize = PaperDimens.BodySp)
        }
    }
}

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
