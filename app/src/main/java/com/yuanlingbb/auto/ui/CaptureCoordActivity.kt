package com.yuanlingbb.auto.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.yuanlingbb.auto.util.FrameHolder

/**
 * 全屏透明拾取器（表单辅助）：
 * mode=point     → 点击返回单点坐标
 * mode=swipe     → 按住拖动返回起点/终点
 * mode=color     → 点击返回取色（底图显示截屏帧或外部图片辅助定位）
 * mode=region    → 拖框返回区域矩形
 * mode=multigesture → 依次点击 N 个指位，返回全部坐标
 *
 * 可选 extra：bitmapPath（相册图片）用于 color 模式取色。
 */
class CaptureCoordActivity : Activity() {

    private var mode = "point"
    private var downX = 0; private var downY = 0
    private var curX = 0; private var curY = 0
    private var hasDrag = false
    private var count = 2
    private var extraBmp: android.graphics.Bitmap? = null
    private val points = mutableListOf<Pair<Int, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: "point"
        count = intent.getIntExtra("count", 2)
        val bmpPath = intent.getStringExtra("bitmapPath")
        if (bmpPath != null) {
            try { extraBmp = BitmapFactory.decodeFile(bmpPath) } catch (_: Exception) { extraBmp = null }
        }
        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                super.onDraw(c)
                if (mode == "color") {
                    val bmp = extraBmp ?: FrameHolder.latestBitmap
                    if (bmp != null) {
                        val vw = width.toFloat(); val vh = height.toFloat()
                        val scale = Math.min(vw / bmp.width, vh / bmp.height)
                        val w = bmp.width * scale; val h = bmp.height * scale
                        val l = (vw - w) / 2; val t = (vh - h) / 2
                        c.drawBitmap(bmp, null, android.graphics.RectF(l, t, l + w, t + h), null)
                        val p = Paint(); p.color = Color.parseColor("#66BB00"); p.textSize = 34f
                        c.drawText("点击图片选择颜色（${if (extraBmp != null) "图片" else "截屏"}模式）", 24f, 50f, p)
                    } else {
                        val p = Paint(); p.color = Color.WHITE; p.textSize = 40f
                        c.drawText("请先在主界面完成一次截屏", 60f, height.toFloat() / 2f, p)
                    }
                }
                if ((mode == "region" || mode == "swipe") && hasDrag) {
                    val p = Paint()
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 6f
                    p.color = Color.parseColor("#66BB00")
                    c.drawRect(Math.min(downX, curX).toFloat(), Math.min(downY, curY).toFloat(),
                        Math.max(downX, curX).toFloat(), Math.max(downY, curY).toFloat(), p)
                }
                if (mode == "multigesture") {
                    val p = Paint(); p.style = Paint.Style.FILL
                    for (i in points.indices) {
                        val (px, py) = points[i]
                        p.color = Color.parseColor("#66BB00")
                        c.drawCircle(px.toFloat(), py.toFloat(), 26f, p)
                        p.color = Color.WHITE; p.textSize = 30f; p.textAlign = Paint.Align.CENTER
                        c.drawText((i + 1).toString(), px.toFloat(), py.toFloat() + 10f, p)
                    }
                    val tip = Paint(); tip.color = Color.WHITE; tip.textSize = 36f
                    c.drawText("依次点击 ${points.size}/$count 个指位", 24f, 50f, tip)
                }
            }
        }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x.toInt(); downY = e.y.toInt()
                    curX = downX; curY = downY
                    hasDrag = false
                    if (mode == "point") {
                        finishWith("x", downX, "y", downY)
                        return@setOnTouchListener true
                    }
                    if (mode == "multigesture") {
                        points.add(Pair(downX, downY))
                        if (points.size >= count) {
                            val arr = IntArray(points.size * 2)
                            points.forEachIndexed { i, (x, y) -> arr[i * 2] = x; arr[i * 2 + 1] = y }
                            val i2 = Intent(); i2.putExtra("points", arr)
                            setResult(RESULT_OK, i2); finish()
                        } else {
                            v.invalidate()
                        }
                        return@setOnTouchListener true
                    }
                    if (mode == "color") {
                        val bmp = extraBmp ?: FrameHolder.latestBitmap
                        if (bmp == null) {
                            Toast.makeText(this, "无可用截屏/图片帧", Toast.LENGTH_SHORT).show()
                            finish(); return@setOnTouchListener true
                        }
                        val vw = v.width.toFloat(); val vh = v.height.toFloat()
                        val scale = Math.min(vw / bmp.width, vh / bmp.height)
                        val bx = ((downX - (vw - bmp.width * scale) / 2) / scale).toInt()
                        val by = ((downY - (vh - bmp.height * scale) / 2) / scale).toInt()
                        val px = if (bx in 0 until bmp.width && by in 0 until bmp.height) bmp.getPixel(bx, by) else 0
                        val i2 = Intent()
                        i2.putExtra("color", px)
                        i2.putExtra("x", bx); i2.putExtra("y", by)
                        setResult(RESULT_OK, i2)
                        finish()
                        return@setOnTouchListener true
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    curX = e.x.toInt(); curY = e.y.toInt()
                    hasDrag = true
                    v.invalidate()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (mode == "swipe") {
                        val i2 = Intent()
                        i2.putExtra("x1", downX); i2.putExtra("y1", downY)
                        i2.putExtra("x2", e.x.toInt()); i2.putExtra("y2", e.y.toInt())
                        setResult(RESULT_OK, i2)
                        finish()
                    } else if (mode == "region") {
                        val i2 = Intent()
                        i2.putExtra("rect", intArrayOf(
                            Math.min(downX, curX), Math.min(downY, curY),
                            Math.max(downX, curX), Math.max(downY, curY)))
                        setResult(RESULT_OK, i2)
                        finish()
                    }
                    true
                }
                else -> false
            }
        }
        setContentView(v)
    }

    private fun finishWith(k1: String, v1: Int, k2: String, v2: Int) {
        val i = Intent()
        i.putExtra(k1, v1); i.putExtra(k2, v2)
        setResult(RESULT_OK, i)
        finish()
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
    }
}
