package com.papermill.app.feature

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papermill.app.core.design.PaperColors
import com.papermill.app.core.design.PaperDimens
import com.papermill.app.core.update.UpdateChecker
import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.GradeProfiles
import com.papermill.app.domain.generate.PageOrientation
import com.papermill.app.domain.generate.PaperTemplates
import com.papermill.app.domain.generate.Subject
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
@Composable
fun PaperMillRoot(state: AppState) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.importBackup(uri)
    }
    val scroll = rememberScrollState()
    LaunchedEffect(state.screen) { scroll.scrollTo(0) }
    // 提示条自动收起：切页后不再挂着上一条「已生成 N 题」的旧消息
    LaunchedEffect(state.toast) {
        if (state.toast.isNotEmpty()) {
            delay(6000)
            state.clearToast()
        }
    }

    val (title, subtitle) = screenTitle(state)
    val onBack: (() -> Unit)? = when (state.screen) {
        Screen.CREATE -> {{ state.screen = Screen.HOME }}
        Screen.PREVIEW -> {{ state.screen = if (state.isReference) Screen.REFERENCE else Screen.CREATE }}
        Screen.HISTORY, Screen.HELP -> {{ state.screen = Screen.MORE }}
        else -> null
    }

    Column(Modifier.fillMaxSize().background(PaperColors.Background)) {
        AppTopBar(title = title, subtitle = subtitle, onBack = onBack)

        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = PaperDimens.ScreenPad)
            ) {
                Spacer(Modifier.height(4.dp))
                when (state.screen) {
                    Screen.HOME -> HomeScreen(state, scope)
                    Screen.CREATE -> CreateScreen(state, scope)
                    Screen.PREVIEW -> PreviewScreen(state, scope, activity)
                    Screen.REFERENCE -> ReferenceScreen(state, scope)
                    Screen.MISTAKES -> MistakesScreen(state)
                    Screen.MORE -> MoreScreen(state, scope) { importLauncher.launch("*/*") }
                    Screen.HISTORY -> HistoryScreen(state, scope) { importLauncher.launch("*/*") }
                    Screen.HELP -> HelpScreen(state)
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        ToastBar(state.toast) { state.clearToast() }

        when (state.screen) {
            Screen.HOME -> StickyBar {
                SecondaryButton(
                    "自定义题量",
                    modifier = Modifier.weight(1f),
                ) { state.screen = Screen.CREATE }
                PrimaryButton(
                    "直接出卷",
                    enabled = !state.busy && state.totalQuestions > 0,
                    modifier = Modifier.weight(1.2f),
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

        BottomNav(state)
    }
}

private fun screenTitle(state: AppState): Pair<String, String> = when (state.screen) {
    Screen.HOME -> "卷子" to "自己配卷，一键打印"
    Screen.CREATE -> "自定义题量" to "${GradeProfiles.gradeLabel(state.grade)} · ${state.totalQuestions} 题 · 预计 ${state.estimatedPages} 页 ${state.orientation.label}"
    Screen.PREVIEW -> if (state.isReference) {
        "速查表预览" to "A4 表格 · 与打印结果完全一致"
    } else {
        "试卷预览" to "共 ${state.paper?.questions?.size ?: 0} 题 · ${state.pdf?.pageCount ?: 0} 页 ${state.orientation.label}"
    }
    Screen.REFERENCE -> "学生必备速查表" to "7 张 A4 表格，可反复打印张贴"
    Screen.MISTAKES -> "错题本" to "数据只存在这台手机"
    Screen.MORE -> "更多" to "数据 / 打印 / 更新 / 关于"
    Screen.HISTORY -> "试卷库" to "只存题目快照，随时重开"
    Screen.HELP -> "打印帮助" to "在家打印与远程打印两种方式"
}

@Composable
private fun Divider() = Box(
    Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(PaperColors.Outline)
)

// ------------------------------------------------------------------ 首页：一屏完成选择 + 出卷

@Composable
private fun HomeScreen(state: AppState, scope: CoroutineScope) {
    val presets = PaperTemplates.forSelection(state.grade, state.subjects).take(4)
    val subjectLabel = state.subjects.joinToString(" + ") { it.label }

    CardSection {
        Text(
            "${GradeProfiles.gradeLabel(state.grade)} · $subjectLabel",
            fontSize = PaperDimens.HeroSp,
            fontWeight = FontWeight.SemiBold,
            color = PaperColors.OnSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "共 ${state.totalQuestions} 题 · 预计 ${state.estimatedPages} 页 A4 · ${state.answerMode.label}",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(PaperColors.AccentContainer, RoundedCornerShape(10.dp))
                .clickable { scope.launch { state.generateDaily() } }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "每日一练 · 今天这一套，明天自动换新",
                fontSize = PaperDimens.LabelSp,
                fontWeight = FontWeight.SemiBold,
                color = PaperColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            Text("›", fontSize = 18.sp, color = PaperColors.Accent)
        }
    }

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

    Spacer(Modifier.height(12.dp))
    SectionLabel("03", "选个卷子", "点一下直接出卷")
    Spacer(Modifier.height(8.dp))
    presets.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall)) {
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
        Spacer(Modifier.height(PaperDimens.GapSmall))
    }

    Spacer(Modifier.height(PaperDimens.Gap))
    if (state.savedPapers.isNotEmpty()) {
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
                if (state.liveSuggestExtra > 0) {
                    Text(
                        "建议再加约 ${state.liveSuggestExtra} 题，或开启「自动补满整页」由 App 代劳。",
                        fontSize = PaperDimens.CaptionSp,
                        color = PaperColors.Accent,
                        lineHeight = 16.sp,
                    )
                } else {
                    Text(
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
    SectionLabel("01", "题量", "共 ${state.totalQuestions} 题")
    Spacer(Modifier.height(8.dp))
    CardSection {
        if (state.selectedTypes.isEmpty()) {
            Text("还没选题型，点下方「添加题型」。", fontSize = PaperDimens.BodySp, color = PaperColors.OnSurfaceVariant)
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
            )
        }
        Divider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { state.showAllTypes = !state.showAllTypes }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "+ 添加题型（${state.unselectedTypes.size} 种可选）",
                fontSize = PaperDimens.BodySp,
                color = PaperColors.Primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (state.showAllTypes) "收起" else "展开",
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
            )
        }
        if (state.showAllTypes) {
            state.unselectedTypes.forEach { type ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(type.label, fontSize = PaperDimens.BodySp, modifier = Modifier.weight(1f))
                    MiniAction("加 10 题") { state.addType(type) }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().clickable { state.showAdvanced = !state.showAdvanced },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            SectionLabel("02", "卷面设置", if (state.showAdvanced) "收起" else "展开")
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
            TextField(
                value = state.titleOverride ?: "",
                onValueChange = { state.titleOverride = it },
                placeholder = { Text("留空则自动生成标题", fontSize = PaperDimens.BodySp) },
                label = { Text("卷子标题（可选）", fontSize = PaperDimens.CaptionSp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
                    if (isRef) "速查表 · 可直接打印张贴" else "共 ${gp?.questions?.size ?: 0} 题",
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
                hint = "1–30 题 · 会先试排，放不下自动减量",
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniAction("‹ 上一页", enabled = state.previewPage > 0) { state.nextPreviewPage(-1) }
            Spacer(Modifier.weight(1f))
            Text(
                "第 ${state.previewPage + 1} / ${state.previews.size} 页",
                fontSize = PaperDimens.LabelSp,
                color = PaperColors.OnSurface,
            )
            Spacer(Modifier.weight(1f))
            MiniAction(
                "下一页 ›",
                enabled = state.previewPage < state.previews.size - 1,
            ) { state.nextPreviewPage(1) }
        }
        Spacer(Modifier.height(10.dp))

        val bmp = state.previews.getOrNull(state.previewPage)
        if (bmp != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(1.dp, PaperColors.Outline, RoundedCornerShape(6.dp)),
            ) {
                Image(
                    bitmap = bmp,
                    contentDescription = "第 ${state.previewPage + 1} 页",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
        CardSection(title = "关于速查表") {
            Text(
                "速查表只收录不受著作权保护的基础事实与公式，自行整理编排。可反复打印张贴，不需要答案页。",
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

@Composable
private fun ReferenceScreen(state: AppState, scope: CoroutineScope) {
    CardSection {
        Text(
            "点任意一张即可生成 A4 PDF，用底部「系统打印」或「分享」远程打印。基础事实与公式，自行整理编排。",
            fontSize = PaperDimens.BodySp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
    Spacer(Modifier.height(PaperDimens.Gap))
    ReferenceData.sheets.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall)) {
            row.forEach { sheet ->
                PresetCard(
                    title = sheet.title,
                    desc = sheet.subtitle,
                    meta = "A4 · 立即打印",
                    onClick = { scope.launch { state.renderReference(sheet) } },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(PaperDimens.GapSmall))
    }
}

// ------------------------------------------------------------------ 错题本

@Composable
private fun MistakesScreen(state: AppState) {
    val filtered = state.mistakes.filter {
        state.masteryFilter == "ALL" || it.mastery == state.masteryFilter
    }
    val pending = state.mistakes.count { it.mastery != "MASTERED" }

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

    Spacer(Modifier.height(PaperDimens.Gap))
    filtered.forEach { m ->
        CardSection(title = "第 ${m.sourceNo} 题 · ${m.topic}", hint = "${GradeProfiles.gradeLabel(m.grade)} · ${m.type}") {
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

    if (filtered.isEmpty()) {
        CardSection(title = "还没有错题") {
            Text(
                "打印并批改后，到试卷预览的「题目清单」勾做错的题，就会进这里。",
                fontSize = PaperDimens.BodySp,
                color = PaperColors.OnSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
    }
    Text(
        "已掌握的题不参与组卷；同一知识点最多取 3 题。",
        fontSize = PaperDimens.CaptionSp,
        color = PaperColors.OnSurfaceVariant,
    )
}

private fun masteryLabel(m: String) = when (m) {
    "NEW" -> "未练"; "PRACTICED" -> "练过"; "MASTERED" -> "已掌握"; else -> m
}

// ------------------------------------------------------------------ 更多

@Composable
private fun MoreScreen(state: AppState, scope: CoroutineScope, onImport: () -> Unit) {
    CardSection(title = "数据") {
        ListRow("试卷库", "${state.savedPapers.size} 张卷子") { state.screen = Screen.HISTORY }
        Divider()
        ListRow("分享备份文件", "导出 JSON，含试卷库与错题本") { state.shareBackup() }
        Divider()
        ListRow("从备份恢复", "选择之前导出的 JSON") { onImport() }
    }

    Spacer(Modifier.height(PaperDimens.Gap))
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

    Spacer(Modifier.height(PaperDimens.Gap))
    CardSection(title = "关于") {
        Text(
            "题目全部由本 App 程序生成，或按知识点自编（内置 ${state.bankSize} 道选择 / 判断题）。" +
                "不抓取也不收录任何教辅、题库或教材原文；速查表只收录不受著作权保护的基础事实与公式。",
            fontSize = PaperDimens.BodySp,
            color = PaperColors.OnSurface,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "v${state.versionName} · 检查更新是唯一需要联网的动作，出卷、排版、打印全程离线可用。",
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
        )
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
