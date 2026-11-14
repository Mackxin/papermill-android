package com.papermill.app.feature

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.papermill.app.BuildConfig
import com.papermill.app.core.export.PdfExporter
import com.papermill.app.core.export.PdfSharer
import com.papermill.app.core.pdf.render.PaperAssembler
import com.papermill.app.core.pdf.render.PaperRenderer
import com.papermill.app.core.pdf.render.PdfPreviewRenderer
import com.papermill.app.core.pdf.render.PracticeRenderer
import com.papermill.app.core.pdf.render.ReferenceRenderer
import com.papermill.app.core.pdf.render.RenderedPdf
import com.papermill.app.core.print.PrintLauncher
import com.papermill.app.core.print.PrintServiceProbe
import com.papermill.app.core.store.LocalStore
import com.papermill.app.core.store.MistakeItem
import com.papermill.app.core.store.SavedPaper
import com.papermill.app.core.update.UpdateChecker
import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.BankQuestionSource
import com.papermill.app.domain.generate.GenContext
import com.papermill.app.domain.generate.GeneratedPaper
import com.papermill.app.domain.generate.GeneratedQuestion
import com.papermill.app.domain.generate.GeneratedSection
import com.papermill.app.domain.generate.GeneratorRegistry
import com.papermill.app.domain.generate.GradeProfiles
import com.papermill.app.domain.generate.PaperComposer
import com.papermill.app.domain.generate.PaperSpec
import com.papermill.app.domain.generate.PageOrientation
import com.papermill.app.domain.generate.QuestionType
import com.papermill.app.domain.generate.Subject
import com.papermill.app.domain.generate.stableHash
import com.papermill.app.domain.reference.PracticeSheet
import com.papermill.app.domain.reference.ReferenceSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.ceil

enum class Screen { HOME, CREATE, PREVIEW, MISTAKES, HISTORY, HELP, REFERENCE, MORE }

/** 底部 4 个固定页签；PREVIEW / CREATE 归到「出卷」，HISTORY / HELP 归到「更多」 */
enum class NavTab { HOME, REFERENCE, MISTAKES, MORE }

private data class Built(
    val paper: GeneratedPaper,
    val pdf: RenderedPdf,
    val previews: List<ImageBitmap>,
    val savedId: String,
)

/** 一页正文大约能装的高度与单题高度，用于把「还差多少题」估算出来 */
private const val TYPICAL_CONTENT_H = 657f
private const val TYPICAL_CONTENT_H_LANDSCAPE = 410f
private const val TYPICAL_QUESTION_H = 20.5f
private const val FILL_TARGET = 0.84f
/** 自动补题总量上限：想要更多由用户在预览页显式点「加 N 题」 */
private const val MAX_AUTO_EXTRA = 24

class AppState(private val rawContext: Context) {

    private val ctx = rawContext.applicationContext

    var screen by mutableStateOf(Screen.HOME)

    var grade by mutableStateOf(3)
    var subject by mutableStateOf(Subject.MATH)
    var subjects by mutableStateOf(listOf(Subject.MATH))
    var counts by mutableStateOf<Map<QuestionType, Int>>(emptyMap())

    var difficulty by mutableStateOf(2)
    var columns by mutableStateOf(2)
    var answerMode by mutableStateOf(AnswerMode.LAST_PAGE)
    var showNameRow by mutableStateOf(true)
    var titleOverride by mutableStateOf<String?>(null)
    var autoFillPage by mutableStateOf(true)
    /** 竖版 / 横版 A4，预览页可直接改并即时重排 */
    var orientation by mutableStateOf(PageOrientation.PORTRAIT)

    var paper by mutableStateOf<GeneratedPaper?>(null)
    var pdf by mutableStateOf<RenderedPdf?>(null)
    var previews by mutableStateOf<List<ImageBitmap>>(emptyList())
    var savedPaperId by mutableStateOf("")
    var wrongNumbers by mutableStateOf<Set<Int>>(emptySet())
    var showQuestionList by mutableStateOf(false)
    var isReference by mutableStateOf(false)

    // 预览页改成「一次只看一页」，不再把 3 页 A4 竖着铺满整个屏幕
    var previewPage by mutableStateOf(0)
    // 参数页默认只露题量；卷面细节收进折叠区，避免一屏塞 6 组开关
    var showAdvanced by mutableStateOf(false)
    var showAllTypes by mutableStateOf(false)
    /** v0.6.0 组合面板的题型搜索词 */
    var typeQuery by mutableStateOf("")
    /** 预览页「自定义加题」一次加几道，用户可调 */
    var extraAddCount by mutableStateOf(5)

    var livePreview by mutableStateOf<ImageBitmap?>(null)
    var liveFillPercent by mutableStateOf(0)
    var previewSeed by mutableStateOf(daySeed())

    var busy by mutableStateOf(false)
    var toast by mutableStateOf("")

    /** 进预览页之前在哪：返回键要回到「上一页」，不是一律回首页 */
    var previewOrigin by mutableStateOf(Screen.HOME)

    /** 返回键二段式退出：第一次只提示，2 秒内再按才真的退到桌面 */
    var exitArmed by mutableStateOf(false)

    var savedPapers by mutableStateOf(emptyList<SavedPaper>())
    var mistakes by mutableStateOf(emptyList<MistakeItem>())
    var masteryFilter by mutableStateOf("ALL")

    var updateInfo by mutableStateOf("")
    var updateRelease by mutableStateOf<UpdateChecker.Release?>(null)

    val hasPrintService: Boolean = PrintServiceProbe.hasService(ctx)
    val versionName: String = BuildConfig.VERSION_NAME
    val bankSize: Int

    val availableTypes: List<QuestionType>
        get() = GeneratorRegistry.availableFor(grade, subjects)

    /** 参数页只列已选题型；没选的收进「添加题型」折叠区 */
    val selectedTypes: List<QuestionType>
        get() = availableTypes.filter { (counts[it] ?: 0) > 0 }


    val navTab: NavTab
        get() = when (screen) {
            Screen.REFERENCE -> NavTab.REFERENCE
            Screen.MISTAKES -> NavTab.MISTAKES
            Screen.MORE, Screen.HISTORY, Screen.HELP -> NavTab.MORE
            Screen.PREVIEW -> if (isReference) NavTab.REFERENCE else NavTab.HOME
            else -> NavTab.HOME
        }

    /**
     * App 内返回：逐层退回，**最后才在首页退出 App**。
     *
     * 旧版全靠系统默认行为，预览页按一下返回就直接回桌面，卷子、滚动位置全丢。
     * 现在顺序是：预览 → 来时的页面 → 首页 → （2 秒内再按）退出。
     *
     * @return true = 已在本 App 内处理；false = 已在首页根节点，交给调用方退出
     */
    fun back(): Boolean {
        toast = ""
        return when (screen) {
            Screen.PREVIEW -> {
                screen = previewOrigin
                true
            }
            Screen.CREATE -> {
                screen = Screen.HOME
                true
            }
            Screen.HISTORY, Screen.HELP -> {
                screen = Screen.MORE
                true
            }
            Screen.REFERENCE, Screen.MISTAKES, Screen.MORE -> {
                screen = Screen.HOME
                true
            }
            Screen.HOME -> false
        }
    }

    fun goTab(tab: NavTab) {
        toast = ""
        screen = when (tab) {
            NavTab.HOME -> Screen.HOME
            NavTab.REFERENCE -> Screen.REFERENCE
            NavTab.MISTAKES -> Screen.MISTAKES
            NavTab.MORE -> Screen.MORE
        }
        // 这里原来同步调 refreshLists()（两次 JSON 磁盘读取 + 解析）。
        // 滑到「更多」页的那一帧正好是 pager settle 收尾，主线程一堵就是明显掉帧。
        // 现在交给 MoreScreen 进页时异步刷，见 refreshListsAsync()。
    }

    /** 首页快捷卡片：套用题量后立刻出卷，不再先跳去参数页 */
    fun useTemplate(newCounts: Map<QuestionType, Int>) {
        counts = LinkedHashMap(newCounts)
        titleOverride = null
        previewSeed = System.currentTimeMillis()
    }

    /**
     * v0.5.0 首页「本版新增」快捷卷：自动切到对应科目再出卷。
     * 不切的话卷头会写「三年级 · 数学练习」，纸上却印着英语 / 识字题，
     * 家长拿到纸会觉得出错了；同时低年级没开英语、初中不开主题识字，先拦一下。
     */
    suspend fun generateForSubject(target: Subject, newCounts: Map<QuestionType, Int>) {
        if (!target.availableFor(grade)) {
            toast = "${target.label}在${GradeProfiles.gradeLabel(grade)}尚未开课，请先换年级"
            return
        }
        subjects = listOf(target)
        subject = target
        useTemplate(newCounts)
        generate()
    }

    /**
     * 跨科专项卷：勾多个科目，只保留当前年级真有生成器的题型。
     * 「课本同步卷」要同时用到语文生字和英语单词，单科目版本会漏一半。
     */
    suspend fun generateForSubjects(targets: List<Subject>, newCounts: Map<QuestionType, Int>) {
        val usable = targets.filter { it.availableFor(grade) }
        if (usable.isEmpty()) {
            toast = "当前年级还没有这些科目，请先换年级"
            return
        }
        subjects = usable
        subject = usable.first()
        useTemplate(newCounts)
        generate()
    }

    fun addType(type: QuestionType) = setCount(type, 10)

    fun nextPreviewPage(delta: Int) {
        val last = (previews.size - 1).coerceAtLeast(0)
        previewPage = (previewPage + delta).coerceIn(0, last)
    }

    fun clearToast() { toast = "" }

    init {
        bankSize = BankQuestionSource.loadFromAssets(ctx)
        counts = PaperComposer.defaultSpec(grade, subjects).counts
        refreshLists()
    }

    fun refreshLists() {
        savedPapers = LocalStore.loadPapers(ctx)
        mistakes = LocalStore.loadMistakes(ctx)
    }

    /**
     * 异步版刷新：磁盘读取 + JSON 解析全在 IO 线程。
     * 切页 / 进「更多」页这类交互路径上不能再同步读盘，否则掉帧。
     * 只在真变了才赋值，避免白白触发一轮重组。
     */
    suspend fun refreshListsAsync() {
        val papers = withContext(Dispatchers.IO) { LocalStore.loadPapers(ctx) }
        val marks = withContext(Dispatchers.IO) { LocalStore.loadMistakes(ctx) }
        if (savedPapers != papers) savedPapers = papers
        if (mistakes != marks) mistakes = marks
    }

    // ---------------------------------------------------------------- 选择

    fun selectGrade(g: Int) {
        grade = g
        val kept = subjects.filter { it.availableFor(g) }.ifEmpty { listOf(Subject.MATH) }
        subjects = kept
        subject = kept.first()
        counts = PaperComposer.defaultSpec(g, kept).counts
        titleOverride = null
        previewSeed = System.currentTimeMillis()
    }

    /** 多学科交叉：点一下加进来，再点一下取消（至少留一科） */
    fun toggleSubject(s: Subject) {
        if (!s.availableFor(grade)) return
        val list = subjects.toMutableList()
        if (s in list) {
            if (list.size == 1) return
            list.remove(s)
        } else {
            list.add(s)
        }
        subjects = list
        subject = list.first()
        counts = PaperComposer.defaultSpec(grade, list).counts
        titleOverride = null
        previewSeed = System.currentTimeMillis()
    }

    fun applyTemplate(newCounts: Map<QuestionType, Int>) {
        counts = LinkedHashMap(newCounts)
        titleOverride = null
        previewSeed = System.currentTimeMillis()
        screen = Screen.CREATE
    }

    /** 参数页「换一套看看」：只换实时预览的题目，不动正式卷 */
    fun shufflePreviewSeed() {
        previewSeed = System.currentTimeMillis()
    }

    fun setCount(type: QuestionType, delta: Int) {
        val map = LinkedHashMap(counts)
        val next = ((map[type] ?: 0) + delta).coerceIn(0, 100)
        if (next == 0) map.remove(type) else map[type] = next
        counts = map
    }

    /** v0.6.0 组合面板：单独移除一种题型 */
    fun removeType(type: QuestionType) {
        if (counts[type] == null) return
        counts = LinkedHashMap(counts).apply { remove(type) }
        previewSeed = System.currentTimeMillis()
    }

    fun clearTypes() {
        counts = emptyMap()
        titleOverride = null
        previewSeed = System.currentTimeMillis()
        toast = "已清空题型，重新挑几样吧"
    }

    /**
     * v0.6.0 智能配比：把当前年级 / 科目下**所有能出的题型**各取一点，
     * 组成大约一页纸的组合。用户不用自己算「每种几题才够一页」。
     *
     * 规则：每科最多取 2 种题型，客观题 8 题、需书写过程的题 3 题，
     * 总题量封顶 32 —— 再多就必然翻到第二页，反而浪费纸。
     */
    fun smartCompose() {
        val types = availableTypes
        if (types.isEmpty()) {
            toast = "这个年级还没有可用题型"
            return
        }
        val map = LinkedHashMap<QuestionType, Int>()
        var budget = 32
        types.groupBy { GeneratorRegistry.subjectOf(it) }
            .forEach { (_, list) ->
                list.take(2).forEach { t ->
                    if (budget <= 0) return@forEach
                    val want = if (t.answerLines > 0) 3 else 8
                    val n = minOf(want, budget)
                    if (n > 0) {
                        map[t] = n
                        budget -= n
                    }
                }
            }
        counts = map
        titleOverride = null
        previewSeed = System.currentTimeMillis()
        toast = "已按 ${map.size} 种题型智能配比，共 ${map.values.sum()} 题"
    }

    val totalQuestions: Int get() = counts.values.sum()

    val estimatedPages: Int
        get() {
            var pages = 1
            pages += totalQuestions / (columns * 26).coerceAtLeast(1)
            pages += counts.entries.filter { it.key.answerLines > 0 }.sumOf { it.value } / 8
            if (answerMode == AnswerMode.LAST_PAGE) pages += 1
            return pages.coerceAtLeast(1)
        }

    /** 当前正式卷「最后一页正文」的填充百分比 */
    val lastContentFillPercent: Int
        get() {
            val p = pdf ?: return 0
            return (contentFillRatio(p) * 100).toInt()
        }

    private fun contentFillRatio(p: RenderedPdf): Float {
        val list = p.pageFillRatios
        if (list.isEmpty()) return 1f
        // PaperRenderer 已把答案页排除在 contentPageCount 之外，
        // 不再用「倒数第二页」这种在答案跨页时会算错的近似。
        val idx = (p.contentPageCount - 1).coerceIn(0, list.lastIndex)
        return list[idx]
    }

    /** 参数页实时预览用：还差多少题才能把最后一页正文印满 */
    val liveSuggestExtra: Int get() = suggestedExtra(liveFillPercent / 100f)

    /** 正式卷用：还差多少题 */
    val finalSuggestExtra: Int get() = suggestedExtra(lastContentFillPercent / 100f)

    private fun suggestedExtra(ratio: Float): Int = suggestedExtra(ratio, null)

    /**
     * v0.5.0：补题量按「实际要补的那个题型」折算栏数。
     * 连词成句 / 选词填空是单栏整行题，一道顶 2 道两栏口算的高度；
     * 之前统一按全局 columns=2 估算，一次补 16 题直接顶到第二页，
     * 触发「不增页」硬约束后整轮回退，结果一题没补、末页只有 65%。
     */
    private fun suggestedExtra(ratio: Float, fillType: QuestionType?): Int {
        if (ratio >= FILL_TARGET) return 0
        val pageH = if (orientation == PageOrientation.LANDSCAPE) {
            TYPICAL_CONTENT_H_LANDSCAPE
        } else {
            TYPICAL_CONTENT_H
        }
        // 2 栏时一道题只占约半行高度；单栏长句题按 1 栏算
        val effectiveCols = if (fillType != null &&
            (fillType.answerLines > 0 || fillType.columns <= 1)
        ) 1 else columns.coerceAtLeast(1)
        val gainPerQuestion = TYPICAL_QUESTION_H / effectiveCols
        return ceil((0.90f - ratio) * pageH / gainPerQuestion)
            .toInt().coerceIn(1, 16)
    }

    /**
     * 优先补「最后一个大题」的题型 —— 它就在最后一页正文上，补它才真的把最后一张纸填满。
     *
     * v0.5.0：先排除「已经补不动」的题型。像选词填空只有有限条自编小题库，
     * 请求 20 题实际只能出 8 题时，继续给它加题只会空转，还会弹出
     * 「可用题量不足」却始终填不满纸。这时改补其它还有容量的题型。
     */
    private fun pickFillType(map: Map<QuestionType, Int>, probe: Built?): QuestionType? {
        val used = map.filter { it.value > 0 }.keys
        val exhausted = probe?.paper?.sections
            ?.filter { it.questions.size < (map[it.type] ?: 0) }
            ?.map { it.type }
            ?.toSet() ?: emptySet()
        val last = probe?.paper?.sections?.lastOrNull()?.type
        if (last != null && last in used && last !in exhausted) return last
        return used.firstOrNull { it.answerLines == 0 && it !in exhausted }
            ?: used.firstOrNull { it !in exhausted }
            ?: used.firstOrNull { it.answerLines == 0 }
            ?: used.firstOrNull()
            ?: availableTypes.firstOrNull()
    }

    private fun currentSpec(seed: Long, useCounts: Map<QuestionType, Int> = counts): PaperSpec {
        val total = useCounts.values.sum()
        val autoTitle = if (subjects.size > 1) {
            "${GradeProfiles.stageLabel(grade)} · ${GradeProfiles.gradeLabel(grade)} · 综合练习"
        } else {
            "${GradeProfiles.stageLabel(grade)} · ${GradeProfiles.gradeLabel(grade)} · ${subject.label}练习"
        }
        return PaperSpec(
            grade = grade,
            subject = subject,
            subjects = subjects,
            title = titleOverride?.takeIf { it.isNotBlank() } ?: autoTitle,
            subtitle = "共 $total 题　满分 ${total * 2} 分",
            counts = useCounts,
            difficulty = difficulty,
            columns = columns,
            answerMode = answerMode,
            showNameRow = showNameRow,
            fontSize = 11f,
            seed = seed,
            orientation = orientation,
        )
    }

    /**
     * 切换纸张方向。在预览页直接切会立刻用同一套题重新排版，
     * 用户看到空白页时可以先试试横版，不用回参数页重新生成。
     */
    suspend fun setOrientation(value: PageOrientation) {
        if (orientation == value) return
        orientation = value
        val gp = paper
        if (screen == Screen.PREVIEW && gp != null && !isReference) {
            busy = true
            try {
                reRender(gp.copy(spec = gp.spec.copy(orientation = value)))
                toast = "已切换为${value.label}，共 ${pdf?.pageCount ?: 0} 页，末页正文 $lastContentFillPercent%"
            } catch (t: Throwable) {
                toast = "切换方向失败：${t.message}"
            }
            busy = false
        } else {
            toast = "已切换为${value.label}，生成试卷时生效"
        }
    }

    // ---------------------------------------------------------------- 构建

    private fun buildOnce(
        seed: Long,
        useCounts: Map<QuestionType, Int>,
        save: Boolean,
        withPreviews: Boolean = save,
    ): Built {
        val spec = currentSpec(seed, useCounts)
        val recent = LocalStore.recentHashes(ctx, grade, subject)
        val gp = PaperComposer.compose(spec, recent)
        val rendered = PaperRenderer.render(PaperAssembler.build(gp))
        val pages = if (withPreviews) PdfPreviewRenderer.render(ctx, rendered.bytes) else emptyList()
        val savedId = if (save) LocalStore.savePaper(ctx, gp, rendered.fileName).id else ""
        return Built(gp, rendered, pages, savedId)
    }

    /**
     * 生成正式卷。
     *
     * 「补满整页」的**硬约束：补题不能让正文页数变多**。
     * 上一版只盯填充率补，遇到多题型卷（每个大题单独起页）会越补越多页：
     * 24 题被补成 72 题 / 4 页，末页还是空的，比不补更浪费纸。
     * 这一版逐轮试排，只保留「不增页 & 填充率最高」的那一轮定稿。
     */
    suspend fun generate(seed: Long = System.currentTimeMillis()) {
        if (totalQuestions <= 0) {
            toast = "至少选一种题型、题量大于 0"
            return
        }
        busy = true
        try {
            var working = LinkedHashMap(counts)
            var bestCounts = LinkedHashMap(counts)
            var bestRatio = -1f
            var bestExtra = 0
            var extraAdded = 0
            var basePages = -1
            var lastCount = -1

            for (attempt in 0..4) {
                val probe = withContext(Dispatchers.IO) { buildOnce(seed, working, save = false) }
                val pages = probe.pdf.contentPageCount
                if (basePages < 0) basePages = pages
                // 补到多出一张纸就停，回退到之前最好的版本
                if (pages > basePages) break

                val ratio = contentFillRatio(probe.pdf)
                if (ratio > bestRatio) {
                    bestRatio = ratio
                    bestCounts = LinkedHashMap(working)
                    bestExtra = extraAdded
                }
                // 词表 / 题库抽干后计数再涨也出不了新题，别空转
                val noProgress = lastCount >= 0 && probe.paper.questions.size <= lastCount
                lastCount = probe.paper.questions.size

                // 只有「正文就一页」才自动补：已经要印 2 页正文时再补只会越补越多，
                // 那种情况交给预览页的显式「补满这一页 / 加 N 题」。
                val fillType = pickFillType(working, probe)
                val need = if (autoFillPage && basePages == 1 && extraAdded < MAX_AUTO_EXTRA) {
                    suggestedExtra(ratio, fillType)
                } else 0
                if (need <= 0 || fillType == null || attempt == 4 || noProgress) break

                // 每轮只走估算量的 80%，靠多轮试排逼近 90%：
                // 一次补满容易越过页边界，被「不增页」约束整轮回退。
                val lineFactor = 1f + fillType.answerLines
                val step = (need * 0.8f / lineFactor).toInt()
                    .coerceIn(1, need)
                    .coerceAtMost(MAX_AUTO_EXTRA - extraAdded)
                if (step <= 0) break
                working[fillType] = (working[fillType] ?: 0) + step
                extraAdded += step
            }

            working = bestCounts
            extraAdded = bestExtra
            val result = withContext(Dispatchers.IO) { buildOnce(seed, working, save = true) }
            counts = LinkedHashMap(working)
            applyBuilt(result)

            val actual = result.paper.questions.size
            toast = buildString {
                append("已生成 $actual 题，共 ${result.pdf.pageCount} 页")
                if (extraAdded > 0) append("（自动补了 $extraAdded 题，未增加页数）")
                // 去重 / 历史卷避让少给两三题是常态，别动不动报「题量不足」吓人
                val shortage = totalQuestions - actual
                // 页面已经排满就不再拿「少几题」打扰用户；只有明显缺题或真的没排满才提示
                if (shortage > maxOf(4, totalQuestions / 5) || lastContentFillPercent < 70) {
                    append("　⚠ 可用题量不足 $shortage 题，可减少题量或增选其它题型")
                }
                append("　最后一页正文用了 $lastContentFillPercent%")
                if (lastContentFillPercent < 84) append("　可在预览页点「补满这一页」继续加题")
            }
            previewOrigin = if (screen == Screen.CREATE) Screen.CREATE else Screen.HOME
            screen = Screen.PREVIEW
        } catch (t: Throwable) {
            toast = "生成失败：${t.message ?: t.javaClass.simpleName}"
        }
        busy = false
    }

    /** 参数页实时预览：不落库、只渲染第 1 页缩略图 */
    suspend fun refreshLivePreview() {
        if (totalQuestions <= 0) {
            livePreview = null
            liveFillPercent = 0
            return
        }
        runCatching {
            val result = withContext(Dispatchers.IO) {
                val b = buildOnce(previewSeed, counts, save = false)
                val bmp = PdfPreviewRenderer.render(ctx, b.pdf.bytes, scale = 0.75f).firstOrNull()
                bmp to (contentFillRatio(b.pdf) * 100).toInt()
            }
            livePreview = result.first
            liveFillPercent = result.second
        }
    }

    /** 试卷库里点一张历史卷：同一种子会生成同一套题 */
    suspend fun reopen(saved: SavedPaper) {
        grade = saved.grade
        subject = runCatching { Subject.valueOf(saved.subject) }.getOrDefault(Subject.MATH)
        subjects = listOf(subject)
        counts = PaperComposer.defaultSpec(grade, subjects).counts
        titleOverride = saved.title
        busy = true
        try {
            val built = withContext(Dispatchers.IO) {
                val spec = LocalStore.toSpec(saved).copy(title = saved.title, seed = saved.createdAt)
                val gp = PaperComposer.compose(spec)
                val rendered = PaperRenderer.render(PaperAssembler.build(gp))
                Built(gp, rendered, PdfPreviewRenderer.render(ctx, rendered.bytes), saved.id)
            }
            applyBuilt(built)
            toast = "已重新打开：${saved.title}"
            previewOrigin = Screen.HISTORY
            screen = Screen.PREVIEW
        } catch (t: Throwable) {
            toast = "打开失败：${t.message}"
        }
        busy = false
    }

    suspend fun generateMistakePaper() {
        val pool = mistakes.filter { it.mastery != "MASTERED" }
            .groupBy { it.topic }
            .flatMap { (_, list) -> list.take(3) }
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
                        stem = m.stem, answer = m.answer, topic = m.topic,
                    )
                }
                val sections = qs.groupBy { it.type }.entries.mapIndexed { idx, (type, list) ->
                    GeneratedSection(
                        index = idx + 1,
                        title = "${idx + 1}、${type.label}（错题重练）",
                        type = type, questions = list,
                        columns = if (type.answerLines > 0) 1 else 2,
                        answerLines = type.answerLines,
                    )
                }
                val spec = currentSpec(System.currentTimeMillis()).copy(
                    title = "${subject.label}错题重练",
                    subtitle = "共 ${qs.size} 题　来自错题本",
                    isMistakePaper = true,
                    answerMode = AnswerMode.LAST_PAGE,
                    counts = emptyMap(),
                )
                val gp = GeneratedPaper(spec, sections, qs, System.currentTimeMillis())
                val rendered = PaperRenderer.render(PaperAssembler.build(gp))
                Built(
                    gp, rendered,
                    PdfPreviewRenderer.render(ctx, rendered.bytes),
                    LocalStore.savePaper(ctx, gp, rendered.fileName).id,
                )
            }
            LocalStore.markPracticed(ctx, pool.map { it.id })
            applyBuilt(built)
            toast = "已生成错题重练卷：${built.paper.questions.size} 题"
            previewOrigin = Screen.MISTAKES
            screen = Screen.PREVIEW
        } catch (t: Throwable) {
            toast = "生成失败：${t.message}"
        }
        busy = false
    }

    // ---------------------------------------------------------------- 预览页补题

    private data class AppendProbe(
        val paper: GeneratedPaper,
        val pdf: RenderedPdf,
        val ratio: Float,
        val pages: Int,
    )

    /**
     * 在现有卷子末尾追加 n 道题并试排一次（不落库、不渲染预览）。
     *
     * 新题追加到「最后一个大题」—— 它正好落在最后一页正文上，补它才真的把
     * 那张纸填满；补到第一个大题反而会把后面的版面整体往后推，越补越浪费纸
     * （v0.3.0 就踩过这个坑）。
     */
    private fun probeAppend(base: GeneratedPaper, n: Int): AppendProbe? {
        if (n <= 0) return null
        val last = base.sections.lastOrNull() ?: return null
        val generator = GeneratorRegistry.of(last.type, base.spec.grade) ?: return null
        val ctx2 = GenContext(
            grade = base.spec.grade,
            count = n,
            difficulty = base.spec.difficulty,
            rng = Random(System.nanoTime()),
            blocked = base.questions.map { stableHash(it.stem) }.toMutableSet(),
            subjects = base.spec.effectiveSubjectsForState().toSet(),
        )
        val fresh = generator.generate(ctx2)
        if (fresh.isEmpty()) return null

        val mutable = base.sections.toMutableList()
        val idx = mutable.lastIndex
        mutable[idx] = mutable[idx].copy(questions = mutable[idx].questions + fresh)
        val sections = renumber(mutable)
        val gp = base.copy(sections = sections, questions = sections.flatMap { it.questions })
        val rendered = PaperRenderer.render(PaperAssembler.build(gp))
        return AppendProbe(gp, rendered, contentFillRatio(rendered), rendered.contentPageCount)
    }

    fun changeExtraAdd(delta: Int) {
        extraAddCount = (extraAddCount + delta).coerceIn(1, 30)
    }

    /** 自定义加 N 题：若会多出一张纸，自动退到「不增页」的最大题数 */
    suspend fun addQuestions(n: Int) {
        val gp = paper ?: return
        if (isReference || n <= 0) return
        busy = true
        try {
            val basePages = withContext(Dispatchers.IO) {
                PaperRenderer.render(PaperAssembler.build(gp)).contentPageCount
            }
            val candidates = buildList {
                add(n)
                listOf(0.75f, 0.5f, 0.25f).forEach { f ->
                    val v = (n * f).toInt()
                    if (v > 0) add(v)
                }
                add(1)
            }.distinct()

            var chosen: AppendProbe? = null
            var exhausted = false
            for (candidate in candidates) {
                val probe = withContext(Dispatchers.IO) { probeAppend(gp, candidate) }
                if (probe == null) {
                    exhausted = true
                    continue
                }
                if (probe.pages <= basePages && probe.paper.questions.size > gp.questions.size) {
                    chosen = probe
                    break
                }
            }
            if (chosen != null) {
                applyAppend(chosen, "已加")
            } else {
                toast = if (exhausted) {
                    "这类题暂时抽不出新题了，可回参数页换题型"
                } else {
                    "这一页已经放不下了：再加题会多出一张纸，已取消"
                }
            }
        } catch (t: Throwable) {
            toast = "加题失败：${t.message}"
        }
        busy = false
    }

    /** 「补满这一页」：逐轮试排，只保留「不增页 & 填充率最高」的那一轮 */
    suspend fun fillLastPage() {
        val gp = paper ?: return
        if (isReference) return
        busy = true
        try {
            val basePages = withContext(Dispatchers.IO) {
                PaperRenderer.render(PaperAssembler.build(gp)).contentPageCount
            }
            var best: AppendProbe? = null
            var bestRatio = pdf?.let { contentFillRatio(it) } ?: 0f
            var total = 0

            for (attempt in 0..5) {
                if (bestRatio >= FILL_TARGET) break
                val step = suggestedExtra(bestRatio)
                if (step <= 0) break
                val want = total + step
                if (want > 40) break
                val probe = withContext(Dispatchers.IO) { probeAppend(gp, want) } ?: break
                // 补到多出一张纸、或填充率没有长进，就停，保留上一轮最好的版本
                if (probe.pages > basePages || probe.ratio <= bestRatio + 0.005f) break
                best = probe
                bestRatio = probe.ratio
                total = want
            }

            if (best != null) {
                applyAppend(best, "自动补满，已加")
            } else {
                toast = "当前版面已经排得比较满，再加题会多出一张纸"
            }
        } catch (t: Throwable) {
            toast = "补题失败：${t.message}"
        }
        busy = false
    }

    private suspend fun applyAppend(probe: AppendProbe, prefix: String) {
        val baseSize = paper?.questions?.size ?: 0
        val added = probe.paper.questions.size - baseSize
        val total = probe.paper.questions.size
        // 卷头副标题写着「共 N 题」，加题后必须同步，不然印出来数字对不上
        val fixed = probe.paper.copy(
            spec = probe.paper.spec.copy(subtitle = "共 $total 题　满分 ${total * 2} 分"),
        )
        reRender(fixed)
        val type = fixed.sections.lastOrNull()?.type
        if (type != null) {
            val map = LinkedHashMap(counts)
            map[type] = (map[type] ?: 0) + added
            counts = map
        }
        toast = "$prefix $added 题，共 $total 题 / ${probe.pdf.pageCount} 页，" +
            "末页正文 $lastContentFillPercent%，未增加页数"
    }

    // ---------------------------------------------------------------- 单题编辑

    private fun freshQuestion(gp: GeneratedPaper, type: QuestionType): GeneratedQuestion? {
        val generator = GeneratorRegistry.of(type, gp.spec.grade) ?: return null
        val ctx2 = GenContext(
            grade = gp.spec.grade,
            count = 1,
            difficulty = gp.spec.difficulty,
            rng = Random(System.nanoTime()),
            blocked = gp.questions.map { com.papermill.app.domain.generate.stableHash(it.stem) }.toMutableSet(),
            subjects = gp.spec.effectiveSubjectsForState().toSet(),
        )
        return generator.generate(ctx2).firstOrNull()
    }

    /** 所有题目重新连续编号，避免删掉中间一题后题号出现跳号 */
    private fun renumber(sections: List<GeneratedSection>): List<GeneratedSection> {
        var cursor = 0
        return sections.map { s ->
            val qs = s.questions.mapIndexed { i, q -> q.copy(no = cursor + i + 1) }
            cursor += qs.size
            s.copy(questions = qs)
        }
    }

    /** 换一题：只重出这一道，其它题不动 */
    suspend fun replaceQuestion(no: Int) {
        val gp = paper ?: return
        val old = gp.questions.firstOrNull { it.no == no } ?: return
        busy = true
        try {
            val fresh = withContext(Dispatchers.IO) { freshQuestion(gp, old.type) }
            if (fresh == null) {
                toast = "这类题暂时抽不出新题了，可换其它题型"
            } else {
                val sections = gp.sections.map { s ->
                    s.copy(questions = s.questions.map { q -> if (q.no == no) fresh.copy(no = no) else q })
                }
                reRender(gp.copy(sections = sections, questions = sections.flatMap { it.questions }))
                toast = "第 $no 题已换"
            }
        } catch (t: Throwable) {
            toast = "换题失败：${t.message}"
        }
        busy = false
    }

    /** 删一题：后续题号自动顺延，重新排版 */
    suspend fun removeQuestion(no: Int) {
        val gp = paper ?: return
        if (gp.questions.size <= 1) {
            toast = "至少留一道题"
            return
        }
        busy = true
        try {
            val stripped = gp.sections.map { s -> s.copy(questions = s.questions.filter { it.no != no }) }
            val sections = renumber(stripped)
            reRender(gp.copy(sections = sections, questions = sections.flatMap { it.questions }))
            toast = "已删除第 $no 题，题号已顺延"
        } catch (t: Throwable) {
            toast = "删除失败：${t.message}"
        }
        busy = false
    }

    /** 加一题：跟最后一道题同题型，追加到对应大题末尾并整卷重新编号 */
    suspend fun appendQuestion() {
        val gp = paper ?: return
        val lastType = gp.questions.lastOrNull()?.type ?: return
        busy = true
        try {
            val fresh = withContext(Dispatchers.IO) { freshQuestion(gp, lastType) }
            if (fresh == null) {
                toast = "这类题暂时抽不出新题了，可回参数页换题型"
            } else {
                val mutable = gp.sections.toMutableList()
                val target = mutable.indexOfLast { it.type == lastType }.takeIf { it >= 0 }
                    ?: mutable.lastIndex
                mutable[target] = mutable[target].copy(questions = mutable[target].questions + fresh)
                val sections = renumber(mutable)
                reRender(gp.copy(sections = sections, questions = sections.flatMap { it.questions }))
                toast = "已在末尾加 1 题，共 ${sections.flatMap { it.questions }.size} 题"
            }
        } catch (t: Throwable) {
            toast = "加题失败：${t.message}"
        }
        busy = false
    }

    private suspend fun reRender(gp: GeneratedPaper) {
        val rendered = withContext(Dispatchers.IO) {
            val r = PaperRenderer.render(PaperAssembler.build(gp))
            r to PdfPreviewRenderer.render(ctx, r.bytes)
        }
        paper = gp
        pdf = rendered.first
        previews = rendered.second
        previewPage = previewPage.coerceIn(0, (rendered.second.size - 1).coerceAtLeast(0))
        wrongNumbers = emptySet()
    }

    // ---------------------------------------------------------------- 速查表 / 每日一练

    suspend fun renderReference(sheet: ReferenceSheet) {
        busy = true
        try {
            val result = withContext(Dispatchers.IO) {
                val r = PaperRenderer.render(ReferenceRenderer.build(sheet))
                r to PdfPreviewRenderer.render(ctx, r.bytes)
            }
            pdf = result.first
            previews = result.second
            previewPage = 0
            paper = null
            savedPaperId = ""
            wrongNumbers = emptySet()
            showQuestionList = false
            isReference = true
            toast = "《${sheet.title}》共 ${result.first.pageCount} 页，可直接打印"
            previewOrigin = Screen.REFERENCE
            screen = Screen.PREVIEW
        } catch (t: Throwable) {
            toast = "生成速查表失败：${t.message}"
        }
        busy = false
    }

    /** 练习纸：一页铺满，横版那张会同时把打印框切到 A4 横向 */
    suspend fun renderPractice(sheet: PracticeSheet) {
        busy = true
        try {
            val result = withContext(Dispatchers.IO) {
                val r = PaperRenderer.render(PracticeRenderer.build(sheet))
                r to PdfPreviewRenderer.render(ctx, r.bytes)
            }
            pdf = result.first
            previews = result.second
            previewPage = 0
            paper = null
            savedPaperId = ""
            wrongNumbers = emptySet()
            showQuestionList = false
            isReference = true
            toast = "《${sheet.title}》1 页 ${sheet.orientation.shortLabel}，已铺满整页，可直接打印"
            previewOrigin = Screen.REFERENCE
            screen = Screen.PREVIEW
        } catch (t: Throwable) {
            toast = "生成练习纸失败：${t.message}"
        }
        busy = false
    }

    /** 每天一份不重样：以当天日期为种子，混合该年级 3 门主科 */
    suspend fun generateDaily() {
        val picked = Subject.values().filter { it.availableFor(grade) }.take(3)
        subjects = picked
        subject = picked.first()
        val types = GeneratorRegistry.availableFor(grade, picked)
        val map = LinkedHashMap<QuestionType, Int>()
        types.firstOrNull { it == QuestionType.ORAL || it == QuestionType.RATIONAL }
            ?.let { map[it] = 20 }
        types.filter { it.answerLines == 0 && it != QuestionType.KNOWLEDGE && it !in map }
            .take(2).forEach { map[it] = 8 }
        if (QuestionType.KNOWLEDGE in types) map[QuestionType.KNOWLEDGE] = 6
        counts = map
        titleOverride = "${GradeProfiles.gradeLabel(grade)} · 每日一练 · ${todayText()}"
        generate(daySeed())
    }

    // ---------------------------------------------------------------- 打印 / 导出

    private fun applyBuilt(b: Built) {
        paper = b.paper
        pdf = b.pdf
        previews = b.previews
        previewPage = 0
        savedPaperId = b.savedId
        wrongNumbers = emptySet()
        showQuestionList = false
        isReference = false
        refreshLists()
    }

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
        val file = File(PdfExporter.papersDir(ctx), "卷子备份_${System.currentTimeMillis()}.json")
        file.writeText(LocalStore.exportAll(ctx))
        toast = "备份已存到：${file.absolutePath}"
        return file
    }

    fun importBackup(uri: Uri) {
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        toast = if (text != null && LocalStore.importAll(ctx, text)) {
            refreshLists(); "备份已恢复"
        } else "备份文件读取失败或格式不对"
    }

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

    // ---------------------------------------------------------------- 更新

    suspend fun checkUpdate() {
        updateInfo = "正在检查更新…"
        val release = UpdateChecker.check()
        updateRelease = release
        updateInfo = when {
            release == null -> "检查失败（可能访问不到 GitHub），可到「打印帮助」里手动打开仓库"
            UpdateChecker.isNewer(release.version, versionName) ->
                "发现新版本 ${release.version}：${release.name}"
            else -> "已是最新版本 $versionName"
        }
    }

    fun openUrl(url: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { toast = "打开失败：${it.message}" }
    }

    fun openPrintSettings() = PrintServiceProbe.openSettings(ctx)
}

private fun daySeed(): Long {
    val sdf = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    return sdf.format(Date()).toLongOrNull() ?: System.currentTimeMillis()
}

private fun todayText(): String = SimpleDateFormat("M 月 d 日", Locale.CHINA).format(Date())

private fun PaperSpec.effectiveSubjectsForState(): List<Subject> =
    if (subjects.isEmpty()) listOf(subject) else subjects
