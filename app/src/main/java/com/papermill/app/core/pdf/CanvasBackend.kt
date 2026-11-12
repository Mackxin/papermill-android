package com.papermill.app.core.pdf

import android.graphics.Paint

/**
 * 绘图后端的抽象。
 *
 * 只有两个实现，但换来一个重要能力：
 *  - [PdfBackend]     画到 PdfDocument 的 Canvas → 打印/导出用
 *  - ComposeBackend   画到 Compose 的 nativeCanvas → 预览用（后续里程碑）
 *
 * 因为两边走的是同一套 Block.paint 代码、同一个 Paint 实例，
 * 「预览看到的」和「打印出来的」在结构上就不可能不一致。
 */
interface CanvasBackend {
    fun drawText(text: String, x: Float, baseline: Float, paint: Paint)
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint)

    /** 描边矩形（用 paint 的 strokeWidth / style） */
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint)
}
