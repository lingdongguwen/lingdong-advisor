package com.yuanlingbb.auto.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import com.yuanlingbb.auto.util.FrameHolder

/**
 * 覆盖在截屏画面上的交互层：
 * - 普通模式：按下拖拽框选区域（松手即定稿，写入 FrameHolder.selectedRect）
 * - 取色模式：按下/移动即出现像素放大镜，松手取该点精确颜色
 * 坐标通过源 ImageView 的 imageMatrix 反算回位图坐标，保证与画面像素对齐。
 */
class RegionSelectorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatImageView(context, attrs) {

    private var source: ImageView? = null
    fun bindSource(iv: ImageView) { source = iv }

    private var startX = 0f; private var startY = 0f
    private var curX = 0f; private var curY = 0f
    private var drawing = false
    var pickMode = false
    private var pickX = 0f; private var pickY = 0f
    private var pickActive = false

    private val stroke = Paint().apply {
        color = Color.parseColor("#FF8A3D"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val fill = Paint().apply {
        color = Color.parseColor("#33FF8A3D"); style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pickMode) {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { pickActive = true; pickX = e.x; pickY = e.y; invalidate() }
                MotionEvent.ACTION_MOVE -> { pickX = e.x; pickY = e.y; invalidate() }
                MotionEvent.ACTION_UP -> { pickX = e.x; pickY = e.y; pickAt(e.x, e.y); invalidate() }
            }
            return true
        }
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.x; startY = e.y; curX = e.x; curY = e.y; drawing = true; invalidate()
            }
            MotionEvent.ACTION_MOVE -> { curX = e.x; curY = e.y; invalidate() }
            MotionEvent.ACTION_UP -> { drawing = false; finalizeRect() }
        }
        return true
    }

    private fun viewToBitmap(vx: Float, vy: Float): PointF {
        val iv = source ?: return PointF(vx, vy)
        val inv = Matrix(); iv.imageMatrix.invert(inv)
        val pts = floatArrayOf(vx, vy); inv.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    private fun pickAt(vx: Float, vy: Float) {
        val b = FrameHolder.latestBitmap ?: return
        val p = viewToBitmap(vx, vy)
        val bx = p.x.toInt().coerceIn(0, b.width - 1)
        val by = p.y.toInt().coerceIn(0, b.height - 1)
        val color = b.getPixel(bx, by)
        FrameHolder.pickedColor = color
        FrameHolder.pickedPoint = Point(bx, by)
        (context as? CapturePreviewActivity)?.onColorPicked(color, bx, by)
    }

    private fun finalizeRect() {
        val left = Math.min(startX, curX); val top = Math.min(startY, curY)
        val right = Math.max(startX, curX); val bottom = Math.max(startY, curY)
        if (right - left < 10 || bottom - top < 10) return
        val p1 = viewToBitmap(left, top); val p2 = viewToBitmap(right, bottom)
        val b = FrameHolder.latestBitmap ?: return
        val rl = p1.x.toInt().coerceIn(0, b.width)
        val rt = p1.y.toInt().coerceIn(0, b.height)
        val rr = p2.x.toInt().coerceIn(0, b.width)
        val rb = p2.y.toInt().coerceIn(0, b.height)
        val rect = Rect(rl, rt, rr, rb)
        FrameHolder.selectedRect = rect
        (context as? CapturePreviewActivity)?.onRegionSelected(rect)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawing) {
            val left = Math.min(startX, curX); val top = Math.min(startY, curY)
            val right = Math.max(startX, curX); val bottom = Math.max(startY, curY)
            canvas.drawRect(left, top, right, bottom, fill)
            canvas.drawRect(left, top, right, bottom, stroke)
        }
        if (pickMode && pickActive) drawMagnifier(canvas)
    }

    private fun drawMagnifier(canvas: Canvas) {
        val b = FrameHolder.latestBitmap ?: return
        val radius = 60f
        val p = viewToBitmap(pickX, pickY)
        val sx = p.x.toInt(); val sy = p.y.toInt()
        val half = 7
        val src = Rect(
            (sx - half).coerceAtLeast(0), (sy - half).coerceAtLeast(0),
            (sx + half).coerceAtMost(b.width - 1), (sy + half).coerceAtMost(b.height - 1)
        )
        val dst = RectF(pickX - radius, pickY - radius, pickX + radius, pickY + radius)
        val path = Path(); path.addCircle(pickX, pickY, radius, Path.Direction.CW)
        canvas.save(); canvas.clipPath(path)
        canvas.drawBitmap(b, src, dst, null)
        canvas.restore()
        canvas.drawCircle(pickX, pickY, radius, stroke)
        canvas.drawLine(pickX - radius, pickY, pickX + radius, pickY, stroke)
        canvas.drawLine(pickX, pickY - radius, pickX, pickY + radius, stroke)
        val col = FrameHolder.pickedColor ?: 0
        val hex = String.format("#%06X", col and 0xFFFFFF)
        canvas.drawText(hex, pickX + radius + 8, pickY + 8, textPaint)
    }
}
