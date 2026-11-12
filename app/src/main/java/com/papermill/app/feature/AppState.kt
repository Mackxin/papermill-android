package com.papermill.app.feature

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.papermill.app.core.export.PdfExporter
import com.papermill.app.core.export.PdfSharer
import com.papermill.app.core.pdf.render.PaperAssembler
import com.papermill.app.core.pdf.render.PaperRenderer
import com.papermill.app.core.pdf.render.PdfPreviewRenderer
import com.papermill.app.core.pdf.render.RenderedPdf
import com.papermill.app.core.print.PrintLauncher
import com.papermill.app.core.print.PrintServiceProbe
import com.papermill.app.core.store.LocalStore
import com.papermill.app.core.store.MistakeItem
import com.papermill.app.core.store.SavedPaper
import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.GeneratedPaper
import com.papermill.app.domain.generate.GeneratedQuestion
import com.papermill.app.domain.generate.GeneratedSection
import com.papermill.app.domain.generate.GradeProfiles
import com.papermill.app.domain.generate.PaperComposer
import com.papermill.app.domain.generate.PaperSpec
import com.papermill.app.domain.generate.QuestionType
import com.papermill.app.domain.generate.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { HOME, CREATE, PREVIEW, MISTAKES, HISTORY, HELP }

private data class Built(
    val paper: GeneratedPaper,
    val pdf: RenderedPdf,
    val previews: List<ImageBitmap>,
    val savedId: String,
)

/**
 * 全局 UI 状态 + 动作。
 *
 * 没有引 ViewModel / DI 框架：整个 App 的状态图很小，
 * 一个状态类 + 一个 Context 足够，也避开本机 Hilt/KSP 的已知坑。
 * 所有写盘、渲染、生成题目都在 IO 线程；Compose state 只在主线程改。
 */
class AppState(private val rawContext: Context) {

    private val ctx = rawContext.applicationContext

    var screen by mutableStateOf(Screen.HOME)
    var grade by mutableStateOf(3)
    var subject by mutableStateOf(Subject.MATH)
    var counts by mutableStateOf<Map<QuestionType, Int>>(emptyMap())
    var difficulty by mutableStateOf(2)
    var columns by mutableStateOf(2)
    var answerMode by mutableStateOf(AnswerMode.LAST_PAGE)
    var showNameRow by mutableStateOf(true)
    var titleOverride by mutableStateOf<String?>(null)

    var paper by mutableStateOf<GeneratedPaper?>(null)
    var pdf by mutableStateOf<RenderedPdf?>(null)
    var previews by mutableStateOf<List<ImageBitmap>>(emptyList())
    var savedPaperId by mutableStateOf("")
    var wrongNumbers by mutableStateOf<Set<Int>>(emptySet())
    var showQuestionList by mutableStateOf(false)

    var busy by mutableStateOf(false)
    var toast by mutableStateOf("")

    var savedPapers by mutableStateOf(emptyList<SavedPaper>())
    var mistakes by mutableStateOf(emptyList<MistakeItem>())
    var masteryFilter by mutableStateOf("ALL")

    val hasPrintService: Boolean = PrintServiceProbe.hasService(ctx)

    init {
        counts = PaperComposer.defaultSpec(grade, subject).counts
        refreshLists()
    }

    fun refreshLists() {
        savedPapers = LocalStore.loadPapers(ctx)
        mistakes = LocalStore.loadMistakes(ctx)
    }

    // ---------------------------------------------------------------- 选择

    fun selectGrade(g: Int) {
        grade = g
        if (!subject.availableFor(g)) subject = Subject.MATH
        counts = PaperComposer.defaultSpec(g, subject).counts
        resetTitle()
    }

    fun selectSubject(s: Subject) {
        if (!s.availableFor(grade)) return
        subject = s
        counts = PaperComposer.defaultSpec(grade, s).counts
        resetTitle()
    }

    fun applyTemplate(counts: Map<QuestionType, Int>) {
        this.counts = LinkedHashMap(counts)
        resetTitle()
        screen = Screen.CREATE
    }

    private fun resetTitle() {
        titleOverride = null
    }

    fun setCount(type: QuestionType, delta: Int) {
        val map = LinkedHashMap(counts)
        val next = ((map[type] ?: 0) + delta).coerceIn(0, 100)
        if (next == 0) map.remove(type) else map[type] = next
        counts = map
    }

    val totalQuestions: Int get() = counts.values.sum()

    val estimatedPages: Int
        get() {
            var pages = 1
            val perPage = columns * 26
            pages += totalQuestions / perPage.coerceAtLeast(1)
            // 有书写过程的题占地方
            pages += counts.entries.filter { it.key.answerLines > 0 }.sumOf { it.value } / 8
            if (answerMode == AnswerMode.LAST_PAGE) pages += 1
            return pages.coerceAtLeast(1)
        }

    private fun currentSpec(seed: Long = System.currentTimeMillis()): PaperSpec {
        val total = totalQuestions
        val autoTitle = "${GradeProfiles.stageLabel(grade)} · ${GradeProfiles.gradeLabel(grade)} · ${subject.label}练习"
        return PaperSpec(
            grade = grade,
            subject = subject,
            title = titleOverride?.takeIf { it.isNotBlank() } ?: autoTitle,
            subtitle = "共 $total 题　满分 ${total * 2} 分",
            counts = counts,
            difficulty = difficulty,
            columns = columns,
            answerMode = answerMode,
            showNameRow = showNameRow,
            fontSize = 11f,
            seed = seed,
        )
    }

    // ---------------------------------------------------------------- 生成

    suspend fun generate(seed: Long = System.currentTimeMillis()) {
        if (totalQuestions <= 0) {
            toast = "至少选一种题型、题量大于 0"
            return
        }
        busy = true
        try {
            val built = withContext(Dispatchers.IO) {
                val spec = currentSpec(seed)
                val recent = LocalStore.recentHashes(ctx, grade, subject)
                val gp = PaperComposer.compose(spec, recent)
                val doc = PaperAssembler.build(gp)
                val rendered = PaperRenderer.render(doc)
                val pages = PdfPreviewRenderer.render(ctx, rendered.bytes)
                val saved = LocalStore.savePaper(ctx, gp, rendered.fileName)
                Built(gp, rendered, pages, saved.id)
            }
            applyBuilt(built)
            val actual = built.paper.questions.size
            toast = if (actual < totalQuestions) {
                "已生成 $actual 题（要了 $totalQuestions 题，该年级可用题量不足）——可减少题量或增选其它题型"
            } else {
                "已生成 $actual 题，共 ${built.pdf.pageCount} 页"
            }
            screen = Screen.PREVIEW
        } catch (t: Throwable) {
            toast = "生成失败：${t.message ?: t.javaClass.simpleName}"
        }
        busy = false
    }

    /** 试卷库里点一张历史卷：同一种子会生成同一套题，无需存整张 PDF */
    suspend fun reopen(saved: SavedPaper) {
        grade = saved.grade
        subject = runCatching { Subject.valueOf(saved.subject) }.getOrDefault(Subject.MATH)
        counts = PaperComposer.defaultSpec(grade, subject).counts
        titleOverride = saved.title
        busy = true
        try {
            val built = withContext(Dispatchers.IO) {
                val spec = LocalStore.toSpec(saved).copy(title = saved.title, seed = saved.createdAt)
                val gp = PaperComposer.compose(spec)
                val rendered = PaperRenderer.render(PaperAssembler.build(gp))
                val pages = PdfPreviewRenderer.render(ctx, rendered.bytes)
                Built(gp, rendered, pages, saved.id)
            }
            applyBuilt(built)
            toast = "已重新打开：${saved.title}"
            screen = Screen.PREVIEW
        } catch (t: Throwable) {
            toast = "打开失败：${t.message}"
        }
        busy = false
    }

    /** 错题重练卷：直接把错题快照组成卷子，不走生成器 */
    suspend fun generateMistakePaper() {
        val pool = mistakes.filter { it.mastery != "MASTERED" }
            .groupBy { it.topic }
            .flatMap { (_, list) -> list.take(3) }   // 同一知识点最多 3 题，防止整卷都是同一考点
            .take(40)
        if (pool.isEmpty()) {
            toast = "没有待练的错题（已掌握的不参与组卷）"
            return
        }
        busy = true
        try {
            val built = withContext(Dispatchers.IO) {
                val qs = pool.mapIndexed { i, m ->
                    GeneratedQuestion(
                        no = i + 1,
                        type = runCatching { QuestionType.valueOf(m.type) }.getOrDefault(QuestionType.ORAL),
                        stem = m.stem,
                        answer = m.answer,
                        topic = m.topic,
                    )
                }
                val sections = qs.groupBy { it.type }.entries.mapIndexed { idx, (type, list) ->
                    GeneratedSection(
                        index = idx + 1,
                        title = "${idx + 1}、${type.label}（错题重练）",
                        type = type,
                        questions = list,
                        columns = if (type.answerLines > 0) 1 else 2,
                        answerLines = type.answerLines,
                    )
                }
                val spec = currentSpec().copy(
                    title = "${subject.label}错题重练",
                    subtitle = "共 ${qs.size} 题　来自错题本",
                    isMistakePaper = true,
                    answerMode = AnswerMode.LAST_PAGE,
                    counts = emptyMap(),
                )
                val gp = GeneratedPaper(spec, sections, qs, System.currentTimeMillis())
                val rendered = PaperRenderer.render(PaperAssembler.build(gp))
                val pages = PdfPreviewRenderer.render(ctx, rendered.bytes)
                val saved = LocalStore.savePaper(ctx, gp, rendered.fileName)
                LocalStore.markPracticed(ctx, pool.map { it.id })
                Built(gp, rendered, pages, saved.id)
            }
            applyBuilt(built)
            refreshLists()
            toast = "已生成错题重练卷：${built.paper.questions.size} 题"
            screen = Screen.PREVIEW
        } catch (t: Throwable) {
            toast = "生成失败：${t.message}"
        }
        busy = false
    }

    private fun applyBuilt(b: Built) {
        paper = b.paper
        pdf = b.pdf
        previews = b.previews
        savedPaperId = b.savedId
        wrongNumbers = emptySet()
        showQuestionList = false
        refreshLists()
    }

    // ---------------------------------------------------------------- 打印 / 导出

    suspend fun print(activity: Activity) {
        val current = pdf ?: return
        busy = true
        try {
            PrintLauncher.print(activity, paper?.spec?.title ?: "练习卷", current)
            toast = "已调起系统打印框：选择奔图 M1（共 ${current.pageCount} 页）"
        } catch (t: Throwable) {
            toast = "打印失败：${t.message}"
        }
        busy = false
    }

    fun savePdf(): File? {
        val current = pdf ?: return null
        val file = PdfExporter.write(ctx, current.fileName, current.bytes)
        toast = "已保存：${file.absolutePath}"
        return file
    }

    fun sharePdf() {
        val file = savePdf() ?: return
        runCatching { PdfSharer.share(ctx, file, paper?.spec?.title ?: "练习卷") }
            .onFailure { toast = "分享失败：${it.message}" }
    }

    fun addWrongToMistakes() {
        val gp = paper ?: return
        val added = LocalStore.addMistakes(ctx, gp, savedPaperId, wrongNumbers)
        refreshLists()
        toast = "已加入错题本：$added 道（重复的题只更新状态）"
        wrongNumbers = emptySet()
    }

    fun toggleWrong(no: Int) {
        val set = wrongNumbers.toMutableSet()
        if (!set.add(no)) set.remove(no)
        wrongNumbers = set
    }

    fun setMastery(id: String, value: String) {
        LocalStore.setMastery(ctx, id, value)
        refreshLists()
    }

    // ---------------------------------------------------------------- 备份

    fun exportBackup(): File {
        val dir = PdfExporter.papersDir(ctx)
        val file = File(dir, "卷子备份_${System.currentTimeMillis()}.json")
        file.writeText(LocalStore.exportAll(ctx))
        toast = "备份已存到：${file.absolutePath}"
        return file
    }

    fun importBackup(uri: Uri) {
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        toast = if (text != null && LocalStore.importAll(ctx, text)) {
            refreshLists()
            "备份已恢复"
        } else "备份文件读取失败或格式不对"
    }

    fun openPrintSettings() = PrintServiceProbe.openSettings(ctx)

    fun shareBackup() {
        val file = exportBackup()
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            ctx.startActivity(
                Intent.createChooser(send, "发送备份").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { toast = "分享失败：${it.message}" }
    }
}
