package com.papermill.app.core.pdf

/**
 * A4 纸张几何常量。
 *
 * ============================================================
 * 为什么一切都是 pt（磅），而不是 dp / px
 * ============================================================
 * `android.graphics.pdf.PdfDocument` 的坐标系单位就是 pt（1/72 英寸）：
 * A4 = 210×297mm = 595×842pt。所以在画布上写「字号 11」就是纸面上的 11pt，
 * 不需要任何 dp/dpi 换算，也不会因为设备密度不同而排版错乱。
 *
 * 换算备查：1pt = 25.4/72 mm ≈ 0.353mm；1mm ≈ 2.835pt。
 *
 * ============================================================
 * 为什么边距是 40pt（≈14.1mm），不是常见的 10mm
 * ============================================================
 * Android 打印框架的 `PrintAttributes.Margins.NO_MARGINS` 只是「最小边距提示」，
 * 真实边距由 print service（奔图的驱动 / Mopria）决定，常见会在四周各裁掉 4~8mm。
 * 40pt 的留白是双保险：即使驱动再裁 8mm，页眉和题号也不会被切掉。
 */
object PageGeometry {
    /** A4 宽度（pt） */
    const val PAGE_W = 595

    /** A4 高度（pt） */
    const val PAGE_H = 842

    /** 四周页边距（pt）≈ 14.1mm */
    const val MARGIN = 40f

    /** 内容区宽度：595 - 40*2 = 515pt */
    const val CONTENT_W = PAGE_W - MARGIN * 2

    /** 内容区底部（页脚要留出空间，不能画到这儿以下） */
    const val CONTENT_BOTTOM = PAGE_H - MARGIN

    /** 页脚占位高度（分隔线 + 一行小字） */
    const val FOOTER_H = 26f

    /** 可排版区域的最下沿：再往下就碰到页脚了 */
    const val CONTENT_BOTTOM_SAFE = CONTENT_BOTTOM - FOOTER_H

    /** 毫米转 pt */
    fun mm(v: Float): Float = v * 72f / 25.4f
}
