package com.papermill.app.feature

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.papermill.app.core.store.AppSettings
import com.papermill.app.core.store.LocalStore
import com.papermill.app.core.store.MistakeItem
import com.papermill.app.core.store.SavedPaper
import com.papermill.app.core.store.SavedQuestion
import com.papermill.app.core.store.SessionState
import com.papermill.app.core.update.UpdateChecker
import com.papermill.app.domain.content.CurricUnit
import com.papermill.app.domain.content.CurriculumBank
import com.papermill.app.domain.content.ScienceBank
import com.papermill.app.domain.content.TextbookCatalog
import com.papermill.app.domain.generate.AnswerMode
import com.papermill.app.domain.generate.BankQuestionSource
import com.papermill.app.domain.generate.CustomSheet
import com.papermill.app.domain.generate.Density
import com.papermill.app.domain.generate.MathPoint
import com.papermill.app.domain.generate.GenContext
import com.papermill.app.domain.generate.GeneratedPaper
import com.papermill.app.domain.generate.GeneratedQuestion
import com.papermill.app.domain.generate.GeneratedSection
import com.papermill.app.domain.generate.FooterSize
import com.papermill.app.domain.generate.GeneratorRegistry
import com.papermill.app.domain.generate.GradeProfiles
import com.papermill.app.domain.generate.LineGap
import com.papermill.app.domain.generate.PaperComposer
import com.papermill.app.domain.generate.PaperKind
import com.papermill.app.domain.generate.PaperSpec
import com.papermill.app.domain.generate.PageOrientation
import com.papermill.app.domain.generate.QuestionType
import com.papermill.app.domain.generate.Subject
import com.papermill.app.domain.generate.Term
import com.papermill.app.domain.generate.Tilt
import com.papermill.app.domain.generate.stableHash
import com.papermill.app.domain.profile.Gender
import com.papermill.app.domain.profile.Student
import com.papermill.app.domain.reference.PracticeSheet
import com.papermill.app.domain.reference.ReferenceSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.ceil

/** 用户自己选的本地 PDF（只读地活在 cacheDir 里，不打包、不上传） */
data class LocalPdf(
    val file: File,
    val name: String,
    val pages: Int,
    val preview: ImageBitmap?,
    /** 首页宽 > 高：打印框按横版 A4 匹配，否则会被驱动再转一次 */
    val landscape: Boolean,
)

enum class Screen { HOME, CREATE, PREVIEW, MISTAKES, HISTORY, HELP, REFERENCE, MORE, TEXTBOOK, LOCAL_PDF, STUDENTS }

/**
 * 底部 dock 的页签。
 *
 * v0.18.0 起把「显示文字」和「对应页面」收进枚举本身：
 * 原来 `Screens.kt` 里有一份 `TAB_ORDER`、`CommonUi.kt` 里又有一份 `DOCK_TABS`，
 * 两处硬编码靠一句注释「顺序必须一致」维持，一加页签就错位。
 * 现在唯一的事实来源是这里，加一个页签只改一处。
 *
 * 注意：[label] 会直接印在 dock 上，所以保持 2~3 个字，格子窄时也放得下。
 */
enum class NavTab(val label: String, val screen: Screen) {
    HOME("出卷", Screen.HOME),
    REFERENCE("打印纸", Screen.REFERENCE),
    MISTAKES("错题本", Screen.MISTAKES),
    HISTORY("试卷库", Screen.HISTORY),
    TEXTBOOK("课本", Screen.TEXTBOOK),
    MORE("更多", Screen.MORE),
}

/** 默认全部显示；用户可以在「更多 → tab 栏」里关掉用不上的 */
private val DEFAULT_DOCK_TABS: List<NavTab> = NavTab.entries.toList()

/** 所有「可以出现在 dock 里」的页面 */
private val ALL_TAB_SCREENS: Set<Screen> = NavTab.entries.map { it.screen }.toSet()

/**
 * 一次「批改记错题」的现场（v0.18.0）。
 *
 * 记的是**题目快照**而不是 paper 对象：从试卷库直接批改时不需要重新渲染 PDF，
 * 打开面板就是一张题号表，点几下就进错题本了。
 * 题目少的时候点格子，题目多的时候直接敲「3 7 12-15」，两条路都留着。
 */
data class GradingSession(
    val paperId: String,
    val title: String,
    val grade: Int,
    val subjectCode: String,
    val items: List<SavedQuestion>,
)

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

/** 一条答题横线占的高度：与 QuestionBlock 的 lineHeight × 1.55 对齐 */
private const val TYPICAL_ANSWER_LINE_H = 24.8f
private const val FILL_TARGET = 0.84f
/** 自动补题总量上限：想要更多由用户在预览页显式点「加 N 题」 */
private const val MAX_AUTO_EXTRA = 24

/** A/B 卷的标号。顺带决定了「生成张数」的最大值 */
private val COPY_LABELS = listOf("A 卷", "B 卷")

/**
 * B 卷相对 A 卷的种子偏移。
 *
 * 取一个质数，避免 seed 与 seed+1 这种相邻值在某些随机实现里抽出高度相似的题序；
 * 又不能用 0 —— 同种子会出完全一样的两张卷，A/B 卷就失去意义了。
 */
private const val COPY_SEED_STEP = 7919L

/** 导出图片的渲染倍率：A4 竖版 595×842pt × 2.2 ≈ 1309×1852px，放微信里看/打印都清楚 */
private const val EXPORT_IMAGE_SCALE = 2.2f

/**
 * 卷面排版口径的版本（v0.18.1）。
 *
 * 每次「改了默认样式、且老用户的旧设置必须跟着变」就 +1，并在 [AppState.applySettings]
 * 里写对应的迁移。只改字段默认值对老用户是无效的 —— 他磁盘上那份设置会被原样读回来。
 */
private const val LAYOUT_VERSION = 1

class AppState(private val rawContext: Context) {

    private val ctx = rawContext.applicationContext

    /**
     * 落盘用的后台协程域（v0.17.1）。
     *
     * 「记住上次看到哪儿」原来是在主线程直接写 JSON 文件：滚停 1.2 秒后整份
     * session 序列化 + 写盘，正好卡在手指刚离开屏幕、用户以为要响应的那一刻。
     * 一次写 1~2KB 看着不大，但 SharedPreferences 之外的文件写要过一遍
     * fsync，落在一帧里就是 8~20ms 的卡顿 —— 滑动时最容易被察觉。
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 开机就去读「上次停在哪儿」，而不是等界面挂起来再读。
     *
     * 踩过的坑：一开始在 `LaunchedEffect` 里异步复原（goTab 切页面）。可底部
     * 页签的 pager 有个「落页后校正」的监听，它拿初始的 settledPage=0（首页）
     * 往回对齐，把刚复原好的 REFERENCE 又拽回首页 —— 看起来就是「记忆没生效」。
     * 现在初始 screen 直接由这份 session 决定，pager 建出来第一帧就是对的。
     */
    private val bootSession: SessionState =
        runCatching { LocalStore.loadSession(ctx) }.getOrDefault(SessionState())

    var screen by mutableStateOf(bootScreen())

    var grade by mutableStateOf(3)
    var subject by mutableStateOf(Subject.MATH)
    var subjects by mutableStateOf(listOf(Subject.MATH))
    var counts by mutableStateOf<Map<QuestionType, Int>>(emptyMap())

    var difficulty by mutableStateOf(2)
    /** 题型倾向：决定这份卷以哪类题为主 */
    var tilt by mutableStateOf(Tilt.BALANCED)
    /**
     * 卷型：全部 / 单元测试 / 期中测试 / 期末测试 / 练习题 / 拼音 / 复习题 /
     * 综合测试 / 竞赛 / 奥数 / 小升初（决定铺多宽、题量多大、优先挑哪类题）
     */
    var kind by mutableStateOf(PaperKind.ALL)
    var columns by mutableStateOf(2)
    var answerMode by mutableStateOf(AnswerMode.LAST_PAGE)
    var showNameRow by mutableStateOf(true)
    var titleOverride by mutableStateOf<String?>(null)
    var autoFillPage by mutableStateOf(true)
    /** 竖版 / 横版 A4，预览页可直接改并即时重排 */
    var orientation by mutableStateOf(PageOrientation.PORTRAIT)

    // ---------------- v0.17.0 卷面定制（都进 settings.json，下次开 App 还在） ----------------

    /** 标题与「共 N 题 满分 M 分」排一行：省下 17pt 卷头高度 */
    var headerOneLine by mutableStateOf(false)

    /**
     * 页脚模式：0 = 不写字（只印右侧时间与页码），1 = 自定义，2 = 不印。
     *
     * 为什么用三个状态而不是「留空 = 不印」：留空是「我懒得写」还是「我不要页脚」
     * 分不出来，而这两种意图的结果差 26pt 纸面高度。
     */
    var footerMode by mutableStateOf(0)
    var footerCustom by mutableStateOf("")

    /** 页脚右侧印生成时间 */
    var showCreatedAt by mutableStateOf(true)

    /** 页脚右侧印「第 N/M 页」（v0.18.1；第一页也有，页眉只有第 2 页起才有） */
    var showPageNo by mutableStateOf(true)

    /** 页脚高度档位：矮 / 标准 / 高，直接决定正文可用高度 */
    var footerSize by mutableStateOf(FooterSize.NORMAL)

    /**
     * 题间行距档位。
     *
     * v0.18.1 默认从「标准」改成「紧凑」：用户的原话是「行与行之间不要那么宽，
     * 再窄一点」。想要更松的自己往上选，别让所有人一起忍受宽行距。
     */
    var lineGap by mutableStateOf(LineGap.COMPACT)

    /**
     * 行距选「自定义」时用的具体 pt 值（v0.18.1）。
     *
     * 用户的原话是「行与行之间不要那么宽，再窄一点，或者后台可以自定义」——
     * 原来只有三档预设，而且**撑开算法会把题间距拉到 26~34pt**（为了填满纸），
     * 三档之间的差别被吃掉大半。现在自定义值同时作为撑开的上界，
     * 选了它就严格按这个间距排，不再偷偷拉开。
     */
    var lineGapCustom by mutableStateOf(8f)

    /**
     * 正文页数上限（v0.18.3）。0 = 不限制。
     *
     * 用户的原话是「可以选择试卷生成几页，这样的话题目可以根据试卷自动压缩
     * 显示的内容大小」。渲染器拿到的题量如果超出这个数，会二分缩小正文字号
     * 硬压进去（下限 0.72 倍）；压不进就老实多印一页，不印看不清的卷子。
     *
     * 只约束正文，答案页另算。
     */
    var targetPages by mutableStateOf(0)

    // ---------------- v0.22.0 排版自由度 ----------------

    /**
     * 纸边留白（pt）：12 极窄 / 20 窄 / 28 中 / 40 标准。
     *
     * 为什么它比「缩字号」更该先给用户：同样题量要少印一页，收窄页边距只损失四周空白，
     * 缩字号却直接损失可读性。所以「排版密度」预设里第一个动的就是它。
     */
    var margin by mutableStateOf(40f)

    /** 正文字号（pt）：9 / 10 / 11 / 12。大题标题与说明按固定比例跟随，不会比例失衡 */
    var bodySize by mutableStateOf(11f)

    /**
     * 当前这组「页边距 / 字号 / 行距」落在哪个密度档上。
     *
     * 只用于界面高亮：三个值分别存，用户单独微调任何一项都会自然落到「自定义」，
     * 不会出现「界面说紧凑、实际已经改了字号」的假开关。
     */
    val density: Density
        get() = Density.of(margin, bodySize, lineGap)

    /** 一键套用密度：三个数一起摆好，之后仍可单独改 */
    fun applyDensity(d: Density) {
        margin = d.margin
        bodySize = d.bodySize
        lineGap = d.lineGap
        saveSettings()
    }

    // ---------------- v0.23.0 知识点专项 ----------------

    /**
     * 选中的数学知识点（v0.23.0，照试卷库的「知识点导航」搬的 15 个）。
     *
     * 非空 = 这份卷子是专项卷：一个知识点一道大题、只考这一课，题型配比整块失效。
     * 为什么单独一个维度：常规卷是「按题型配比拼一张纸」，专项卷是「20 道全是它」，
     * 后者才是家长真会买的教辅形态（口算天天练、有余数除法专项）。
     */
    var points by mutableStateOf<List<MathPoint>>(emptyList())
        private set

    /** 专项卷里每个知识点出几题（网站的范围 5~50） */
    var pointCount by mutableStateOf(20)
        private set

    fun choosePointCount(n: Int) {
        pointCount = n.coerceIn(5, 50)
        titleOverride = null
        previewSeed = System.currentTimeMillis()
        saveSettings()
    }

    /**
     * 勾 / 取消一个知识点。
     *
     * 顺手做三件事：科目切到数学（知识点全是数学的，勾着英语点它会出一张
     * 一道数学题都没有的「专项卷」）、年级跟着知识点走（点「表内乘法」而卷头
     * 还写着三年级，家长会以为点错了）、标题立刻换成知识点名。
     */
    fun togglePoint(p: MathPoint) {
        val set = points.toMutableSet()
        if (!set.remove(p)) set.add(p)
        // 按枚举顺序存：界面与卷面顺序就永远一致，不受点击先后影响
        points = MathPoint.values().filter { it in set }
        if (points.isNotEmpty()) {
            subjects = listOf(Subject.MATH)
            subject = Subject.MATH
            grade = points.maxOf { it.grade }
            normalizeTilt()
            normalizeKind()
        }
        clearUnitFilter()
        titleOverride = null
        previewSeed = System.currentTimeMillis()
        saveSettings()
    }

    fun clearPoints() {
        points = emptyList()
        titleOverride = null
        previewSeed = System.currentTimeMillis()
        saveSettings()
    }

    // ---------------- v0.19.0 学生档案 / 学期 / 自编题 ----------------

    /**
     * 学生档案。点名字 = 年级 + 学期 + 科目一次就位，直接出卷。
     *
     * 数据只在本机 `students.json`，不联网、不上传；只收姓名 / 年级 / 学期 / 性别 / 科目。
     */
    var students by mutableStateOf(emptyList<Student>())
        private set
    var activeStudentId by mutableStateOf("")
    /** 学期：0 全学年 / 1 上册 / 2 下册。只影响真带学期数据的资源（课标题库 / 课本目录） */
    var term by mutableStateOf(Term.FIRST)
    /** 自编题：用户自己写的题，按学生归属 */
    var customSheets by mutableStateOf(emptyList<CustomSheet>())
        private set

    val activeStudent: Student? get() = students.firstOrNull { it.id == activeStudentId }

    /** 当前学生能用的自编题：共用（studentId 空）+ 他自己的 */
    val visibleCustomSheets: List<CustomSheet>
        get() = customSheets.filter { it.studentId.isEmpty() || it.studentId == activeStudentId }

    /** 自编题一共会印几道（去重 + 去无效行之后） */
    val customQuestionCount: Int get() = visibleCustomSheets.sumOf { CustomSheet.validCount(it) }

    /** 页脚最终要印的文字：null 走默认，"" 表示不印 */
    private fun footerTextForSpec(): String? = when (footerMode) {
        1 -> footerCustom.trim()
        2 -> ""
        else -> null
    }

    var paper by mutableStateOf<GeneratedPaper?>(null)
    var pdf by mutableStateOf<RenderedPdf?>(null)
    var previews by mutableStateOf<List<ImageBitmap>>(emptyList())
    var savedPaperId by mutableStateOf("")

    /**
     * 一次出几张：1 张普通卷，2 张出 A / B 卷。
     *
     * A/B 卷的用法是防抄：同桌拿到的是同一套结构、不同的题目。
     * 所以 B 卷**沿用 A 卷补满后的题量**，只换种子 —— 两张卷的大题结构、每大题题数
     * 完全一致，批改时对得上号；如果让 B 卷自己再补一轮，很可能补出不同题数，
     * 反而变成两份不同的卷子。
     */
    var copies by mutableStateOf(1)

    /** 这一批生成的卷（通常 1 张，A/B 卷时 2 张），供预览页在 A / B 之间切换 */
    private var batchBuilt by mutableStateOf<List<Built>>(emptyList())

    /** 当前预览的是批里的第几张 */
    var activeCopy by mutableStateOf(0)
        private set

    /** 这一批共几张（A/B 卷 = 2）；预览页据此决定要不要显示 A/B 切换 */
    val batchSize: Int get() = batchBuilt.size

    /** 当前卷的标号（「A 卷」/「B 卷」），单张卷返回空串 */
    val copyLabel: String get() = if (batchBuilt.size <= 1) "" else COPY_LABELS.getOrElse(activeCopy) { "" }

    fun selectCopy(index: Int) {
        if (index !in batchBuilt.indices || index == activeCopy) return
        activeCopy = index
        applyBuilt(batchBuilt[index], clearBatch = false)
        previewPage = 0
        toast = "${copyLabel}：共 ${batchBuilt[index].pdf.pageCount} 页"
    }
    var wrongNumbers by mutableStateOf<Set<Int>>(emptySet())
    var showQuestionList by mutableStateOf(false)

    /**
     * 题目清单的「批量删题」模式（v0.18.3）。
     *
     * 为什么单开一个模式而不是复用错题勾选：那个 checkbox 是「这道做错了」，
     * 勾完是点「加入错题本」；如果同一套勾选还能一键删除，家长勾好错题、
     * 手滑点到旁边的删除，半张卷子就没了。两个动作共用一份选择，迟早出事。
     */
    var batchSelect by mutableStateOf(false)
        private set

    /** 批量删题模式下已选中的题号 */
    var selectedNos by mutableStateOf<Set<Int>>(emptySet())
        private set

    fun enterBatchSelect() {
        batchSelect = true
        selectedNos = emptySet()
    }

    fun exitBatchSelect() {
        batchSelect = false
        selectedNos = emptySet()
    }

    fun toggleSelect(no: Int) {
        selectedNos = if (no in selectedNos) selectedNos - no else selectedNos + no
    }

    fun selectAllQuestions() {
        selectedNos = paper?.questions?.map { it.no }?.toSet() ?: emptySet()
    }

    fun clearSelection() {
        selectedNos = emptySet()
    }

    /**
     * 按题号表达式选中，如「3 7 12-15」。
     *
     * 复用判分面板那套解析口径：空格/逗号/顿号分隔，`-~～` 都算区间。
     * 题多的卷子（60 道口算）逐格点太累，敲一串数字快得多。
     *
     * @return 实际命中的题数（用户敲了卷里没有的题号时不吭声，只按命中的算）
     */
    fun selectByExpr(raw: String): Int {
        val valid = paper?.questions?.map { it.no }?.toSet() ?: emptySet()
        val hit = parseNumberList(raw, valid)
        if (hit.isEmpty()) {
            // 敲了卷里没有的号：不静默 —— 用户会以为输入框坏了
            toast = "这些题号不在卷里（卷子共 ${valid.size} 题）"
            return 0
        }
        selectedNos = selectedNos + hit
        toast = "已选中 ${selectedNos.size} 题"
        return hit.size
    }

    /**
     * 「加一题」选题型的面板开没开（v0.18.2）。
     *
     * 放在 state 里而不是 Composable 的 remember：面板挂在预览页的 LazyColumn 内部，
     * 滚出屏幕那段 item 会整块销毁，remember 里的 true 跟着没了 ——
     * 用户滚一下面板就自己关掉。
     */
    var addingQuestion by mutableStateOf(false)
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
    /** 实时预览里「自动补满」额外加了多少题（0 = 没补或没开） */
    var liveAutoExtra by mutableStateOf(0)
    /** 实时预览的真实正文页数 */
    var livePages by mutableStateOf(0)
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

    /** 底部 tab 栏用玻璃半透明（默认关：不透明实底，透出的卡片文字会干扰阅读） */
    var dockGlass by mutableStateOf(false)
        private set

    // ---------------- v0.18.0 底部 tab 栏可自定义 ----------------

    /**
     * dock 上显示哪几个页签，顺序即顺序。
     *
     * 为什么连顺序也存：页签数量一多，常用的那个总在最右边很难受；
     * 存成一串名字后，将来做「长按拖动排序」只是改这个列表，不用再动存储格式。
     */
    var dockTabs by mutableStateOf(DEFAULT_DOCK_TABS)
        private set

    /** 打开一个页签；它被隐藏时退到「更多」，不会白屏 */
    fun isTabScreen(s: Screen): Boolean = dockTabs.any { it.screen == s }

    /**
     * 开 / 关某个页签。
     *
     * 三条护栏，缺一条都会出现「进不去也回不来」的死界面：
     * ① 「更多」不能关 —— 它是这套开关自己的入口，关掉就再也打不开了；
     * ② 至少留两个 —— 只剩一个时 pager 的滑动、胶囊动画都没有意义；
     * ③ 正停留在被关掉的那一页时，先退回「更多」再关，否则当前页会从 pager 里消失。
     */
    fun toggleDockTab(tab: NavTab): String? {
        if (tab == NavTab.MORE) return "「更多」是设置入口，不能隐藏"
        val on = tab in dockTabs
        if (on && dockTabs.size <= 2) return "至少保留两个页签"
        // 加回来时按枚举顺序插回原位：「更多」永远在最后，
        // 否则关掉再打开的那一格会跑到「更多」右边，看着像被挤出去了
        dockTabs = if (on) dockTabs - tab else (dockTabs + tab).sortedBy { it.ordinal }
        if (on && screen == tab.screen) screen = Screen.MORE
        saveSettings()
        return null
    }

    // ---------------- v0.18.0 批改记错题 ----------------

    /** 非 null = 批改面板正开着 */
    var grading by mutableStateOf<GradingSession?>(null)

    /** 从预览页当前这张卷开始批改 */
    fun startGrading() {
        val gp = paper ?: run { toast = "还没有生成卷子"; return }
        grading = GradingSession(
            paperId = savedPaperId,
            title = gp.spec.title,
            grade = gp.spec.grade,
            subjectCode = gp.spec.subject.code,
            items = gp.questions.map {
                SavedQuestion(it.no, it.stem, it.answer, it.type.name, it.topic)
            },
        )
    }

    /** 从试卷库的历史卷开始批改：不重新排版，直接用题目快照 */
    fun startGrading(saved: SavedPaper) {
        grading = GradingSession(
            paperId = saved.id,
            title = saved.title,
            grade = saved.grade,
            subjectCode = saved.subject,
            items = saved.questions,
        )
    }

    fun finishGrading(numbers: Set<Int>) {
        val g = grading ?: return
        if (numbers.isEmpty()) {
            toast = "没有勾任何题"
            return
        }
        val added = LocalStore.addMistakesForPaper(
            ctx, g.grade, g.subjectCode, g.paperId, g.items, numbers,
        )
        grading = null
        refreshLists()
        toast = if (added == numbers.size) {
            "已加入错题本：${added} 道"
        } else {
            "已加入 ${added} 道，另外 ${numbers.size - added} 道之前错过（已重置为待练）"
        }
    }

    /**
     * 按单元出卷时的知识点白名单（空 = 不限制）。
     *
     * 从「课本」页点某个单元的「出卷」进来时会填上，
     * 用户一旦改年级 / 改科目 / 从首页重新出卷就必须清掉 ——
     * 否则会出现「换了科目，出来的还是上个单元那几道题」这种鬼故事。
     */
    var unitTopicIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var unitTitle by mutableStateOf("")
        private set

    fun clearUnitFilter() {
        if (unitTopicIds.isEmpty() && unitTitle.isEmpty()) return
        unitTopicIds = emptySet()
        unitTitle = ""
        titleOverride = null
    }

    fun updateDockGlass(on: Boolean) {
        if (dockGlass == on) return
        dockGlass = on
        saveSettings()
    }

    /**
     * 把「dock 玻璃 + 卷面定制」一起落盘。
     *
     * v0.17.0 之前这里每次只存 dockGlass，于是 `AppSettings(showNameRow = ...)` 这种
     * 半新半旧的对象会把别的字段冲回默认值 —— 用户改了页脚，顺手切一下 dock 玻璃，
     * 页脚就悄悄变回去了。所以所有写入统一走这一个出口。
     */
    fun saveSettings() {
        runCatching {
            LocalStore.saveSettings(
                ctx,
                AppSettings(
                    dockGlass = dockGlass,
                    showNameRow = showNameRow,
                    headerOneLine = headerOneLine,
                    footerMode = footerMode,
                    footerCustom = footerCustom,
                    showCreatedAt = showCreatedAt,
                    lineGap = lineGap.name,
                    lineGapCustom = lineGapCustom,
                    showPageNo = showPageNo,
                    footerSize = footerSize.name,
                    dockTabs = dockTabs.joinToString(",") { it.name },
                    targetPages = targetPages,
                    margin = margin,
                    bodySize = bodySize,
                    points = points.joinToString(",") { it.name },
                    pointCount = pointCount,
                    layoutVersion = LAYOUT_VERSION,
                    activeStudentId = activeStudentId,
                    term = term,
                ),
            )
        }
    }

    /** 启动时把设置读回来（v0.17.0 起卷面样式也在里面） */
    private fun applySettings(s: AppSettings) {
        dockGlass = s.dockGlass
        showNameRow = s.showNameRow
        headerOneLine = s.headerOneLine
        footerMode = s.footerMode.coerceIn(0, 2)
        footerCustom = s.footerCustom
        showCreatedAt = s.showCreatedAt
        lineGap = LineGap.of(s.lineGap)
        lineGapCustom = s.lineGapCustom.coerceIn(0f, 24f)
        showPageNo = s.showPageNo
        footerSize = FooterSize.of(s.footerSize)
        dockTabs = parseDockTabs(s.dockTabs)
        targetPages = s.targetPages.coerceIn(0, 8)
        margin = s.margin.coerceIn(8f, 60f)
        bodySize = s.bodySize.coerceIn(8f, 14f)
        points = s.points.split(",").mapNotNull { MathPoint.of(it.trim()) }
        pointCount = s.pointCount.coerceIn(5, 50)
        activeStudentId = s.activeStudentId
        term = s.term.coerceIn(0, 2)

        // v0.18.1 一次性迁移：老版本行距默认 NORMAL，用户反馈「行与行之间太宽」。
        // 只改默认值不够 —— 磁盘上存的还是 NORMAL，升级后读回来照样宽。
        // 这里把「没自己动过、还是老默认值」的换成 COMPACT；
        // 选过 TIGHT / LOOSE / CUSTOM 的一律不动，那是用户明确的意图。
        if (s.layoutVersion < LAYOUT_VERSION) {
            if (LineGap.of(s.lineGap) == LineGap.NORMAL) lineGap = LineGap.COMPACT
            saveSettings()
        }
    }

    // ---------------------------------------------------------------- 学生档案

    /** 把档案里的年级 / 学期 / 科目搬到参数上（不动题型配比） */
    private fun applyProfile(s: Student) {
        grade = s.grade.coerceIn(1, 9)
        term = s.term.coerceIn(0, 2)
        subjects = s.subjects.filter { it.availableFor(grade) }.ifEmpty { listOf(Subject.MATH) }
        subject = subjects.first()
        normalizeTilt()
    }

    /**
     * 点学生名字：档案就位 + 直接出卷。
     *
     * 用户的原话是「点击名字自动选择对应的教学资源来生成卷子」，所以这里不只是切参数，
     * 还会立刻排一张新卷。**年级/科目真的变了才重算题量** —— 同一个人连点两下，
     * 题量不该被他上次的手动调整冲掉。
     */
    suspend fun useStudent(id: String) {
        val s = students.firstOrNull { it.id == id } ?: return
        val changed = grade != s.grade || subjects.toSet() != s.subjects.toSet() || term != s.term
        activeStudentId = id
        applyProfile(s)
        normalizeKind()
        titleOverride = null
        clearUnitFilter()
        if (changed || counts.isEmpty()) {
            counts = PaperComposer.defaultSpec(grade, subjects, tilt, kind).counts
        }
        saveSettings()
        if (totalQuestions <= 0) {
            toast = "「${s.name}」的配置出不了题，去参数页挑一下题型"
            return
        }
        generate()
    }

    fun upsertStudent(s: Student) {
        val prev = students.firstOrNull { it.id == s.id }
        val exists = prev != null
        students = if (exists) students.map { if (it.id == s.id) s else it } else students + s
        LocalStore.saveStudents(ctx, students)
        // 新增第一个学生、或改的正好是当前学生：参数跟着变，省得再去点一次名字
        if (!exists || s.id == activeStudentId) {
            // 改了年级 / 科目要重算配比 —— 否则留着「初一物理」那套题型去出三年级的卷，
            // 那些题型在三年级根本不存在，会静默消失（纸上少一整道大题）
            val recollect = prev == null || prev.grade != s.grade ||
                prev.subjects.toSet() != s.subjects.toSet()
            activeStudentId = s.id
            applyProfile(s)
            normalizeKind()
            if (recollect) counts = PaperComposer.defaultSpec(grade, subjects, tilt, kind).counts
        }
        saveSettings()
    }

    fun deleteStudent(id: String) {
        val name = students.firstOrNull { it.id == id }?.name.orEmpty()
        students = students.filterNot { it.id == id }
        // 学生没了，挂在他名下的自编题也没意义了 —— 留着只会在别的孩子卷上冒出来
        customSheets = customSheets.filterNot { it.studentId == id }
        LocalStore.saveStudents(ctx, students)
        LocalStore.saveCustomSheets(ctx, customSheets)
        if (activeStudentId == id) {
            activeStudentId = ""
            saveSettings()
        }
        toast = "已删除「$name」的档案"
    }

    /** 新建档案的空壳 id：时间戳 36 进制，够唯一又不重复 */
    fun newStudentId(): String = "s" + System.currentTimeMillis().toString(36)

    // ---------------------------------------------------------------- 自编题

    fun upsertCustomSheet(sheet: CustomSheet) {
        val exists = customSheets.any { it.id == sheet.id }
        customSheets = if (exists) {
            customSheets.map { if (it.id == sheet.id) sheet else it }
        } else customSheets + sheet
        LocalStore.saveCustomSheets(ctx, customSheets)
    }

    fun deleteCustomSheet(id: String) {
        customSheets = customSheets.filterNot { it.id == id }
        LocalStore.saveCustomSheets(ctx, customSheets)
    }

    fun newCustomId(): String = "c" + System.currentTimeMillis().toString(36)

    /**
     * 解析存下来的页签串。
     *
     * 三个必须兜住的情况：枚举改名（旧名字直接丢掉）、手改过文件（出现脏值）、
     * 全部解析失败或被清空（回落到默认全集，绝不留一个空 dock 让用户无路可走）。
     * 「更多」一定要在：它是这套开关的入口。
     */
    private fun parseDockTabs(raw: String): List<NavTab> {
        val parsed = raw.split(',').mapNotNull { name ->
            NavTab.entries.firstOrNull { it.name == name.trim() }
        }.distinct().filter { it != NavTab.MORE }
        val withMore = (parsed + NavTab.MORE).distinct()
        return if (withMore.size < 2) DEFAULT_DOCK_TABS else withMore
    }

    // ---------------------------------------------------------------- 上次位置

    /** 上次退出时存下的浏览位置；界面据此决定列表从哪里开始滚 */
    var restoredSession by mutableStateOf(bootSession)
        private set

    /** 当前会话的实时位置（滚动时不断更新，退出时落盘） */
    private var liveScrolls: MutableMap<String, List<Int>> = LinkedHashMap(bootSession.scrolls)

    /** 参数页用的是 ScrollState（像素），不进 scrolls */
    var createScrollPx by mutableStateOf(bootSession.createScroll)
        private set

    /**
     * 记某个列表滚到了哪儿。
     *
     * 只存内存不落盘：滚动每帧都调，落盘会把主线程 IO 打满。
     * 真正写文件在 [persistSession]（Activity onStop 时调一次）。
     */
    fun noteScroll(key: String, index: Int, offset: Int) {
        val prev = liveScrolls[key]
        if (prev != null && prev[0] == index && prev[1] == offset) return
        liveScrolls[key] = listOf(index, offset)
    }

    fun noteCreateScroll(px: Int) {
        createScrollPx = px
    }

    /**
     * 取一次「复原参数页滚动位置」的任务，取完就作废。
     *
     * 不能直接把 createScrollPx 当初始值：参数页的滚动条是共用的，
     * 每次从别的页面进来都该从顶部开始，只有「刚从盘里复原」这一次要回到原位。
     */
    fun takeCreateScrollRestore(): Int? {
        if (!createScrollPending) return null
        createScrollPending = false
        return createScrollPx
    }

    /** 退出 / 切后台时把当前状态写盘。失败不打扰用户：丢了只是下次回到顶部 */
    fun persistSession() {
        // 快照在主线程取（读 Compose 状态必须在主线程），序列化与写盘丢到 IO。
        // 先取快照再切线程：反过来在 IO 线程读 mutableStateOf 会撞上快照系统的
        // 「只能在一个线程改状态」检查。
        val snapshot = SessionState(
            tab = navTab.name,
            screen = screen.name,
            previewPage = previewPage,
            // 只在预览页记卷 id：其他页面上 savedPaperId 可能是上一次的残留
            paperId = if (screen == Screen.PREVIEW) savedPaperId else "",
            createScroll = createScrollPx,
            scrolls = liveScrolls.toMap(),
        )
        ioScope.launch {
            runCatching { LocalStore.saveSession(ctx, snapshot) }
        }
    }

    /**
     * 启动时的异步复原：只有「上次停在试卷预览页」需要它。
     *
     * 列表类页面（首页 / 打印纸 / 错题本 / 更多 / 参数页）在 [bootScreen] 里
     * 已经定好了初始页面，不需要再切 —— 再切一次反而会和 pager 打架。
     */
    suspend fun restoreSessionOnce() {
        if (sessionRestored) return
        sessionRestored = true
        val saved = bootSession
        if (saved.screen != "PREVIEW" || saved.paperId.isEmpty()) {
            // 参数页：记下待复原的滚动位置，界面切进去时取一次
            if (saved.screen == "CREATE" && saved.createScroll > 0) createScrollPending = true
            return
        }
        // 预览页需要重新渲染才有东西可看：按记下的卷 id 打开，页码也回到原处。
        // 卷被删了就老实待着（bootScreen 已经把 screen 落到首页了）
        val paper = savedPapers.firstOrNull { it.id == saved.paperId } ?: return
        runCatching {
            reopen(paper)
            if (screen == Screen.PREVIEW) {
                previewPage = saved.previewPage.coerceIn(0, (previews.size - 1).coerceAtLeast(0))
            }
        }
    }

    /** 从 session 推出开机页面：预览页先落首页，等 [restoreSessionOnce] 把卷渲染出来再说 */
    private fun bootScreen(): Screen = when (bootSession.screen) {
        Screen.REFERENCE.name -> Screen.REFERENCE
        Screen.MISTAKES.name -> Screen.MISTAKES
        Screen.MORE.name -> Screen.MORE
        Screen.CREATE.name -> Screen.CREATE
        Screen.HISTORY.name -> Screen.HISTORY
        Screen.TEXTBOOK.name -> Screen.TEXTBOOK
        Screen.HELP.name -> Screen.HELP
        else -> Screen.HOME
    }

    private var sessionRestored = false
    private var createScrollPending = false

    val hasPrintService: Boolean = PrintServiceProbe.hasService(ctx)
    val versionName: String = BuildConfig.VERSION_NAME
    val bankSize: Int

    val availableTypes: List<QuestionType>
        get() = GeneratorRegistry.availableFor(grade, subjects)

    /** 参数页只列已选题型；没选的收进「添加题型」折叠区 */
    val selectedTypes: List<QuestionType>
        get() = availableTypes.filter { (counts[it] ?: 0) > 0 }


    val navTab: NavTab
        get() {
            // 预览页不属于任何一个页签：速查表预览算「打印纸」，试卷预览算「出卷」
            if (screen == Screen.PREVIEW) return if (isReference) NavTab.REFERENCE else NavTab.HOME
            // 子页面一律高亮它所属的页签，否则顶栏有返回键、dock 一个都不亮（像丢了）
            if (screen == Screen.CREATE) return NavTab.HOME
            if (screen == Screen.STUDENTS) return NavTab.HOME
            if (screen == Screen.HELP) return NavTab.MORE
            return NavTab.entries.firstOrNull { it.screen == screen } ?: NavTab.HOME
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
                // 来时的那一页也可能在这期间被用户藏进 tab 栏了，藏了就退回首页
                screen = if (isTabScreen(previewOrigin) || previewOrigin == Screen.CREATE) {
                    previewOrigin
                } else {
                    Screen.HOME
                }
                true
            }
            Screen.CREATE -> {
                screen = Screen.HOME
                true
            }
            // 打印帮助是从「更多」点进来的子页面，退回去还是「更多」
            Screen.HELP, Screen.LOCAL_PDF -> {
                screen = Screen.MORE
                true
            }
            // 页签页：返回键先回首页，首页再按才退出 App
            Screen.REFERENCE, Screen.MISTAKES, Screen.HISTORY, Screen.TEXTBOOK,
            Screen.MORE, Screen.STUDENTS -> {
                screen = Screen.HOME
                true
            }
            Screen.HOME -> false
        }
    }

    fun goTab(tab: NavTab) {
        toast = ""
        // 页签被隐藏时它不在 pager 里，直接跳过去会落到空白页
        if (tab !in dockTabs) return
        screen = tab.screen
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
        val t0 = android.os.SystemClock.elapsedRealtime()
        bankSize = BankQuestionSource.loadFromAssets(ctx)
        val t1 = android.os.SystemClock.elapsedRealtime()
        // 课标题库必须先 init：题型可用性判断要读 index.json 的题量统计，
        // 没初始化时 countOf 一律返回 0，那两个题型会静默消失
        CurriculumBank.init(ctx)
        val t2 = android.os.SystemClock.elapsedRealtime()
        ScienceBank.init(ctx)
        val t3 = android.os.SystemClock.elapsedRealtime()
        TextbookCatalog.init(ctx)
        val t4 = android.os.SystemClock.elapsedRealtime()
        // 卷面样式（姓名栏 / 一行标题 / 页脚 / 行距）跟着设置一起读回来，
        // 用户上次调好的版式下次开 App 还是那样
        applySettings(LocalStore.loadSettings(ctx))
        // 学生档案与自编题：小文件，开机一次读完就够（加起来几 KB）
        students = LocalStore.loadStudents(ctx)
        customSheets = LocalStore.loadCustomSheets(ctx)
        // 上次选中的学生还在：把年级 / 学期 / 科目带回来，但**不**自动出卷 ——
        // 冷启动直接弹一张卷子会让人以为「我只想看看错题本，怎么给我印卷子」。
        activeStudent?.let { applyProfile(it) }
        // 上次停在「试卷库」或「课本」，这次那个页签被关掉了 —— 落到首页，
        // 否则会渲染一个既不在 pager 里、又不是子页面的空白界面
        if (screen in ALL_TAB_SCREENS && !isTabScreen(screen)) screen = Screen.HOME
        val t5 = android.os.SystemClock.elapsedRealtime()
        counts = PaperComposer.defaultSpec(grade, subjects, tilt, kind).counts
        val t6 = android.os.SystemClock.elapsedRealtime()
        refreshLists()
        val t7 = android.os.SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG) {
            android.util.Log.i(
                "Boot",
                "题库 ${t1 - t0} · 课标 ${t2 - t1} · 科学 ${t3 - t2} · 教材 ${t4 - t3} · " +
                    "设置 ${t5 - t4} · defaultSpec ${t6 - t5} · 列表 ${t7 - t6} = 合计 ${t7 - t0}ms",
            )
        }
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

    /**
     * 从「课本」页点某个单元直接出卷：只考这一个单元的知识点。
     *
     * 题量按**单元里实际有多少题**封顶（选择题最多 20、填空最多 16），
     * 不硬凑：一个小单元只有 6 道题，就出 6 道 —— 凑到 20 道只能靠别的单元甚至别的年级来填，
     * 那就不叫「这一单元的练习卷」了。
     */
    suspend fun generateUnitPaper(unit: CurricUnit) {
        val ids = unit.topicIds.toSet()
        if (ids.isEmpty()) {
            toast = "这个单元还没有配套题"
            return
        }
        val blank = CurriculumBank.countIn(ids, choice = false)
        val choice = CurriculumBank.countIn(ids, choice = true)
        if (blank == 0 && choice == 0) {
            toast = "「${unit.name}」还没有配套题，可换个单元或按年级出卷"
            return
        }

        val newCounts = LinkedHashMap<QuestionType, Int>()
        if (blank > 0) newCounts[QuestionType.CURRIC_BLANK] = minOf(blank, 16)
        if (choice > 0) newCounts[QuestionType.CURRIC_CHOICE] = minOf(choice, 20)

        grade = unit.grade
        subject = unit.subject
        subjects = listOf(unit.subject)
        clearUnitFilter()
        unitTopicIds = ids
        unitTitle = unit.name
        counts = newCounts
        titleOverride = null
        previewSeed = System.currentTimeMillis()
        generate()
        // 从课本页出的卷子，返回时应该回到课本页而不是首页 —— 家长通常要连着出好几个单元
        previewOrigin = Screen.TEXTBOOK
    }

    /**
     * 单元题不够时一键「改成按整册出卷」。
     *
     * 保留课标题库的题型（家长要的仍是知识点练习，不是口算），只是把单元这个圈去掉，
     * 题量拉回 20 道让自动补满去裁剪页数。语文没有填空题，会自然只剩选择题。
     */
    suspend fun widenToGrade() {
        val wanted = counts.keys.filter {
            it == QuestionType.CURRIC_BLANK || it == QuestionType.CURRIC_CHOICE
        }.ifEmpty { listOf(QuestionType.CURRIC_CHOICE) }
        clearUnitFilter()
        val usable = wanted.filter { t ->
            val choice = t == QuestionType.CURRIC_CHOICE
            subjects.any { CurriculumBank.countOf(it, choice) > 0 }
        }.ifEmpty { listOf(QuestionType.CURRIC_CHOICE) }
        counts = usable.associateWith { 20 }
        previewSeed = System.currentTimeMillis()
        generate()
    }

    /**
     * 这个倾向在当前「年级 + 科目」下能不能真出题。
     *
     * 数学没有判断题、语文没有计算题 —— 这类组合点下去配比一个字都不会变。
     * UI 拿它把 chip 置灰，让人一眼看出「不是我不想选，是这科压根没有」。
     */
    fun canUseTilt(t: Tilt): Boolean = PaperComposer.fits(grade, subjects, t)

    /**
     * 换年级 / 换科目后，原来选的倾向可能就落空了（比如从科学切到数学、
     * 而当前选的是「判断题偏多」）。落空就静默回落到均衡搭配 ——
     * 留在那个 chip 上会一直显示「已选中」，但配比是别的，比不选更误导。
     */
    private fun normalizeTilt() {
        if (!PaperComposer.fits(grade, subjects, tilt)) tilt = Tilt.BALANCED
    }

    /**
     * 换年级 / 换科目后，原来选的卷型可能就落空了：拼音只到六年级、小升初只在五六年级，
     * 拼音卷认语文、奥数卷认数学。落空就回落到「全部」——
     * 留着一个名不副实的卷型比不选更误导。
     */
    private fun normalizeKind() {
        if (!kind.fits(grade)) kind = PaperKind.ALL
        val forced = kind.forceSubject
        if (forced != null && forced !in subjects) kind = PaperKind.ALL
    }

    fun selectGrade(g: Int) {
        clearUnitFilter()
        grade = g
        val kept = subjects.filter { it.availableFor(g) }.ifEmpty { listOf(Subject.MATH) }
        subjects = kept
        subject = kept.first()
        normalizeTilt()
        normalizeKind()
        counts = PaperComposer.defaultSpec(g, kept, tilt, kind).counts
        titleOverride = null
        previewSeed = System.currentTimeMillis()
    }

    /**
     * 换学期（v0.19.0）。
     *
     * 只影响**真带学期数据**的资源：课标题库的知识点、课本目录。
     * 口算/字词按年级分段，选上册不会变少 —— 所以换学期只换种子，不重算题量，
     * 免得用户以为「换了学期题量怎么变了」，其实那只是配比重算的副作用。
     */
    fun selectTerm(t: Int) {
        val next = t.coerceIn(0, 2)
        if (term == next) return
        term = next
        saveSettings()
        previewSeed = System.currentTimeMillis()
        toast = "已切到${GradeProfiles.gradeLabel(grade)}${Term.label(next)}"
    }

    /** 多学科交叉：点一下加进来，再点一下取消（至少留一科） */
    fun toggleSubject(s: Subject) {
        if (!s.availableFor(grade)) return
        clearUnitFilter()
        val list = subjects.toMutableList()
        if (s in list) {
            if (list.size == 1) return
            list.remove(s)
        } else {
            list.add(s)
        }
        subjects = list
        subject = list.first()
        normalizeTilt()
        normalizeKind()
        counts = PaperComposer.defaultSpec(grade, list, tilt, kind).counts
        titleOverride = null
        previewSeed = System.currentTimeMillis()
    }

    /**
     * 改「题型倾向 / 卷型」后重新配比。
     *
     * 只重算题型与题量，不动难度、栏数、纸向 —— 这两个参数决定「出什么题」，
     * 不决定「怎么印」，混在一起改会让人以为只是换了个标签。
     *
     * 卷型认科目时（拼音 = 语文、奥数 = 数学）顺手把科目切过去：留着「英语 + 拼音」
     * 会出一张一道拼音题都没有的拼音卷 —— 那正是「点了有反应、反应是假的」。
     */
    fun applyPreference(newTilt: Tilt = tilt, newKind: PaperKind = kind) {
        clearUnitFilter()
        // 该科做不出来的倾向直接不接 —— 接了就变成「点了没反应」的假控件
        tilt = if (newTilt == tilt || canUseTilt(newTilt)) newTilt else tilt
        kind = newKind
        val forced = newKind.forceSubject
        if (forced != null && forced.availableFor(grade) && subjects != listOf(forced)) {
            subjects = listOf(forced)
            subject = forced
        }
        counts = PaperComposer.defaultSpec(grade, subjects, tilt, kind).counts
        titleOverride = null
        previewSeed = System.currentTimeMillis()
    }

    fun applyTemplate(newCounts: Map<QuestionType, Int>) {
        clearUnitFilter()
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

    /**
     * 这张卷一共几道题。
     *
     * v0.19.0 起把自编题也算进来 —— 原来只数 counts，用户写 20 个词进卷子，
     * 首页还显示「0 题」，点「直接出卷」直接被 `totalQuestions > 0` 挡掉。
     */
    val totalQuestions: Int
        get() = if (points.isEmpty()) {
            counts.values.sum() + customQuestionCount
        } else {
            points.size * pointCount + customQuestionCount
        }

    val estimatedPages: Int
        get() {
            // 按题型各自的高度折算：竖式 3 栏、每道还要 3 条答题线，一道顶 4 道口算。
            // 以前统一按「每页 2 栏 × 26 题」估，竖式卷会多报一页（估 3 页实际 2 页）。
            val contentH = if (orientation == PageOrientation.LANDSCAPE) {
                TYPICAL_CONTENT_H_LANDSCAPE
            } else {
                TYPICAL_CONTENT_H
            }
            var used = 0f
            counts.forEach { (t, n) ->
                val cols = if (t.columns <= 1) 1 else maxOf(t.columns, columns)
                val h = TYPICAL_QUESTION_H + t.answerLines * TYPICAL_ANSWER_LINE_H
                used += n * h / cols
            }
            // 自编题也占纸：按各自栏数 / 答题线估一个高度，否则首页会少报一页
            visibleCustomSheets.forEach { sheet ->
                val n = CustomSheet.validCount(sheet)
                val cols = sheet.columns.coerceIn(1, 4)
                val h = TYPICAL_QUESTION_H + sheet.answerLines * TYPICAL_ANSWER_LINE_H
                used += n * h / cols
            }
            var pages = 1 + (used / contentH).toInt()
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

    /**
     * 题库抽干了（这一年级能出的题已经全部印上）。
     *
     * 和「版面排满了」要分开说：前者再点「补满这一页」也不会多一道题，
     * 界面上却还写着「建议再加约 16 题」，等于骗人 —— 科学题库一个年级只有三十几道，
     * 一张卷就能把它用光。
     */
    var poolDrained by mutableStateOf(false)
        private set

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
        // 2 栏时一道题只占约半行高度；单栏长句题按 1 栏算。
        // 栏数口径与 PaperComposer 保持一致（题型推荐栏数与用户选的取较大者），
        // 否则这里按 1 栏估、实际印成 3 栏，补题量会算得离谱。
        val effectiveCols = when {
            fillType == null -> columns.coerceAtLeast(1)
            fillType.columns <= 1 -> 1
            else -> maxOf(fillType.columns, columns)
        }
        // 每道题在「页高」上占多少：栏数决定并排几道，答题横线决定它比口算高几倍。
        // 以前只算栏数、不算答题线，竖式（3 条横线）被当成口算那么矮，
        // 估算出的补题量偏大，试排时越过页边界被回退，末页永远差一截。
        val qHeight = TYPICAL_QUESTION_H + (fillType?.answerLines ?: 0) * TYPICAL_ANSWER_LINE_H
        val gainPerQuestion = qHeight / effectiveCols
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

    private fun currentSpec(
        seed: Long,
        useCounts: Map<QuestionType, Int> = counts,
        /** A/B 卷后缀：题量结构与 A 卷完全一致，只是换了种子重抽题目 */
        copySuffix: String = "",
    ): PaperSpec {
        val total = if (points.isEmpty()) useCounts.values.sum() else points.size * pointCount
        // 「三年级上册」这种写法家长最熟：只写「三年级」分不出这学期学的是哪册
        val gradeText = GradeProfiles.gradeLabel(grade) +
            if (term == Term.BOTH) "" else Term.label(term)
        val autoTitle = when {
            // 按单元出卷：标题直接写单元名，家长一眼知道这张卷考的是哪一课
            unitTitle.isNotEmpty() -> "$gradeText · $unitTitle"
            // 卷型直接进标题（「数学期末测试」），点一下就看得见效果
            else -> PaperComposer.autoTitle(grade, subjects.ifEmpty { listOf(subject) }, kind, points)
        }
        val current = titleOverride?.takeIf { it.isNotBlank() } ?: autoTitle
        // titleOverride 可能是「…数学练习　A 卷」（从试卷库改参数、或上一轮生成 A 卷
        // 时存下来的成品标题）。再拼一次后缀就印成「A 卷　A 卷」——
        // 截图里已经出现过。只在真的要加后缀时剥旧后缀，用户自己写的
        // 「…A 卷」在单张卷模式下原样保留。
        val baseTitle = if (copySuffix.isEmpty()) current else stripCopyLabel(current)
        return PaperSpec(
            grade = grade,
            subject = subject,
            subjects = subjects,
            // 卷头直接印「A 卷 / B 卷」：两张纸混在一起时，老师一眼能分出来
            title = if (copySuffix.isEmpty()) baseTitle else "$baseTitle　$copySuffix",
            subtitle = "共 $total 题　满分 ${total * 2} 分",
            counts = useCounts,
            difficulty = difficulty,
            columns = columns,
            answerMode = answerMode,
            showNameRow = showNameRow,
            fontSize = 11f,
            seed = seed,
            tilt = tilt,
            kind = kind,
            orientation = orientation,
            topicIds = unitTopicIds,
            headerOneLine = headerOneLine,
            footerText = footerTextForSpec(),
            showCreatedAt = showCreatedAt,
            showPageNo = showPageNo,
            footerSize = footerSize,
            lineGap = lineGap,
            lineGapCustom = lineGapCustom,
            targetPages = targetPages,
            // v0.22.0：排版自由度（纸边留白 / 正文字号）
            margin = margin,
            bodySize = bodySize,
            // v0.19.0：学期 / 卷头姓名 / 自编题
            term = term,
            studentName = activeStudent?.name.orEmpty(),
            customSheets = visibleCustomSheets,
            // v0.23.0：知识点专项（非空时 counts 不参与出题）
            points = points,
            pointCount = pointCount,
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
        copySuffix: String = "",
    ): Built {
        val spec = currentSpec(seed, useCounts, copySuffix)
        val recent = LocalStore.recentHashes(ctx, grade, subject)
        val gp = PaperComposer.compose(spec, recent)
        val rendered = PaperRenderer.render(PaperAssembler.build(gp))
        val pages = if (withPreviews) PdfPreviewRenderer.render(ctx, rendered.bytes) else emptyList()
        val savedId = if (save) LocalStore.savePaper(ctx, gp, rendered.fileName).id else ""
        return Built(gp, rendered, pages, savedId)
    }

    /**
     * 「自动补满」的试排结果：[counts] 补后题量、[extra] 补了几题、[fill] 末页正文填充率。
     */
    private class FillPlan(
        val counts: LinkedHashMap<QuestionType, Int>,
        val extra: Int,
        val fill: Float,
        /** 加题已经加不出新题了（题源见底），界面要据此换文案 */
        val drained: Boolean = false,
    )

    /**
     * 反复「试排 → 加题 → 再试排」，把最后一张正文纸填满。
     *
     * 三条约束，少一条都会印得更糟：
     *  1. **不许增页**：补出来的卷比原来多一张纸就整轮回退，宁可少补；
     *  2. **不许空转**：词表抽干后计数再涨也出不了新题，看实际题数没涨就停；
     *  3. **多页也要补**：v0.7.4 之前只补「正文一页」的卷，可用户开这个开关，
     *     要的就是「最后一张纸别空着」——现在任何页数都补，只是照样不许增页。
     *
     * [autoFillPage] 关闭时原样返回（extra = 0），调用方不用自己分支。
     * 参数页的实时预览与正式生成共用这一份逻辑，才能做到「预览看到的就是印出来的」。
     */
    private suspend fun fillToPage(
        seed: Long,
        start: Map<QuestionType, Int>,
        rounds: Int,
    ): FillPlan {
        if (!autoFillPage) return FillPlan(LinkedHashMap(start), 0, -1f)

        var working = LinkedHashMap(start)
        var bestCounts = LinkedHashMap(start)
        var bestRatio = -1f
        var bestExtra = 0
        var extraAdded = 0
        var basePages = -1
        var lastCount = -1
        var drained = false

        for (attempt in 0..rounds) {
            val probe = withContext(Dispatchers.IO) { buildOnce(seed, working, save = false) }
            val pages = probe.pdf.contentPageCount
            if (basePages < 0) basePages = pages
            // 补到多出一张纸就停，回退到之前最好的那一版
            if (pages > basePages) break

            val ratio = contentFillRatio(probe.pdf)
            if (ratio > bestRatio) {
                bestRatio = ratio
                bestCounts = LinkedHashMap(working)
                bestExtra = extraAdded
            }
            // 题库抽干后计数再涨也出不了新题，别空转
            val noProgress = lastCount >= 0 && probe.paper.questions.size <= lastCount
            if (noProgress) drained = true
            lastCount = probe.paper.questions.size

            val fillType = pickFillType(working, probe)
            if (fillType == null || attempt == rounds || noProgress) break
            if (extraAdded >= MAX_AUTO_EXTRA) break
            val need = suggestedExtra(ratio, fillType)
            if (need <= 0) break

            // 每轮只走估算量的 80%，靠多轮试排逼近 90%：
            // 一次补满容易越过页边界，被「不增页」约束整轮回退。
            // 答题线的高度已经在 suggestedExtra 里算过了，这里不能再除一次。
            val step = (need * 0.8f).toInt()
                .coerceIn(1, need)
                .coerceAtMost(MAX_AUTO_EXTRA - extraAdded)
            if (step <= 0) break
            working[fillType] = (working[fillType] ?: 0) + step
            extraAdded += step
        }

        // 一轮都没补（说明第一版就已经满页 / 或者加不进去）不算「抽干」，
        // 只有「加了题但题数没涨」才是题源见底
        return FillPlan(bestCounts, bestExtra, bestRatio.coerceAtLeast(0f), drained)
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
            val plan = fillToPage(seed, counts, rounds = 5)
            counts = LinkedHashMap(plan.counts)
            // A/B 卷：B 卷沿用 A 卷补满后的题量与结构，只换种子。
            // 让 B 卷自己再补一轮的话，两张卷的大题题数很可能对不上，批改时没法对号。
            val wanted = copies.coerceIn(1, COPY_LABELS.size)
            val built = withContext(Dispatchers.IO) {
                (0 until wanted).map { i ->
                    val s = seed + i * COPY_SEED_STEP
                    buildOnce(s, plan.counts, save = true, copySuffix = COPY_LABELS.getOrElse(i) { "" })
                }
            }
            val result = built.first()
            batchBuilt = built
            activeCopy = 0
            applyBuilt(result, clearBatch = false)
            // 「抽干」的判定不能只看 fillToPage 的 noProgress —— 它可能提前因为
            // 页数到顶就 break 了。更稳的推断：自动补满已经加过题（extra>0）、
            // 末页却还是没排满，那就是题源见底了，不是版面限制。
            poolDrained = plan.drained || (plan.extra > 0 && lastContentFillPercent < 84)

            val extraAdded = plan.extra
            val actual = result.paper.questions.size
            toast = buildString {
                append("已生成 $actual 题，共 ${result.pdf.pageCount} 页")
                if (built.size > 1) {
                    append("　已出 A / B 两张：题量与结构一致、题目各不相同")
                }
                if (extraAdded > 0) append("（自动补了 $extraAdded 题，未增加页数）")
                // 去重 / 历史卷避让少给两三题是常态，别动不动报「题量不足」吓人
                val shortage = totalQuestions - actual
                when {
                    // 按单元出卷：这一单元的题本来就有限，印满不了是正常的，别报成错误
                    unitTopicIds.isNotEmpty() -> append("　这一单元的题已全部印上")
                    shortage > maxOf(4, totalQuestions / 5) ->
                        append("　⚠ 可用题量不足 $shortage 题，可减少题量或增选其它题型")
                }
                append("　最后一页正文用了 $lastContentFillPercent%")
                if (unitTopicIds.isEmpty() && lastContentFillPercent < 84) {
                    append("　可在预览页点「补满这一页」继续加题")
                }
            }
            previewOrigin = if (screen == Screen.CREATE) Screen.CREATE else Screen.HOME
            screen = Screen.PREVIEW
        } catch (t: Throwable) {
            toast = "生成失败：${t.message ?: t.javaClass.simpleName}"
        }
        busy = false
    }

    /**
     * 参数页实时预览：不落库、只渲染第 1 页缩略图。
     *
     * 开了「自动补满整页」时先在后台把题补到真满，再渲染 ——
     * 否则预览永远显示「没满」的那一版，用户开了开关也看不出效果，
     * 只有点「生成」进预览页才发现题量被改过。补满的题数同时回传，界面直接说明。
     */
    suspend fun refreshLivePreview() {
        if (totalQuestions <= 0) {
            livePreview = null
            liveFillPercent = 0
            liveAutoExtra = 0
            return
        }
        runCatching {
            // 预览只走 3 轮：参数页每次改题量都会重排，轮数太多会让缩略图跟不上手指
            val plan = fillToPage(previewSeed, counts, rounds = 3)
            val result = withContext(Dispatchers.IO) {
                val b = buildOnce(previewSeed, plan.counts, save = false)
                val bmp = PdfPreviewRenderer.render(ctx, b.pdf.bytes, scale = 0.75f).firstOrNull()
                Triple(bmp, (contentFillRatio(b.pdf) * 100).toInt(), b.pdf.contentPageCount)
            }
            livePreview = result.first
            liveFillPercent = result.second
            livePages = result.third
            liveAutoExtra = plan.extra
        }
    }

    /** 试卷库里点一张历史卷：同一种子会生成同一套题 */
    /**
     * 「再次编辑」：把一张历史卷的**参数**搬回参数页（v0.18.0）。
     *
     * 和 [reopen] 的区别要讲清楚：
     *  - reopen = 原样再看一遍这张纸（题目是当时生成的那批，参数不重要）；
     *  - editSaved = 拿这张卷当模板，改题量/加题型/换年级再出一张新的。
     *
     * 所以这里必须把 counts 从卷子里读回来 —— 不能像 reopen 那样用
     * `defaultSpec` 的默认配比，否则用户看到的题量和卷子上印的完全不是一回事。
     */
    fun editSaved(saved: SavedPaper) {
        val s = runCatching { Subject.valueOf(saved.subject) }.getOrDefault(Subject.MATH)
        grade = saved.grade
        subject = s
        subjects = listOf(s)
        val restored = LinkedHashMap<QuestionType, Int>()
        saved.counts.forEach { (k, v) ->
            runCatching { QuestionType.valueOf(k) }.getOrNull()?.let { restored[it] = v }
        }
        // 卷里一种题型都读不出来（老版本的备份）时退回默认配比，别给一张空卷
        counts = if (restored.isEmpty()) {
            PaperComposer.defaultSpec(grade, subjects, tilt, kind).counts
        } else {
            restored
        }
        titleOverride = stripCopyLabel(saved.title)
        // 换一个新种子再出卷：否则「编辑」出来的和原卷一模一样，看着像没生效
        previewSeed = System.currentTimeMillis()
        clearUnitFilter()
        showAdvanced = false
        screen = Screen.CREATE
        toast = "已载入「${saved.title}」的参数，改完再点生成"
    }

    /**
     * 去掉标题末尾的「　A 卷 / 　B 卷」。
     *
     * 存进试卷库的标题是**已经拼过后缀**的成品（卷头要印），
     * 拿它当「再次编辑」的模板标题、再走一遍 `currentSpec` 拼后缀，
     * 就会印出「…数学练习　A 卷　A 卷」。这里只剥后缀，标题正文照旧留用。
     */
    private fun stripCopyLabel(title: String): String {
        COPY_LABELS.forEach { label ->
            val suffix = "\u3000$label"
            if (title.endsWith(suffix)) return title.removeSuffix(suffix).trim()
        }
        return title
    }

    /**
     * 把「当前的卷面样式」套到一份 spec 上（v0.18.1）。
     *
     * 重开历史卷原来只搬了标题和种子，页脚 / 行距 / 姓名栏全走 `PaperSpec` 的默认值 ——
     * 用户明明调好了页脚、选了「极窄」，从试卷库点开一张旧卷，印出来又是默认样式，
     * 像是设置丢了。卷面样式是「全局偏好」而不是「这张卷的属性」，所以统一在这里盖。
     */
    private fun PaperSpec.withCurrentLayout(): PaperSpec = copy(
        showNameRow = showNameRow,
        headerOneLine = headerOneLine,
        footerText = footerTextForSpec(),
        showCreatedAt = showCreatedAt,
        showPageNo = showPageNo,
        footerSize = footerSize,
        lineGap = lineGap,
        lineGapCustom = lineGapCustom,
        targetPages = targetPages,
        margin = margin,
        bodySize = bodySize,
    )

    suspend fun reopen(saved: SavedPaper) {
        grade = saved.grade
        subject = runCatching { Subject.valueOf(saved.subject) }.getOrDefault(Subject.MATH)
        subjects = listOf(subject)
        counts = PaperComposer.defaultSpec(grade, subjects, tilt, kind).counts
        titleOverride = saved.title
        busy = true
        try {
            val built = withContext(Dispatchers.IO) {
                val spec = LocalStore.toSpec(saved)
                    .copy(title = saved.title, seed = saved.createdAt)
                    .withCurrentLayout()
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
                        // 与 PaperComposer 同一套栏数口径，错题卷才不会和普通卷排得不一样
                        columns = if (type.columns <= 1) 1
                        else maxOf(type.columns, columns),
                        answerLines = type.answerLines,
                        instruction = type.instruction,
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
            // 补题要的是「真的多出几道新题」。池子抽干就返回空，
            // 否则会把这批题当成没出现过、原样再贴一遍，同一道题在同一张卷里印两次
            allowReuse = false,
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
            // 补上去到底有没有多出题。没有 = 题源抽干了（科学题库一个年级就三十几道），
            // 不是「版面排满了」—— 两种情况的说法不能混，否则用户一直点、一直没反应
            var sawMore = false
            val baseSize = gp.questions.size

            for (attempt in 0..5) {
                if (bestRatio >= FILL_TARGET) break
                val step = suggestedExtra(bestRatio)
                if (step <= 0) break
                val want = total + step
                if (want > 40) break
                val probe = withContext(Dispatchers.IO) { probeAppend(gp, want) } ?: break
                if (probe.paper.questions.size > baseSize) sawMore = true
                // 补到多出一张纸、或填充率没有长进，就停，保留上一轮最好的版本
                if (probe.pages > basePages || probe.ratio <= bestRatio + 0.005f) break
                best = probe
                bestRatio = probe.ratio
                total = want
            }

            if (best != null) {
                poolDrained = false
                applyAppend(best, "自动补满，已加")
            } else if (!sawMore) {
                poolDrained = true
                toast = "这一年级的题已经全部用上了（共 $baseSize 道），想多练可以换年级或换科目"
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

    /**
     * 删题后重建大题（v0.18.3）：题号连续、标题重印、删空的大题整段去掉。
     *
     * 之前只有「加题」会重算标题，删题不会 —— 删掉 3 道口算，标题还印着
     * 「共 60 分」，底下只剩 27 道；某道大题被删空后，序号还会从「一」跳到「三」。
     * 这些在纸上一眼就能看出来，属于「程序算的和印的对不上」那类硬伤。
     */
    private fun rebuildSections(
        sections: List<GeneratedSection>,
        spec: PaperSpec,
    ): List<GeneratedSection> {
        var no = 0
        return sections.filter { it.questions.isNotEmpty() }.mapIndexed { i, s ->
            val qs = s.questions.map { q -> q.copy(no = ++no) }
            s.copy(
                index = i + 1,
                questions = qs,
                // 用 s.name / s.points 重印，而不是按 type 重算：
                // 自编题的名字是用户起的，按类型重算会变回「自编题」、分值也回到 2
                title = PaperComposer.titleFor(
                    name = s.name.ifBlank {
                        PaperComposer.subjectPrefixFor(spec, s.type) + s.type.label
                    },
                    points = s.points,
                    count = qs.size,
                    ordinal = i + 1,
                ),
            )
        }
    }

    /** 删掉一组题并重排（单题删除与批量删除共用，两条路的纸上结果必须一致） */
    private suspend fun commitRemoval(gp: GeneratedPaper, nos: Set<Int>) {
        val stripped = gp.sections.map { s ->
            s.copy(questions = s.questions.filter { it.no !in nos })
        }
        val sections = rebuildSections(stripped, gp.spec)
        reRender(gp.copy(sections = sections, questions = sections.flatMap { it.questions }))
        // 题号整体重排后，之前勾的「第 5 题做错了」已经指向另一道题了 ——
        // 留着比清掉危险得多：用户点「加入错题本」，进错题本的会是别的题。
        // 删题属于「整理卷子」阶段，勾错题属于「批改」阶段，清掉不算打断他。
        wrongNumbers = emptySet()
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
            commitRemoval(gp, setOf(no))
            toast = "已删除第 $no 题，题号已顺延"
        } catch (t: Throwable) {
            toast = "删除失败：${t.message}"
        }
        busy = false
    }

    /**
     * 批量删题（v0.18.3）。
     *
     * 用户的原话是「题目清单的支持批量删除」—— 一张 60 题的口算卷要删掉中间
     * 十来道重复的，一题一题点「删这题」要点十几下，而且每删一次整卷重排、
     * 题号重编，点着点着眼花。这里一次删完、一次重排。
     *
     * 两条护栏：「至少留一道题」（跟单题删除一致）；删空的大题整段去掉 ——
     * 只剩个标题底下一道题都没有，印出来像漏印。
     */
    suspend fun removeQuestions(nos: Set<Int>) {
        val gp = paper ?: return
        if (nos.isEmpty()) {
            toast = "先勾选要删的题"
            return
        }
        if (nos.size >= gp.questions.size) {
            toast = "至少留一道题，不能全删"
            return
        }
        busy = true
        try {
            commitRemoval(gp, nos)
            selectedNos = emptySet()
            toast = "已删 ${nos.size} 题，还剩 ${gp.questions.size - nos.size} 题"
        } catch (t: Throwable) {
            toast = "删除失败：${t.message}"
        }
        busy = false
    }

    /** 加一题：跟最后一道题同题型，追加到对应大题末尾并整卷重新编号 */
    /**
     * 在末尾加一题。
     *
     * [type] 为 null 时沿用「最后一题的题型」（老行为，点一下就加）；
     * 指定题型时，卷里已有这道大题就并进去，没有就**新起一道大题** ——
     * 用户的原话是「生成的卷子可以自定义删减添加」，光能删不能挑着加不够用。
     */
    suspend fun appendQuestion(type: QuestionType? = null) {
        val gp = paper ?: return
        val target = type ?: gp.questions.lastOrNull()?.type ?: return
        busy = true
        try {
            val fresh = withContext(Dispatchers.IO) { freshQuestion(gp, target) }
            if (fresh == null) {
                toast = "「${target.label}」暂时抽不出新题了，换一种题型试试"
            } else {
                val mutable = gp.sections.toMutableList()
                val prefix = PaperComposer.subjectPrefixFor(gp.spec, target)
                val at = mutable.indexOfLast { it.type == target }
                if (at >= 0) {
                    // 并进已有的大题：标题里的「共 N 分」得跟着题数重算，
                    // 否则纸上会出现「共 6 分」底下排着 4 道题
                    val merged = mutable[at].questions + fresh
                    mutable[at] = PaperComposer.sectionOf(
                        spec = gp.spec,
                        type = target,
                        questions = merged,
                        ordinal = at + 1,
                        instruction = mutable[at].instruction,
                        prefix = prefix,
                    )
                } else {
                    mutable.add(
                        PaperComposer.sectionOf(
                            spec = gp.spec,
                            type = target,
                            questions = listOf(fresh),
                            ordinal = mutable.size + 1,
                            prefix = prefix,
                        )
                    )
                }
                val sections = renumber(mutable)
                reRender(gp.copy(sections = sections, questions = sections.flatMap { it.questions }))
                toast = "已加 1 道${target.label}，共 ${sections.flatMap { it.questions }.size} 题"
            }
        } catch (t: Throwable) {
            toast = "加题失败：${t.message}"
        }
        busy = false
    }

    /**
     * 这张卷现在还能加哪些题型。
     *
     * 只列「当前年级 + 已选科目能真正出题」的，跟参数页的题型库同一套判断 ——
     * 列出来点了却抽不出题，比不给这个入口更糟。
     */
    fun addableTypes(): List<QuestionType> {
        // 没有在看的卷子就没有「加题」这回事，直接空列表（面板只在预览页开）
        val spec = paper?.spec ?: return emptyList()
        return GeneratorRegistry.availableFor(spec.grade, spec.effectiveSubjectsForState())
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
        // 科学不算主科：混进来的话，一二年级的「每日一练」会变成 数+语+科学，
        // 把英语挤掉。想要科学就用「选个卷子」单独出。
        val picked = Subject.values()
            .filter { it != Subject.SCIENCE && it.availableFor(grade) }
            .take(3)
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

    /**
     * 把一份生成结果推上界面。
     *
     * [clearBatch] 默认清掉 A/B 批 —— 打开历史卷、错题卷、每日一练都会经过这里，
     * 不清的话预览页会残留上一批的「A 卷 / B 卷」切换，点下去跳到一个毫不相干的卷子。
     * 只有 generate() 自己（以及 A/B 之间切换）需要保留整批。
     */
    private fun applyBuilt(b: Built, clearBatch: Boolean = true) {
        if (clearBatch) {
            batchBuilt = emptyList()
            activeCopy = 0
        }
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

    /**
     * 把当前卷的每一页导出成 PNG，并一次性交给分享面板。
     *
     * 为什么要有这条路：奔图云打印小程序、微信里的一些打印号只收图片不收 PDF；
     * 老师把卷子发群里让家长自己打印时，图片也比 PDF 更容易在手机上直接打。
     *
     * 渲染倍率用 [EXPORT_IMAGE_SCALE]（高于预览的 1.5），因为预览只求看得清，
     * 导出是要落到纸上或放大看的 —— 用预览那张图导出，1 页 A4 只有 892px 宽，
     * 打印出来字会发虚。
     */
    suspend fun exportImages() {
        val current = pdf ?: return
        busy = true
        try {
            val files = withContext(Dispatchers.IO) {
                val dir = File(PdfExporter.papersDir(ctx), "images").apply { mkdirs() }
                val base = current.fileName.substringBeforeLast('.').ifBlank { "试卷" }
                PdfPreviewRenderer.render(ctx, current.bytes, scale = EXPORT_IMAGE_SCALE)
                    .mapIndexed { i, image ->
                        val file = File(dir, "${base}_第${i + 1}页.png")
                        FileOutputStream(file).use { out ->
                            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        file
                    }
            }
            if (files.isEmpty()) {
                toast = "没有可导出的页"
            } else {
                toast = "已导出 ${files.size} 张图片到 ${files.first().parent}/"
                runCatching { PdfSharer.shareImages(ctx, files, paper?.spec?.title ?: "练习卷") }
                    .onFailure { toast = "图片已存好，但打开分享面板失败：${it.message}" }
            }
        } catch (t: Throwable) {
            toast = "导出图片失败：${t.message ?: t.javaClass.simpleName}"
        }
        busy = false
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

    // ---------------------------------------------------------------- 本地 PDF

    /**
     * 演示：用户自己选的 PDF（老师发的卷子、自己下的教材）。
     *
     * 为什么不放进 assets：那属于**用户自己的副本**，App 只是个打印工具。
     * 我们只把它复制进 cacheDir 临时用，不落数据目录、不上传、不打进包，
     * 也就不存在任何再分发问题。
     */
    var localPdf by mutableStateOf<LocalPdf?>(null)
        private set

    suspend fun openLocalPdf(uri: Uri) {
        busy = true
        try {
            val loaded = withContext(Dispatchers.IO) { loadLocalPdf(uri) }
            localPdf = loaded
            screen = Screen.LOCAL_PDF
            toast = "已读取 ${loaded.pages} 页，点「系统打印」选奔图 M1"
        } catch (t: Throwable) {
            toast = "读取失败：${t.message ?: t.javaClass.simpleName}"
        }
        busy = false
    }

    private fun loadLocalPdf(uri: Uri): LocalPdf {
        val name = queryDisplayName(uri)
        val target = File(ctx.cacheDir, "local_print.pdf")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        } ?: error("读不到这个文件，换个位置试试")

        val pfd = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pages = renderer.pageCount
        var preview: ImageBitmap? = null
        var landscape = false
        if (pages > 0) {
            val page = renderer.openPage(0)
            landscape = page.width > page.height
            // 缩略图按最长边 900px 收一下：A4 原尺寸位图要 15MB，一进页面就掉帧
            val scale = 900f / maxOf(page.width, page.height).coerceAtLeast(1)
            val w = (page.width * minOf(1f, scale)).toInt().coerceAtLeast(1)
            val h = (page.height * minOf(1f, scale)).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            preview = bmp.asImageBitmap()
        }
        renderer.close()
        pfd.close()
        return LocalPdf(target, name, pages, preview, landscape)
    }

    private fun queryDisplayName(uri: Uri): String {
        val fallback = "本地文档.pdf"
        return runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: fallback
    }

    suspend fun printLocalPdf(activity: Activity) {
        val doc = localPdf ?: return
        if (doc.pages <= 0) {
            toast = "这个 PDF 没有可打印的页"
            return
        }
        busy = true
        try {
            PrintLauncher.printFile(activity, doc.name, doc.file, doc.pages, doc.landscape)
            toast = "已调起系统打印：在打印机列表里选奔图 M1"
        } catch (t: Throwable) {
            toast = "打印失败：${t.message ?: t.javaClass.simpleName}"
        }
        busy = false
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
