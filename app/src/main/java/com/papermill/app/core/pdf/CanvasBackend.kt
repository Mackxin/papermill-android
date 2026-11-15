package com.papermill.app.core.pdf

import android.graphics.Paint
import android.graphics.Path

/**
 * 绘图后端的抽象。
 *
 * 只有两个实现，但换来一个重要能力：
 *  - [PdfBackend]     画到 PdfDocument 的 Canvas → 打印/导出用
 *  - ComposeBackend   画到 Compose 的 nativeCanvas → 预览用（后续里程碑）
 *
 * 因为两边走的是同一套 Block.paint 代码、同一个 Paint 实例，
 * 「预览看到的」和「打印出来的」在结构上就不可能不一致。
 *
 * v0.16.0 补了圆 / 椭圆 / 圆弧 / 路径四个原语：看图题要画钟面、条形统计图、
 * 三角形和五角星，只有直线加矩形根本画不出来。这几个都是 `android.graphics`
 * 里的现成调用，不引入任何依赖；矢量图形也不会像插一张 PNG 那样把 PDF 撑大。
 */
interface CanvasBackend {
    fun drawText(text: String, x: Float, baseline: Float, paint: Paint)
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint)

    /** 描边矩形（用 paint 的 strokeWidth / style） */
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint)

    /** 圆：钟面、五角星的外接圆 */
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint)

    /** 椭圆：压在矩形里的圆（宽高不等时不会被拉成方块） */
    fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint)

    /**
     * 圆弧：[startAngle] 从 3 点钟方向起、顺时针为正，[useCenter] = true 连到圆心画成扇形。
     * 钟面的刻度和统计图的半圆都要靠它 —— 一条条画直线拼弧既难看又费指令。
     */
    fun drawArc(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        paint: Paint,
    )

    /** 任意路径：三角形、五角星这类多边形，用 Path 一次描边 */
    fun drawPath(path: Path, paint: Paint)
}
