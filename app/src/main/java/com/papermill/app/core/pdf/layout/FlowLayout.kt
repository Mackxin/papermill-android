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
 * 所以改成：先 layout 出 [[Page]] 列表（纯数据，不画），再由渲染器逐页画。
 * 这顺带白送一个能力 —— 预览页拿同一份 Page 列表直接内存绘制，不用生成 PDF。
 *
 * ============================================================
 * 首页和后续页的可排区域高度不同
 * ============================================================
 * 第一页有「卷头 + 姓名班级得分栏」，占掉约 80pt；后续页只有一个窄页眉。
 * 如果统一用首页高度，后续页会浪费大半屏；统一用后续页高度，首页会溢出。
 * 所以这里是 contentHFirst / contentHOther 两个值。
 */
class FlowLayout(
    private val contentW: Float,
    private val contentHFirst: Float,
    private val contentHOther: Float,
    private val columns: Int = 1,
    private val columnGap: Float = 20f,
    /** 本次 layout 的首页是不是「整卷的第 1 页」（第 2 个大题从新页开始时传 false） */
    private val startsOnFirstPage: Boolean = true,
) {

    data class Placed(val block: Block, val x: Float, val y: Float, val w: Float)

    data class Page(val items: List<Placed>)

    fun layout(blocks: List<Block>, tp: TextPainter): List<Page> {
        val pages = ArrayList<Page>()
        var items = ArrayList<Placed>()
        var col = 0
        var y = 0f
        var pageNo = 0

        val cols = columns.coerceAtLeast(1)
        val colW = (contentW - columnGap * (cols - 1)) / cols

        fun cap(): Float =
            if (startsOnFirstPage && pageNo == 0) contentHFirst else contentHOther

        fun flushPage() {
            pages.add(Page(items))
            items = ArrayList()
            col = 0
            y = 0f
            pageNo++
        }

        fun advanceColumn() {
            if (col + 1 >= cols) flushPage() else {
                col++
                y = 0f
            }
        }

        blocks.forEach { block ->
            // 跨页宽块（大题标题）：必须从栏首开始，且一次占满整宽
            if (block.fullWidth && cols > 1) {
                if (col > 0 || y > 0f) flushPage()
                val h = block.measure(tp, contentW)
                items.add(Placed(block, 0f, y, contentW))
                y += h
                return@forEach
            }

            val h = block.measure(tp, colW)

            // 放不下 → 换栏（换到最后一栏还没放下就换页）
            if (y + h > cap() && y > 0f) advanceColumn()

            // 单块比整栏还高（超长题干）：只能强制放，并如实记录溢出，绝不无限换页
            items.add(Placed(block, col * (colW + columnGap), y, colW))
            y += h
        }

        if (items.isNotEmpty()) pages.add(Page(items))
        return if (pages.isEmpty()) listOf(Page(emptyList())) else pages
    }
}
