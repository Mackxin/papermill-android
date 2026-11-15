package com.papermill.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import com.papermill.app.core.pdf.render.PaperAssembler
import com.papermill.app.core.pdf.render.PaperRenderer
import com.papermill.app.core.pdf.render.PdfPreviewRenderer
import com.papermill.app.core.pdf.render.PracticeRenderer
import com.papermill.app.core.pdf.render.ReferenceRenderer
import com.papermill.app.domain.content.CurriculumBank
import com.papermill.app.domain.content.ScienceBank
import com.papermill.app.domain.generate.GenContext
import com.papermill.app.domain.generate.LineGap
import com.papermill.app.domain.generate.PaperComposer
import com.papermill.app.domain.generate.GeneratorRegistry
import com.papermill.app.domain.generate.PaperPurpose
import com.papermill.app.domain.generate.QuestionType
import com.papermill.app.domain.generate.Subject
import com.papermill.app.domain.generate.Tilt
import com.papermill.app.domain.reference.PracticeData
import com.papermill.app.domain.reference.ReferenceData
import java.io.File
import java.io.FileOutputStream

/**
 * 内置纸张的「批量体检」工具，**只在 debug 包生效**。
 *
 * ============================================================
 * 为什么需要它
 * ============================================================
 * App 里有 16 张速查表 + 8 张练习纸 + 24 张主题识字纸，靠手点 UI 一张张
 * 看根本看不过来；而排版崩坏（文字溢出格子、页尾大片空白、表格断开）
 * 只有真的把 PDF 渲出来才能发现。
 *
 * 这里直接在设备上跑一遍渲染管线，把每一页存成 PNG，并顺手用像素扫描
 * 算出「最后一处墨迹到内容区底部还剩多少 pt」—— 那就是页尾空白。
 * 空白超过 200pt 基本可以认定分页策略有问题。
 *
 * 触发方式（不影响正式功能）：
 * ```
 * adb shell am start -n com.papermill.app/.MainActivity --ez dumpSheets true
 * ```
 * 产物在 `/sdcard/Android/data/com.papermill.app/files/sheets/`。
 */
object DebugSheetDump {

    private const val TAG = "SheetDump"

    /** 渲染倍率：1pt = 1.5px，A4 竖版约 892×1263，看清小字够用 */
    private const val SCALE = 1.5f

    /** 页脚高度（pt），与 PageGeometry.footerH 保持一致 */
    private const val FOOTER_H = 26f
    private const val MARGIN = 40f

    /** 页脚那条分隔线画在内容区底部上方约 38pt，扫描时要一并避开，否则每页空白都算成 38 */
    private const val FOOTER_RULE_GAP = 46f

    fun dump(ctx: Context) {
        Thread {
            val dir = File(ctx.getExternalFilesDir(null), "sheets")
            dir.deleteRecursively()
            dir.mkdirs()

            val jobs = ArrayList<Triple<String, String, () -> ByteArray>>()
            ReferenceData.sheets.forEach { s ->
                jobs.add(Triple("ref", s.title) { PaperRenderer.render(ReferenceRenderer.build(s)).bytes })
            }
            (PracticeData.sheets + PracticeData.themeSheets).forEach { s ->
                jobs.add(Triple("practice", s.title) { PaperRenderer.render(PracticeRenderer.build(s)).bytes })
            }

            val report = StringBuilder()
            report.append("名称\t页数\t各页页尾空白(pt)\n")

            jobs.forEachIndexed { index, job ->
                val (kind, title, render) = job
                val name = "%02d-%s-%s".format(index, kind, title.replace(Regex("[^\\w\\u4e00-\\u9fa5]"), "_"))
                try {
                    val bytes = render()
                    val pages = PdfPreviewRenderer.render(ctx, bytes, SCALE)
                    val blanks = ArrayList<String>()
                    pages.forEachIndexed { pageIndex, image ->
                        val bmp = image.asAndroidBitmap()
                        save(dir, "$name-p${pageIndex + 1}.png", bmp)
                        blanks.add("%.0f".format(blankAtBottom(bmp)))
                    }
                    val line = "$title\t${pages.size}\t${blanks.joinToString(" / ")}"
                    report.append(line).append('\n')
                    Log.i(TAG, line)
                } catch (t: Throwable) {
                    val line = "$title\t渲染失败：${t.message}"
                    report.append(line).append('\n')
                    Log.e(TAG, line, t)
                }
            }

            File(dir, "report.txt").writeText(report.toString())
            Log.i(TAG, "done -> ${dir.absolutePath}")
        }.start()
    }

    /**
     * 真卷体检（v0.9.0 新增）：按指定题型组合出一张真卷，渲成 PNG 落盘 + 算页尾空白。
     *
     * 为什么不能只看题型抽样：题干长短直接决定排版好不好看。
     * 课标题库的题干最长的有 136 字、选项最长的有 89 字，这些只有真的排进 A4 才知道
     * 会不会把一栏撑成竖条、会不会 4 个选项折成 8 行。
     *
     * 触发：
     * ```
     * adb shell am start -n com.papermill.app/.MainActivity --ez dumpPaper true
     * ```
     */
    fun dumpPaper(ctx: Context) {
        CurriculumBank.init(ctx)
        Thread {
            val dir = File(ctx.getExternalFilesDir(null), "papers")
            dir.deleteRecursively()
            dir.mkdirs()

            val cases = listOf(
                "curric-math4" to PaperComposer.specWith(
                    4, Subject.MATH,
                    mapOf(QuestionType.CURRIC_BLANK to 12, QuestionType.CURRIC_CHOICE to 12),
                ),
                "curric-chinese6" to PaperComposer.specWith(
                    6, Subject.CHINESE,
                    mapOf(QuestionType.CURRIC_CHOICE to 16),
                ),
                "curric-physics8" to PaperComposer.specWith(
                    8, Subject.PHYSICS,
                    mapOf(QuestionType.CURRIC_BLANK to 8, QuestionType.CURRIC_CHOICE to 10),
                ),
                // v0.11.2 排版反馈的固定体检样本：
                // 竖式计算要三栏、单词拼装不该每题重复指令、答案页要撑满整页
                "vertical-g4" to PaperComposer.specWith(
                    4, Subject.MATH, mapOf(QuestionType.VERTICAL to 9),
                ),
                "scramble-g4" to PaperComposer.specWith(
                    4, Subject.ENGLISH, mapOf(QuestionType.WORD_SCRAMBLE to 15),
                ),
                "mixed-g4" to PaperComposer.specWith(
                    4, Subject.MATH,
                    mapOf(
                        QuestionType.ORAL to 20,
                        QuestionType.VERTICAL to 9,
                        QuestionType.WORD_PROBLEM to 3,
                    ),
                ),
                // v0.14.0 用户截图样本：52 道口算只排 2 栏、答案页 2 栏半页白
                "oral52-g3" to PaperComposer.specWith(
                    3, Subject.MATH, mapOf(QuestionType.ORAL to 52),
                ),
                // 混合卷：口算要 4 栏、选择题要 2 栏，看栏数能不能在大题边界切换
                "oral-choose-g4" to PaperComposer.specWith(
                    4, Subject.MATH,
                    mapOf(QuestionType.ORAL to 24, QuestionType.CURRIC_CHOICE to 8),
                ),
                // v0.16.0 看图题：图是矢量现画的，只有真的排进 A4 才知道
                // 「图会不会把题干挤到下一页」「图和下一题会不会叠」
                "figure-g1" to PaperComposer.specWith(
                    1, Subject.MATH,
                    mapOf(QuestionType.FIGURE_COUNT to 4, QuestionType.FIGURE_EXPR to 4),
                ),
                "clock-g2" to PaperComposer.specWith(
                    2, Subject.MATH, mapOf(QuestionType.CLOCK_READ to 8),
                ),
                "chart-g3" to PaperComposer.specWith(
                    3, Subject.MATH, mapOf(QuestionType.BAR_CHART to 5),
                ),
                // 看图题和普通题混排：一行图 + 一行算式，看分段有没有把页尾弄空
                "mix-figure-g1" to PaperComposer.specWith(
                    1, Subject.MATH,
                    mapOf(
                        QuestionType.ORAL to 12,
                        QuestionType.FIGURE_COUNT to 2,
                        QuestionType.CLOCK_READ to 3,
                    ),
                ),
                // v0.17.0 卷面定制：同一份内容，只改「卷头 / 页脚 / 行距」，
                // 看页数和页尾空白各差多少 —— 用户要的是「充分利用纸张」，
                // 而这些开关承诺的正是「省下一行 / 省下 26pt」，得能对上账
                "gap-compact" to PaperComposer.specWith(
                    3, Subject.MATH, mapOf(QuestionType.ORAL to 52),
                ).copy(lineGap = LineGap.COMPACT),
                "gap-loose" to PaperComposer.specWith(
                    3, Subject.MATH, mapOf(QuestionType.ORAL to 52),
                ).copy(lineGap = LineGap.LOOSE),
                "head-oneline" to PaperComposer.specWith(
                    3, Subject.MATH, mapOf(QuestionType.ORAL to 52),
                ).copy(headerOneLine = true),
                "no-footer" to PaperComposer.specWith(
                    3, Subject.MATH, mapOf(QuestionType.ORAL to 52),
                ).copy(footerText = "", showCreatedAt = false),
                "all-tight" to PaperComposer.specWith(
                    3, Subject.MATH, mapOf(QuestionType.ORAL to 52),
                ).copy(
                    lineGap = LineGap.COMPACT,
                    headerOneLine = true,
                    showNameRow = false,
                    footerText = "",
                    showCreatedAt = false,
                ),
            )

            val report = StringBuilder("名称\t页数\t各页页尾空白(pt)\n")
            cases.forEach { (name, spec) ->
                try {
                    val paper = PaperComposer.compose(spec)
                    val bytes = PaperRenderer.render(PaperAssembler.build(paper)).bytes
                    val pages = PdfPreviewRenderer.render(ctx, bytes, SCALE)
                    val blanks = ArrayList<String>()
                    pages.forEachIndexed { i, image ->
                        val bmp = image.asAndroidBitmap()
                        save(dir, "$name-p${i + 1}.png", bmp)
                        blanks.add("%.0f".format(blankAtBottom(bmp)))
                    }
                    val line = "$name\t${pages.size}\t${blanks.joinToString(" / ")}"
                    report.append(line).append('\n')
                    Log.i(TAG, line)
                } catch (t: Throwable) {
                    val line = "$name\t渲染失败：${t.message}"
                    report.append(line).append('\n')
                    Log.e(TAG, line, t)
                }
            }
            File(dir, "report.txt").writeText(report.toString())
            Log.i(TAG, "done -> ${dir.absolutePath}")
        }.start()
    }

    /**
     * 配比体检：把「倾向 × 用途 × 年级 × 科目」的组合全跑一遍，只打题型与题量。
     *
     * 为什么不能靠手点：7 种倾向 × 3 种用途 = 21 种组合，每种还要看年级/科目能不能出，
     * 手点一遍十几分钟，而且很容易漏看「切了倾向但其实一道都没换」这种静默失败 ——
     * 那正是 v0.11.2 的病根（columns 声明了却从没被读）。这里一次性把结论摊开看。
     *
     * ```
     * adb shell am start -n com.papermill.app/.MainActivity --ez dumpRatios true
     * ```
     */
    fun dumpRatios(ctx: Context) {
        CurriculumBank.init(ctx)
        ScienceBank.init(ctx)
        Thread {
            val grades = listOf(1, 3, 4, 6, 8)
            grades.forEach { grade ->
                val subjects = Subject.values().filter { it.availableFor(grade) }
                subjects.forEach { subject ->
                    Log.i(TAG, "===== ${grade} 年级 / ${subject.label} =====")
                    Tilt.values().forEach { tilt ->
                        val line = PaperPurpose.values().joinToString("　｜　") { p ->
                            val spec = PaperComposer.defaultSpec(grade, listOf(subject), tilt, p)
                            val detail = spec.counts.entries.joinToString("、") { "${it.key.label} ${it.value}" }
                            "${p.label}(${spec.counts.values.sum()}): $detail"
                        }
                        Log.i(TAG, "  ${tilt.label} → $line")
                    }
                }
            }
            // 多学科组合也看一组：勾了语文+数学时，两边都得有题
            val multi = listOf(Subject.CHINESE, Subject.MATH)
            Log.i(TAG, "===== 3 年级 / 语文+数学 =====")
            Tilt.values().forEach { tilt ->
                PaperPurpose.values().forEach { p ->
                    val spec = PaperComposer.defaultSpec(3, multi, tilt, p)
                    val detail = spec.counts.entries.joinToString("、") { "${it.key.label} ${it.value}" }
                    Log.i(TAG, "  ${tilt.label} / ${p.label} → ${spec.counts.values.sum()} 题：$detail")
                }
            }
            Log.i(TAG, "ratios done")
        }.start()
    }

    /**
     * 题型抽样：把指定题型各生成若干题，直接打到 logcat。
     *
     * 用途是**核对题干与答案是否配套** —— 选择题选项洗牌后答案字母容易对不上，
     * 光看代码看不出来，只有把题目真造出来逐条念一遍才可靠。
     *
     * v0.9.0 起对课标题库额外做**机器核对**：拿着答案字母回题干里取出那个选项的文字，
     * 再回源题库找同一道题、看原文的正确答案是不是这段文字 —— 不一致就报 FAIL。
     * 「看起来对」和「真的对」是两件事，这种逐条比对比人眼扫一遍靠谱得多。
     */
    fun dumpQuestions(ctx: Context) {
        CurriculumBank.init(ctx)
        ScienceBank.init(ctx)
        Thread {
            // (题型, 科目, 年级, 题量, 难度)：拆成五元组，之前靠「是不是物理」决定年级，加科目就得改三元组
            data class Job(
                val type: QuestionType,
                val subject: Subject,
                val grade: Int,
                val count: Int,
                val difficulty: Int = 2,
            )
            val jobs = listOf(
                Job(QuestionType.CURRIC_BLANK, Subject.MATH, 4, 5),
                Job(QuestionType.CURRIC_CHOICE, Subject.MATH, 4, 5),
                Job(QuestionType.CURRIC_CHOICE, Subject.CHINESE, 4, 4),
                Job(QuestionType.CURRIC_CHOICE, Subject.PHYSICS, 8, 3),
                Job(QuestionType.CURRIC_BLANK, Subject.ENGLISH, 4, 3),
                // v0.11.0 小学科学：三个年级各看一遍，确认难度与年级对得上
                Job(QuestionType.SCIENCE_CHOICE, Subject.SCIENCE, 3, 12),
                Job(QuestionType.SCIENCE_JUDGE, Subject.SCIENCE, 3, 10),
                Job(QuestionType.SCIENCE_BLANK, Subject.SCIENCE, 3, 12),
                Job(QuestionType.SCIENCE_CHOICE, Subject.SCIENCE, 1, 6, difficulty = 1),
                Job(QuestionType.SCIENCE_CHOICE, Subject.SCIENCE, 6, 6, difficulty = 3),
            )
            jobs.forEach { job ->
                val gen = GeneratorRegistry.of(job.type, job.grade)
                if (gen == null) {
                    Log.e(TAG, "===== ${job.type.label} / ${job.subject.label}：该年级取不到生成器 =====")
                    return@forEach
                }
                val ctxGen = GenContext(
                    grade = job.grade,
                    count = job.count,
                    difficulty = job.difficulty,
                    rng = java.util.Random(42),
                    blocked = HashSet(),
                    subjects = setOf(job.subject),
                )
                val list = runCatching { gen.generate(ctxGen) }.getOrElse { emptyList() }
                Log.i(TAG, "===== ${job.type.label} / ${job.subject.label} ${job.grade} 年级 · " +
                    "要 ${job.count} 题 / 出 ${list.size} 题 · 难度 ${job.difficulty} =====")
                list.forEachIndexed { i, q ->
                    Log.i(TAG, "Q${i + 1} ${q.stem.replace("\n", " ｜ ")}")
                    Log.i(TAG, "   答：${q.answer}")
                    when (job.type) {
                        QuestionType.CURRIC_CHOICE -> Log.i(TAG, "   核对：${verifyChoice(q.stem, q.answer)}")
                        QuestionType.SCIENCE_CHOICE ->
                            Log.i(TAG, "   核对：${verifyScienceChoice(q.stem, q.answer)}")
                        else -> Unit
                    }
                }
            }
            Log.i(TAG, "===== 课标题库总量：${CurriculumBank.totalQuestions()} 题 / " +
                "${CurriculumBank.totalTopics()} 个知识点 =====")
            Log.i(TAG, "===== 科学题库总量：${ScienceBank.total()} 题 =====")

            // 题型可用性矩阵：语文没有填空题，勾语文时「知识点填空」不该出现在参数页
            listOf(
                "一年级·数学" to (1 to listOf(Subject.MATH)),
                "四年级·数学" to (4 to listOf(Subject.MATH)),
                "四年级·语文" to (4 to listOf(Subject.CHINESE)),
                "四年级·英语" to (4 to listOf(Subject.ENGLISH)),
                "初二·物理" to (8 to listOf(Subject.PHYSICS)),
                "初三·化学" to (9 to listOf(Subject.CHEMISTRY)),
                "六年级·数学+语文" to (6 to listOf(Subject.MATH, Subject.CHINESE)),
                "三年级·科学" to (3 to listOf(Subject.SCIENCE)),
                "六年级·数学+科学" to (6 to listOf(Subject.MATH, Subject.SCIENCE)),
            ).forEach { (label, pair) ->
                val (g, subs) = pair
                val types = GeneratorRegistry.availableFor(g, subs)
                Log.i(
                    TAG,
                    "[题型] $label：含填空=${QuestionType.CURRIC_BLANK in types} " +
                        "含选择=${QuestionType.CURRIC_CHOICE in types} " +
                        "含科学=${QuestionType.SCIENCE_CHOICE in types} 共 ${types.size} 种",
                )
            }
        }.start()
    }

    /**
     * 选择题自检：答案字母指向的选项文字，必须等于源题库里这道题的正确答案文字。
     *
     * 从题干首行拿到原题干 → 回题库找同 prompt 的题 → 用答案字母取出洗牌后的选项文字
     * → 与源题的正确答案文字比对。
     */
    private fun verifyChoice(stem: String, answer: String): String {
        val prompt = stem.substringBefore('\n').trim()
        val letter = Regex("（([A-F])）").find(answer)?.groupValues?.get(1) ?: return "✗ 答案里没有选项字母"
        val chosen = Regex("$letter\\.\\s*([^　]+)").find(stem.substringAfter('\n'))
            ?.groupValues?.get(1)?.trim() ?: return "✗ 题干里找不到 $letter 选项"

        val source = CurriculumBank.findByPrompt(prompt) ?: return "？ 源题库里没找到这道题"

        val correct = source.options.firstOrNull { it.first.equals(source.answer.trim(), true) }
            ?: return "✗ 源题答案字母 ${source.answer} 不在选项里"
        return if (correct.second.trim() == chosen) "✓ ${letter}=$chosen"
        else "✗ ${letter}=$chosen，源题正确项应为 ${correct.second.trim()}"
    }

    private fun save(dir: File, name: String, bmp: Bitmap) {
        FileOutputStream(File(dir, name)).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * 页尾空白：内容区最下沿往上，第一处「有墨」的位置离底部多远（pt）。
     *
     * 扫描时避开页脚那一行小字，否则每页都会被页脚撑成 0。
     */
    private fun blankAtBottom(bmp: Bitmap): Float {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // 内容区最下沿：页高 - 页边距 - 页脚 - 页脚分隔线的余量
        val bottomPx = ((h / SCALE) - MARGIN - FOOTER_H - FOOTER_RULE_GAP) * SCALE
        val from = bottomPx.toInt().coerceIn(0, h - 1)

        for (y in from downTo 0) {
            val rowStart = y * w
            var x = 0
            while (x < w) {
                val c = pixels[rowStart + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r < 240 || g < 240 || b < 240) {
                    return (from - y) / SCALE
                }
                x += 2
            }
        }
        return from / SCALE
    }

    /**
     * 科学选择题自检：洗牌后答案字母指向的选项文字，必须等于源题库里这道题的正确选项。
     *
     * 渲染格式是两行：`【知识点】题干` + `A. xx　B. yy　C. zz`，答案形如 `（B）凝结…`。
     * 只要「字母取到的文字」和「源题 answer 字母对应的文字」一致，说明洗牌没把答案带歪。
     */
    private fun verifyScienceChoice(stem: String, answer: String): String {
        val lines = stem.split("\n")
        if (lines.size < 2) return "FAIL 题干格式不对"
        val srcStem = lines[0].substringAfter("】", lines[0]).trim()
        val options = lines[1].split("　").map { it.trim() }.filter { it.isNotEmpty() }
        val letter = answer.substringAfter("（").substringBefore("）").trim().firstOrNull()
            ?: return "FAIL 答案里没有字母"
        val picked = options.firstOrNull { it.startsWith("$letter.") }
            ?.substringAfter(".")?.trim() ?: return "FAIL 字母 $letter 在选项里找不到"
        val source = ScienceBank.findByStem(srcStem) ?: return "FAIL 源题库里没找到这道题"
        val idx = source.answer.trim().uppercase().firstOrNull()?.minus('A') ?: return "FAIL 源题答案异常"
        val expect = source.options.getOrNull(idx)?.trim() ?: return "FAIL 源题选项越界"
        return if (picked == expect) "OK（$letter → $picked）" else "FAIL 期望「$expect」实际「$picked」"
    }
}
