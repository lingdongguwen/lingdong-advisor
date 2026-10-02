package com.yuanlingbb.auto.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.yuanlingbb.auto.util.FrameHolder
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class Match(val x: Int, val y: Int, val score: Double)
data class OcrLine(val text: String, val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * 视觉引擎：找色（纯 Kotlin 像素扫描）+ 找图（OpenCV 模板匹配）+ OCR（ML Kit 中文）。
 * 所有操作均在 FrameHolder.latestBitmap（整屏帧）上进行，覆盖手机全屏范围。
 */
object VisionEngine {

    @Volatile private var ocvReady = false

    private fun ensureOCV(): Boolean {
        if (!ocvReady) {
            ocvReady = try {
                OpenCVLoader.initDebug()
            } catch (_: Exception) {
                false
            }
        }
        return ocvReady
    }

    /** 返回所有命中点（位图坐标），命中数越小说明越精确 */
    fun findColor(targetColor: Int, tolerance: Int = 0): List<Pair<Int, Int>> {
        val bmp = FrameHolder.latestBitmap ?: return emptyList()
        val tr = Color.red(targetColor)
        val tg = Color.green(targetColor)
        val tb = Color.blue(targetColor)
        val pts = mutableListOf<Pair<Int, Int>>()
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            if (Math.abs(Color.red(c) - tr) <= tolerance &&
                Math.abs(Color.green(c) - tg) <= tolerance &&
                Math.abs(Color.blue(c) - tb) <= tolerance
            ) {
                pts.add(Pair(i % w, i / w))
            }
        }
        return pts
    }

    /** 模板匹配：返回命中点列表（按相似度降序，已做非极大抑制），最多 40 个 */
    fun findImage(template: Bitmap, threshold: Double = 0.7): List<Match> {
        if (!ensureOCV()) return emptyList()
        val scene = FrameHolder.latestBitmap ?: return emptyList()
        if (template.width > scene.width || template.height > scene.height) return emptyList()

        val sceneMat = Mat()
        val tmplMat = Mat()
        Utils.bitmapToMat(scene, sceneMat)
        Utils.bitmapToMat(template, tmplMat)
        Imgproc.cvtColor(sceneMat, sceneMat, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(tmplMat, tmplMat, Imgproc.COLOR_RGBA2GRAY)

        val result = Mat()
        Imgproc.matchTemplate(sceneMat, tmplMat, result, Imgproc.TM_CCOEFF_NORMED)

        val cols = result.cols()
        val rows = result.rows()
        val tw = tmplMat.cols()
        val th = tmplMat.rows()
        val matches = mutableListOf<Match>()
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val score = result.get(y, x)[0]
                if (score >= threshold) {
                    matches.add(Match(x + tw / 2, y + th / 2, score.toDouble()))
                }
            }
        }
        // 非极大抑制：去除模板尺寸范围内相互重叠的重复命中
        matches.sortByDescending { it.score }
        val kept = mutableListOf<Match>()
        for (m in matches) {
            if (kept.none { Math.abs(it.x - m.x) < tw / 2 && Math.abs(it.y - m.y) < th / 2 }) {
                kept.add(m)
            }
            if (kept.size >= 40) break
        }
        return kept
    }

    /** OCR：返回文本行（含坐标），同步封装 ML Kit 异步调用 */
    fun ocr(): List<OcrLine> {
        val bmp = FrameHolder.latestBitmap ?: return emptyList()
        val input = InputImage.fromBitmap(bmp, 0)
        val client = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val latch = CountDownLatch(1)
        val results = mutableListOf<OcrLine>()
        var err: Exception? = null
        client.process(input)
            .addOnSuccessListener { text ->
                for (block in text.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox
                        if (box != null) {
                            results.add(OcrLine(line.text, box.left, box.top, box.width(), box.height()))
                        }
                    }
                }
                latch.countDown()
            }
            .addOnFailureListener { e ->
                err = e
                latch.countDown()
            }
        latch.await(15, TimeUnit.SECONDS)
        client.close()
        if (err != null) return emptyList()
        return results
    }
}
