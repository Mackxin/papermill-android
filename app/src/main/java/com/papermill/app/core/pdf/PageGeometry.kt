package com.papermill.app.core.pdf

import com.papermill.app.domain.generate.PageOrientation

/**
 * A4 纸张几何 —— v0.4.0 起支持竖版 / 横版两套尺寸。
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
 * 横版不是「把竖版旋转一下」这么简单
 * ============================================================
 * 系统打印框最终按 `PrintAttributes.MediaSize` 的宽高找驱动能力，
 * PDF 内部也必须真的建成 842×595pt 的页面，否则打印服务会把横版内容
 * 再缩放/旋转一次，题号被裁掉。所以这里同时改三处：
 *   1) PdfDocument.PageInfo 的宽高；
 *   2) 内容区宽度 contentW（515pt → 762pt，一行能排更多）；
 *   3) 内容区高度（首页约 657pt → 约 410pt，题量要相应减少）。
 *
 * ============================================================
 * 为什么边距是 40pt（≈14.1mm），不是常见的 10mm
 * ============================================================
 * Android 打印框架的 `PrintAttributes.Margins.NO_MARGINS` 只是「最小边距提示」，
 * 真实边距由 print service（奔图的驱动 / Mopria）决定，常见会在四周各裁掉 4~8mm。
 * 40pt 的留白是双保险：即使驱动再裁 8mm，页眉和题号也不会被切掉。
 */
data class PageGeometry(
    val pageW: Int,
    val pageH: Int,
    /** 四周页边距（pt）≈ 14.1mm */
    val margin: Float = 40f,
    /** 页脚占位高度（分隔线 + 一行小字） */
    val footerH: Float = 26f,
) {
    /** 内容区宽度：竖版 515pt / 横版 762pt */
    val contentW: Float get() = pageW - margin * 2f

    /** 内容区底部（页脚要留出空间，不能画到这儿以下） */
    val contentBottom: Float get() = pageH - margin

    /** 可排版区域的最下沿：再往下就碰到页脚了 */
    val contentBottomSafe: Float get() = contentBottom - footerH

    val orientation: PageOrientation
        get() = if (pageW > pageH) PageOrientation.LANDSCAPE else PageOrientation.PORTRAIT

    companion object {
        /** A4 竖版：595×842pt */
        val A4_PORTRAIT = PageGeometry(595, 842)

        /** A4 横版：842×595pt（宽高互换，不是旋转画布） */
        val A4_LANDSCAPE = PageGeometry(842, 595)

        fun of(orientation: PageOrientation): PageGeometry = when (orientation) {
            PageOrientation.LANDSCAPE -> A4_LANDSCAPE
            PageOrientation.PORTRAIT -> A4_PORTRAIT
        }

        /** 毫米转 pt */
        fun mm(v: Float): Float = v * 72f / 25.4f
    }
}
