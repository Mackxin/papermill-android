package com.papermill.app.core.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** 画到 PdfDocument 的 Canvas 上。 */
class PdfBackend(private val canvas: Canvas) : CanvasBackend {

    override fun drawText(text: String, x: Float, baseline: Float, paint: Paint) {
        canvas.drawText(text, x, baseline, paint)
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        canvas.drawRect(left, top, right, bottom, paint)
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        canvas.drawCircle(cx, cy, radius, paint)
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        canvas.drawOval(left, top, right, bottom, paint)
    }

    override fun drawArc(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        paint: Paint,
    ) {
        canvas.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint)
    }

    override fun drawPath(path: Path, paint: Paint) {
        canvas.drawPath(path, paint)
    }
}
