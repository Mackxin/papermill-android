package com.papermill.app.core.pdf.layout

import com.papermill.app.core.pdf.TextPainter

/**
 * 分栏分页器 —— 整个排版引擎的地基。
 *
 * ============================================================
 * 为什么不复用报表那套 Pager
 * ============================================================
 * `跑单充电工作台` 里的 Pager 模型是「一条 y 游标往下走，ensure(h) 不够就翻页」。
 * 对流水报表完美，但对试卷缺三样东西：
 *  1. 表达不了**双栏**（口算 / 生字必须两栏或四栏排，单栏浪费 60% 纸）；
 *  2. 保证不了 **keep-together**（一道题加它的答题横线必须同页）；
 *  3. 做不了**两阶段**（"第 x 页 / 共 y 页"要求先完整 layout、知道总页数，再逐页画）。
 *
 * ============================================================
 * 关键设计：多栏要「均衡填充」，不是「填满左栏再填右栏」
 * ============================================================
 * 教科书式的流式排版（先填满第一栏再换栏）在试卷上会出大事：
 * 20 道口算只占 300pt，而一栏能装 650pt —— 结果 20 题全挤在左半页，
 * 右半页整片空白。这不是排版，这是浪费纸。
 *
 * 所以这里做两件事：
 *  1. 先试探「本页能装多少道题」（[assign] 返回 null 就说明装不下）；
 *  2. 装得下之后，按「本栏已用高度 vs 剩余平均高度」的规则均衡分配到各栏，
 *     让 20 题自然分成 10 + 10。
 *
 * 首页和后续页的可用高度不同（首页有卷头、姓名栏），所以是 contentHFirst / contentHOther 两个值。
 */
class FlowLayout(
    private val contentW: Float,
    private val contentHFirst: Float,
    private val contentHOther: Float,
    private val columns: Int = 1,
    private val columnGap: Float = 20f,
    /** 本次 layout 的首页是不是「整卷的第 1 页」（第 2 个大题另起页时传 false） */
    private val startsOnFirstPage: Boolean = true,
) {

    data class Placed(val block: Block, val x: Float, val y: Float, val w: Float)

    data class Page(val items: List<Placed>)

    fun layout(blocks: List<Block>, tp: TextPainter): List<Page> {
        if (blocks.isEmpty()) return listOf(Page(emptyList()))

        val cols = columns.coerceAtLeast(1)
        val colW = (contentW - columnGap * (cols - 1)) / cols
        val pages = ArrayList<Page>()

        var index = 0
        var pageNo = 0

        while (index < blocks.size) {
            val cap = if (startsOnFirstPage && pageNo == 0) contentHFirst else contentHOther

            // ---- 1) 页首的整宽块（大题标题、通栏小注）----
            val wide = ArrayList<Block>()
            while (index < blocks.size && blocks[index].fullWidth) {
                wide.add(blocks[index])
                index++
            }
            var wideH = 0f
            wide.forEach { wideH += it.measure(tp, contentW) }
            val bodyCap = (cap - wideH).coerceAtLeast(60f)

            // ---- 2) 试探本页能装多少道题 ----
            val bodyStart = index
            var count = 0
            var assignment: IntArray? = null

            while (index + count < blocks.size && !blocks[index + count].fullWidth) {
                val heights = ArrayList<Float>(count + 1)
                for (k in 0..count) heights.add(blocks[index + k].measure(tp, colW))

                val candidate = if (cols == 1) {
                    if (heights.sum() <= bodyCap + 0.5f) IntArray(heights.size) else null
                } else {
                    assignToColumns(heights, cols, bodyCap)
                }
                if (candidate == null) break
                assignment = candidate
                count++
            }

            // 一块都放不下（超长题干）→ 至少放一块，绝不能原地打转
            if (count == 0 && index < blocks.size && !blocks[index].fullWidth) {
                count = 1
                assignment = IntArray(1)
            }
            // 整页什么都没有（既无整宽块也无正文）→ 防御性退出
            if (count == 0 && wide.isEmpty()) break

            index = bodyStart + count

            // ---- 3) 落位置 ----
            val items = ArrayList<Placed>(wide.size + count)
            var y = 0f
            wide.forEach { block ->
                val h = block.measure(tp, contentW)
                items.add(Placed(block, 0f, y, contentW))
                y += h
            }

            if (count > 0 && assignment != null) {
                val colUsed = FloatArray(cols)
                for (k in 0 until count) {
                    val block = blocks[bodyStart + k]
                    val h = block.measure(tp, colW)
                    val c = assignment[k].coerceIn(0, cols - 1)
                    items.add(
                        Placed(
                            block = block,
                            x = c * (colW + columnGap),
                            y = wideH + colUsed[c],
                            w = colW,
                        )
                    )
                    colUsed[c] += h
                }
            }

            pages.add(Page(items))
            pageNo++
        }

        return if (pages.isEmpty()) listOf(Page(emptyList())) else pages
    }

    /**
     * 把一串块高度均衡分到 [cols] 栏里；装不下返回 null。
     *
     * 规则是「水位填充」：本栏已用高度 + 这一块 一旦超过**目标水位**就换下一栏，
     * 目标水位 = （总高度 - 前面已填满的栏的总高度）/ 剩余栏数。
     *
     * ⚠️ 这里的减法只能减「前面已填满的栏」，不能减当前栏 ——
     * 否则水位会随着当前栏被填而不断收缩（20 道题会被切成 7 + 13，就是这个坑）。
     */
    private fun assignToColumns(heights: List<Float>, cols: Int, cap: Float): IntArray? {
        val res = IntArray(heights.size)
        val used = FloatArray(cols)
        val total = heights.sum()
        var col = 0

        for (i in heights.indices) {
            val h = heights[i]
            var placed = false

            while (col < cols) {
                var filledBefore = 0f
                for (c in 0 until col) filledBefore += used[c]
                val colsLeft = cols - col
                val target = (total - filledBefore) / colsLeft

                val fitsCap = used[col] + h <= cap + 0.5f
                val underTarget = used[col] + h <= target + 0.5f

                // 该栏还是空的、且这一块自己就超过整栏高度 → 只能让它占着（后续会如实溢出）
                val singleOversize = used[col] <= 0f && !fitsCap

                if (singleOversize || (fitsCap && (underTarget || used[col] <= 0f || colsLeft == 1))) {
                    res[i] = col
                    used[col] += h
                    placed = true
                    break
                }
                col++
            }

            if (!placed) return null
        }
        return res
    }
}
