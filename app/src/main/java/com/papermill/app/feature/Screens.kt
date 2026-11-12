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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.GeneratorRegistry
import com.papermill.app.domain.generate.GradeProfiles
import com.papermill.app.domain.generate.PaperTemplates
import com.papermill.app.domain.generate.QuestionType
import com.papermill.app.domain.generate.Subject
import kotlinx.coroutines.launch

@Composable
fun PaperMillRoot(state: AppState) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.importBackup(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PaperColors.Background)
    ) {
        AppHeader(state)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            when (state.screen) {
                Screen.HOME -> HomeScreen(state, scope, activity)
                Screen.CREATE -> CreateScreen(state, scope)
                Screen.PREVIEW -> PreviewScreen(state, scope, activity)
                Screen.MISTAKES -> MistakesScreen(state, scope)
                Screen.HISTORY -> HistoryScreen(state, scope) { importLauncher.launch("*/*") }
                Screen.HELP -> HelpScreen(state)
            }
            Spacer(Modifier.height(12.dp))
            ToastBar(state.toast)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ 首页

@Composable
private fun HomeScreen(
    state: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    activity: Activity?,
) {
    val templates = PaperTemplates.forSelection(state.grade, state.subject)

    CardSection(title = "① 选择年级", hint = "小学 1–6 年级 / 初中 7–9 年级") {
        ChipGrid(
            items = (1..9).toList(),
            perRow = 4,
            label = { GradeProfiles.gradeLabel(it) },
            selected = { it == state.grade },
            onClick = { state.selectGrade(it) },
        )
    }

    Spacer(Modifier.height(12.dp))

    CardSection(title = "② 选择科目", hint = "灰色 = 该年级还没开这门课") {
        ChipGrid(
            items = Subject.values().toList(),
            perRow = 5,
            label = { it.label },
            selected = { it == state.subject },
            enabled = { it.availableFor(state.grade) },
            onClick = { state.selectSubject(it) },
        )
    }

    Spacer(Modifier.height(12.dp))

    CardSection(
        title = "③ 选个模板，或自己配题量",
        hint = "${GradeProfiles.gradeLabel(state.grade)} · ${state.subject.label} · 当前共 ${state.totalQuestions} 题",
    ) {
        templates.forEach { preset ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(PaperColors.Background, RoundedCornerShape(10.dp))
                    .clickable { state.applyTemplate(preset.wanted) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(preset.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(preset.desc, fontSize = 11.sp, color = PaperColors.OnSurfaceVariant)
                }
                Text("›", fontSize = 20.sp, color = PaperColors.Primary)
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    Spacer(Modifier.height(12.dp))

    PrimaryButton("自定义题量并生成 →") { state.screen = Screen.CREATE }
    Spacer(Modifier.height(10.dp))
    SecondaryButton("按当前配置直接出卷") { scope.launch { state.generate() } }

    if (state.savedPapers.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        CardSection(title = "最近的卷子") {
            state.savedPapers.take(3).forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { state.reopen(p) } }
                        .padding(vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, fontSize = 13.sp, color = PaperColors.OnSurface)
                        Text(
                            "${GradeProfiles.gradeLabel(p.grade)} · ${p.questions.size} 题",
                            fontSize = 11.sp,
                            color = PaperColors.OnSurfaceVariant,
                        )
                    }
                    Text("重开 ›", fontSize = 12.sp, color = PaperColors.Primary)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 参数页

@Composable
private fun CreateScreen(state: AppState, scope: kotlinx.coroutines.CoroutineScope) {
    val types = GeneratorRegistry.availableFor(state.grade, state.subject)

    CardSection(
        title = "自定义试卷",
        hint = "${GradeProfiles.gradeLabel(state.grade)} · ${state.subject.label} · 共 ${state.totalQuestions} 题 · 预计 ${state.estimatedPages} 页 A4",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("← 返回首页", false) { state.screen = Screen.HOME }
            ChoiceChip("换一套题", false) { scope.launch { state.generate() } }
        }
    }

    Spacer(Modifier.height(12.dp))

    CardSection(title = "题型与题量", hint = "点 ＋ / － 调整；0 表示本卷不出这类题") {
        types.forEach { type ->
            StepperRow(
                label = type.label,
                value = state.counts[type] ?: 0,
                hint = if (type.answerLines > 0) "需要书写过程，自动单栏 + 答题横线" else "客观题，按下方栏数排",
                onMinus = { state.setCount(type, -5) },
                onPlus = { state.setCount(type, 5) },
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    CardSection(title = "卷面设置") {
        Text("难度", fontSize = 12.sp, color = PaperColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "基础", 2 to "标准", 3 to "提高").forEach { (v, label) ->
                ChoiceChip(label, state.difficulty == v) { state.difficulty = v }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("客观题栏数", fontSize = 12.sp, color = PaperColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 4).forEach { c ->
                ChoiceChip("$c 栏", state.columns == c) { state.columns = c }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("答案页", fontSize = 12.sp, color = PaperColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnswerMode.values().forEach { m ->
                ChoiceChip(m.label, state.answerMode == m) { state.answerMode = m }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("姓名 / 班级 / 得分栏", fontSize = 12.sp, color = PaperColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("显示", state.showNameRow) { state.showNameRow = true }
            ChoiceChip("不显示", !state.showNameRow) { state.showNameRow = false }
        }
        Spacer(Modifier.height(12.dp))

        TextField(
            value = state.titleOverride ?: "",
            onValueChange = { state.titleOverride = it },
            placeholder = { Text("留空则自动生成标题", fontSize = 13.sp) },
            label = { Text("卷子标题（可选）", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(14.dp))
    PrimaryButton("生成试卷并预览", enabled = !state.busy && state.totalQuestions > 0) {
        scope.launch { state.generate() }
    }
}

// ------------------------------------------------------------------ 预览页

@Composable
private fun PreviewScreen(
    state: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    activity: Activity?,
) {
    val gp = state.paper
    val pdf = state.pdf

    CardSection(
        title = "试卷预览",
        hint = if (pdf != null) "共 ${pdf.pageCount} 页 A4 · ${gp?.questions?.size ?: 0} 题 · 与打印结果完全一致" else "还没有生成卷子",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("← 改参数", false) { state.screen = Screen.CREATE }
            ChoiceChip("A4 页面", !state.showQuestionList) { state.showQuestionList = false }
            ChoiceChip("题目清单", state.showQuestionList) { state.showQuestionList = true }
        }
    }

    Spacer(Modifier.height(12.dp))

    if (!state.showQuestionList) {
        state.previews.forEachIndexed { i, bitmap ->
            Text("第 ${i + 1} 页", fontSize = 11.sp, color = PaperColors.OnSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(1.dp, PaperColors.Outline, RoundedCornerShape(6.dp)),
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "试卷第 ${i + 1} 页",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    } else {
        CardSection(title = "题目清单 · 勾选做错的题", hint = "批改完纸质卷后，在这里点题号即可加入错题本") {
            gp?.questions?.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = q.no in state.wrongNumbers,
                        onCheckedChange = { state.toggleWrong(q.no) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text("${q.no}. ${q.stem}", fontSize = 12.sp, color = PaperColors.OnSurface)
                        Text("答案：${q.answer}", fontSize = 11.sp, color = PaperColors.OnSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            PrimaryButton("系统打印", enabled = !state.busy) {
                if (activity != null) scope.launch { state.print(activity) }
                else state.toast = "拿不到 Activity，无法调起打印"
            }
        }
        Box(Modifier.weight(1f)) {
            SecondaryButton("保存 PDF") { state.savePdf() }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            SecondaryButton("分享 PDF") { state.sharePdf() }
        }
        Box(Modifier.weight(1f)) {
            SecondaryButton("加入错题本（${state.wrongNumbers.size}）") { state.addWrongToMistakes() }
        }
    }
}

// ------------------------------------------------------------------ 错题本

@Composable
private fun MistakesScreen(state: AppState, scope: kotlinx.coroutines.CoroutineScope) {
    val filtered = state.mistakes.filter {
        state.masteryFilter == "ALL" || it.mastery == state.masteryFilter
    }
    val pending = state.mistakes.count { it.mastery != "MASTERED" }

    CardSection(title = "错题本", hint = "共 ${state.mistakes.size} 条 · 待练 $pending 条 · 数据只存在这台手机") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL" to "全部", "NEW" to "未练", "PRACTICED" to "练过", "MASTERED" to "已掌握")
                .forEach { (k, label) ->
                    ChoiceChip(label, state.masteryFilter == k) { state.masteryFilter = k }
                }
        }
    }

    Spacer(Modifier.height(12.dp))

    filtered.forEach { m ->
        CardSection(title = "第 ${m.sourceNo} 题 · ${m.topic}", hint = "${GradeProfiles.gradeLabel(m.grade)} · ${m.type}") {
            Text(m.stem, fontSize = 13.sp, color = PaperColors.OnSurface)
            Text("答案：${m.answer}", fontSize = 12.sp, color = PaperColors.OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("状态：${masteryLabel(m.mastery)}", false) {}
                ChoiceChip("标记已掌握", false) { state.setMastery(m.id, "MASTERED") }
                ChoiceChip("重新待练", false) { state.setMastery(m.id, "NEW") }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (filtered.isEmpty()) {
        CardSection(title = "还没有错题") {
            Text("打印并批改后，到试卷预览的「题目清单」里勾选做错的题，就会进这里。", fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
    }

    PrimaryButton("生成错题重练卷（$pending 题）", enabled = !state.busy && pending > 0) {
        scope.launch { state.generateMistakePaper() }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "已掌握的题不参与组卷；同一知识点最多取 3 题，避免整卷都是同一个考点。",
        fontSize = 11.sp,
        color = PaperColors.OnSurfaceVariant,
    )
}

private fun masteryLabel(m: String) = when (m) {
    "NEW" -> "未练"; "PRACTICED" -> "练过"; "MASTERED" -> "已掌握"; else -> m
}

// ------------------------------------------------------------------ 试卷库

@Composable
private fun HistoryScreen(
    state: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    onImport: () -> Unit,
) {
    CardSection(title = "试卷库", hint = "共 ${state.savedPapers.size} 张 · 只存题目快照，随时可重新出同一张卷") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("分享备份文件", false) { state.shareBackup() }
            ChoiceChip("从备份恢复", false) { onImport() }
        }
    }

    Spacer(Modifier.height(12.dp))

    state.savedPapers.forEach { p ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(PaperColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, PaperColors.Outline, RoundedCornerShape(12.dp))
                .clickable { scope.launch { state.reopen(p) } }
                .padding(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.title, fontSize = 13.sp, color = PaperColors.OnSurface)
                Text(
                    "${GradeProfiles.gradeLabel(p.grade)} · ${p.questions.size} 题 · ${p.counts.size} 种题型",
                    fontSize = 11.sp,
                    color = PaperColors.OnSurfaceVariant,
                )
            }
            Text("打开 ›", fontSize = 12.sp, color = PaperColors.Primary)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (state.savedPapers.isEmpty()) {
        CardSection(title = "还没有生成过卷子") {
            Text("回「出卷」挑模板或自己配题量，生成后会自动存到这里。", fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------ 打印帮助

@Composable
private fun HelpScreen(state: AppState) {
    CardSection(title = "① 在家打印（手机和打印机同一 WiFi）") {
        HelpLine("1", "第一次：手机装「奔图打印」App，或在 系统设置 → 打印 里启用 Mopria 打印服务。")
        HelpLine("2", "在 App 里点「系统打印」，系统打印框中选择奔图 M1。")
        HelpLine("3", "纸张选 ISO A4，份数按需，点打印。")
        Text(
            "原理：奔图 M1 支持 AirPrint / Mopria，Android 自带打印框架就能直接找到它，不需要奔图 SDK。",
            fontSize = 11.sp,
            color = PaperColors.OnSurfaceVariant,
        )
    }

    Spacer(Modifier.height(12.dp))
    CardSection(
        title = "系统打印服务状态",
        hint = if (state.hasPrintService) "已检测到打印服务" else "未检测到打印服务，可能需要在系统设置里启用",
    ) {
        SecondaryButton("打开系统打印设置") { state.openPrintSettings() }
    }

    Spacer(Modifier.height(12.dp))
    CardSection(title = "② 在外面 / 不在同一网络时远程打印") {
        HelpLine("1", "点「分享 PDF」，把文件发到微信（发给「文件传输助手」即可）。")
        HelpLine("2", "在微信里长按该 PDF → 用其他应用打开 → 选「奔图云打印」小程序。")
        HelpLine("3", "小程序里选已绑定的 M1，确认打印。")
        Text(
            "说明：奔图云打印没有公开 API，开放平台 SDK 需企业资质审核，所以本 App 不能直连云端。" +
                "远程打印这一步需要在小程序里完成，App 只负责把排版好的 PDF 交出去。",
            fontSize = 11.sp,
            color = PaperColors.OnSurfaceVariant,
        )
    }

    Spacer(Modifier.height(12.dp))
    CardSection(title = "关于题目") {
        Text(
            "所有题目均由本 App 程序生成（数学计算、字词默写、理化计算等），" +
                "不抓取也不收录任何教辅、题库或教材原文，页脚已标注。答案在生成时同步算出并做自校验。",
            fontSize = 12.sp,
            color = PaperColors.OnSurface,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun HelpLine(no: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(no, fontSize = 12.sp, color = PaperColors.Primary, fontWeight = FontWeight.Medium)
        Text("  $text", fontSize = 12.sp, color = PaperColors.OnSurface, lineHeight = 18.sp)
    }
}
