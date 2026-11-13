package com.papermill.app.core.pdf.layout

import com.papermill.app.core.pdf.TextPainter

/**
 * 分栏分页器 —— 整个排版引擎的地基。
 *
 * ============================================================
 * v0.4.0 重构：从「每个大题各排一遍」改成「整卷一条连续流」
 * ============================================================
 * 旧版 PaperRenderer 会对每个大题单独调用 layout()，再把各段页数直接相加。
 * 结果每个大题都从新的一页开始：一份「口算 + 填括号 + 凑十 + 换算」的
 * 一年级卷，明明一页能装下，却印成 6~7 页，中间全是空白 —— 与用户
 * 「最后要打印出完整的一页纸」完全相反。
 *
 * 现在整卷只调一次 layout()，普通块按栏填充，整宽块（大题标题、
 * 需要书写过程的应用题、表格）占满整行并开启一个新「段」。
 * 段与段在同一页里上下相接，只有真的装不下才翻页。
 *
 * ============================================================
 * 为什么多栏要「贪心最矮栏」而不是填满左栏再换右栏
 * ============================================================
 * 流式排版（先填满第一栏再换栏）在试卷上会出大事：20 道口算只占 300pt，
 * 而一栏能装 650pt —— 结果全挤在左半页，右半页整片空白。
 * 每次把新块交给「当前已用高度最小、且放得下」的那一栏，等高题目会
 * 自然分成 10 + 10；遇到不等高块也不会像旧版目标水位算法那样抖动。
 *
 * ============================================================
 * 首页和后续页可用高度不同（首页有卷头、姓名栏）
 * ============================================================
 * contentHFirst / contentHOther 分开传；y 一律是「相对本页内容区顶部」。
 * [startsOnFirstPage] = false 用于答案页这种接在别人后面的段。
 */
class FlowLayout(
    private val contentW: Float,
    private val contentHFirst: Float,
    private val contentHOther: Float,
    private val columns: Int = 1,
    private val columnGap: Float = 20f,
    /** 本次 layout 的第一页是不是「整卷的第 1 页」 */
    private val startsOnFirstPage: Boolean = true,
) {

    data class Placed(val block: Block, val x: Float, val y: Float, val w: Float)

    data class Page(val items: List<Placed>)

    fun layout(blocks: List<Block>, tp: TextPainter): List<Page> {
        if (blocks.isEmpty()) return listOf(Page(emptyList()))

        val cols = columns.coerceAtLeast(1)
        val colW = (contentW - columnGap * (cols - 1)) / cols
        val pages = ArrayList<Page>()

        var items = ArrayList<Placed>()
        var pageNo = 0
        var cap = capFor(pageNo)
        // y：本页已用到的最下沿；segTop：当前段（栏目区）的起始 y
        var y = 0f
        var segTop = 0f
        val colUsed = FloatArray(cols)

        fun flushPage() {
            pages.add(Page(items))
            items = ArrayList()
            pageNo++
            cap = capFor(pageNo)
            y = 0f
            segTop = 0f
            colUsed.fill(0f)
        }

        for (block in blocks) {
            if (block.fullWidth) {
                val h = block.measure(tp, contentW)
                // 标题和它后面至少一道题应尽量同页：只有当前页已有内容才翻
                if (items.isNotEmpty() && y + h > cap + 0.5f) flushPage()
                items.add(Placed(block, 0f, y, contentW))
                y += h
                segTop = y
                colUsed.fill(0f)
                continue
            }

            val h = block.measure(tp, colW)
            var col = pickColumn(colUsed, segTop, cap, h)
            if (col < 0 && items.isNotEmpty()) {
                // 这一页所有栏都放不下 → 翻页后重试
                flushPage()
                col = pickColumn(colUsed, segTop, cap, h)
            }
            // 单个块比整页还高（超长题干）：硬放第 0 栏，保证不死循环
            if (col < 0) col = 0

            items.add(
                Placed(
                    block = block,
                    x = col * (colW + columnGap),
                    y = segTop + colUsed[col],
                    w = colW,
                )
            )
            colUsed[col] += h
            y = maxOf(y, segTop + colUsed[col])
        }

        if (items.isNotEmpty()) pages.add(Page(items))
        return pages.ifEmpty { listOf(Page(emptyList())) }
    }

    private fun capFor(pageIndex: Int): Float =
        if (startsOnFirstPage && pageIndex == 0) contentHFirst else contentHOther

    /** 在「放得下」的栏里挑最矮的一栏；都放不下返回 -1 */
    private fun pickColumn(colUsed: FloatArray, segTop: Float, cap: Float, h: Float): Int {
        var best = -1
        var bestUsed = Float.MAX_VALUE
        for (c in colUsed.indices) {
            val fits = segTop + colUsed[c] + h <= cap + 0.5f
            if (fits && colUsed[c] < bestUsed) {
                best = c
                bestUsed = colUsed[c]
            }
        }
        return best
    }
}
