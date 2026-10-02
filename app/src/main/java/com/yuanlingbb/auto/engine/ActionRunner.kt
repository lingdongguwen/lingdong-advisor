package com.yuanlingbb.auto.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import com.yuanlingbb.auto.accessibility.AutoAccessibilityService
import com.yuanlingbb.auto.data.Action
import com.yuanlingbb.auto.data.Condition
import com.yuanlingbb.auto.data.Task
import com.yuanlingbb.auto.data.TaskStore
import com.yuanlingbb.auto.util.FrameHolder
import com.yuanlingbb.auto.vision.VisionEngine
import org.json.JSONArray
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 动作执行引擎：按序执行动作组，支持延迟/次数/运行条件/失败策略。
 * 识别类动作依赖 FrameHolder.latestBitmap（需先开启录屏授权，截屏服务持续镜像整屏）。
 */
object ActionRunner {

    @Volatile var running = false
    @Volatile private var stopRequested = false
    @Volatile private var depth = 0

    var onProgress: ((Int, Int) -> Unit)? = null
    var onFinished: ((Boolean, String) -> Unit)? = null

    // 变量系统（简化实现：任务内与全局共两块内存表）
    val taskVars = HashMap<String, String>()
    val globalVars = HashMap<String, String>()

    fun requestStop() { stopRequested = true }

    fun run(context: Context, task: Task, startIndex: Int = 0) {
        if (running) return
        stopRequested = false
        Thread {
            depth = 0
            execute(context.applicationContext, task, startIndex)
        }.start()
    }

    private fun execute(ctx: Context, task: Task, startIndex: Int): Boolean {
        depth++
        running = true
        var ok = true
        var msg = ""
        val total = task.actions.size
        var i = startIndex
        while (i < total) {
            if (stopRequested) { msg = "已手动停止"; ok = false; break }
            val a = task.actions[i]
            onProgress?.invoke(i + 1, total)
            // 运行条件
            if (a.condition.type.isNotEmpty()) {
                val met = checkCondition(ctx, a.condition)
                if (met && a.condition.abortOnSuccess) { msg = "条件满足，按设定中止"; break }
                val shouldRun = if (a.condition.invert) !met else met
                if (!shouldRun) { i++; continue }
            }
            var stepOk = true
            val times = if (a.times < 1) 1 else a.times
            for (t in 0 until times) {
                if (stopRequested) { stepOk = false; msg = "已手动停止"; break }
                if (a.delayMs > 0) { try { Thread.sleep(a.delayMs.toLong()) } catch (_: InterruptedException) {} }
                stepOk = execAction(ctx, a)
                if (!stepOk) {
                    msg = "第${i + 1}步「${a.type.label}」执行失败"
                    // 失败策略：next=跳过继续 / stop=终止
                    if (a.params.optString("onFail", "stop") == "next") { stepOk = true; msg = "" }
                    break
                }
            }
            if (!stepOk) { ok = false; break }
            i++
        }
        depth--
        if (depth <= 0) {
            running = false
            val finalOk = ok
            val finalMsg = msg
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onFinished?.invoke(finalOk, finalMsg)
            }
        }
        return ok
    }

    private fun checkCondition(ctx: Context, c: Condition): Boolean {
        return when (c.type) {
            "image" -> {
                val tpl = loadTemplate(ctx, c.params.optString("template"))
                    ?: return false
                val sim = c.params.optInt("similarity", 90) / 100.0
                try { VisionEngine.findImage(tpl, sim).isNotEmpty() } catch (e: Exception) { false }
            }
            "appOpen" -> {
                val pkg = AutoAccessibilityService.instance?.frontPackage()
                val arr = c.params.optJSONArray("packages")
                if (arr == null || arr.length() == 0) return true // 未选=全部APP生效
                for (i in 0 until arr.length()) if (arr.optString(i) == pkg) return true
                false
            }
            else -> true
        }
    }

    fun loadTemplate(ctx: Context, name: String): android.graphics.Bitmap? {
        if (name.isEmpty()) return null
        val f = File(ctx.filesDir, "templates/$name")
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }

    private fun awaitGesture(block: (done: () -> Unit) -> Unit): Boolean {
        val latch = CountDownLatch(1)
        block { latch.countDown() }
        return latch.await(15, TimeUnit.SECONDS)
    }

    private fun execAction(ctx: Context, a: Action): Boolean {
        val svc = AutoAccessibilityService.instance
        return try {
            when (a.type) {
                com.yuanlingbb.auto.data.ActionType.TAP ->
                    svc != null && awaitGesture { svc.tap(a.params.optInt("x"), a.params.optInt("y"), it) }
                com.yuanlingbb.auto.data.ActionType.DOUBLE_TAP -> {
                    if (svc == null) return false
                    val r1 = awaitGesture { svc.tap(a.params.optInt("x"), a.params.optInt("y"), it) }
                    Thread.sleep(90)
                    r1 && awaitGesture { svc.tap(a.params.optInt("x"), a.params.optInt("y"), it) }
                }
                com.yuanlingbb.auto.data.ActionType.LONG_PRESS ->
                    svc != null && awaitGesture {
                        svc.longPress(a.params.optInt("x"), a.params.optInt("y"),
                            a.params.optInt("longMs", 800).toLong(), it)
                    }
                com.yuanlingbb.auto.data.ActionType.SWIPE ->
                    svc != null && awaitGesture {
                        svc.swipe(a.params.optInt("x1"), a.params.optInt("y1"),
                            a.params.optInt("x2"), a.params.optInt("y2"),
                            a.params.optInt("durMs", 500).toLong(), it)
                    }
                com.yuanlingbb.auto.data.ActionType.INPUT_TEXT ->
                    svc?.input(a.params.optString("text")) ?: false
                com.yuanlingbb.auto.data.ActionType.CLICK_IMAGE -> {
                    val tpl = loadTemplate(ctx, a.params.optString("template"))
                        ?: return false
                    val sim = a.params.optInt("similarity", 90) / 100.0
                    val ms = VisionEngine.findImage(tpl, sim)
                    if (ms.isEmpty()) return false
                    svc != null && awaitGesture { svc.tap(ms[0].x, ms[0].y, it) }
                }
                com.yuanlingbb.auto.data.ActionType.CLICK_TEXT -> {
                    val target = a.params.optString("text")
                    if (target.isEmpty()) return false
                    val lines = VisionEngine.ocr()
                    val exact = a.params.optBoolean("exact", false)
                    var hit: com.yuanlingbb.auto.vision.OcrLine? = null
                    for (l in lines) {
                        if ((exact && l.text == target) || (!exact && l.text.contains(target))) {
                            hit = l; break
                        }
                    }
                    if (hit == null) return false
                    svc != null && awaitGesture {
                        svc.tap(hit.x + hit.w / 2, hit.y + hit.h / 2, it)
                    }
                }
                com.yuanlingbb.auto.data.ActionType.CLICK_CONTROL -> {
                    if (svc == null) return false
                    val node = svc.findNodeByViewId(a.params.optString("viewId"))
                        ?: return false
                    awaitGesture { svc.clickNode(node, it) }
                }
                com.yuanlingbb.auto.data.ActionType.CLICK_COLOR -> {
                    val pts = VisionEngine.findColor(
                        a.params.optInt("color"), a.params.optInt("tolerance", 0))
                    if (pts.isEmpty()) return false
                    svc != null && awaitGesture { svc.tap(pts[0].first, pts[0].second, it) }
                }
                com.yuanlingbb.auto.data.ActionType.EXTRACT_TEXT -> {
                    val lines = VisionEngine.ocr()
                    val sb = StringBuilder()
                    val rect = parseRect(a.params)
                    for (l in lines) {
                        val cx = l.x + l.w / 2
                        val cy = l.y + l.h / 2
                        if (rect == null || (cx >= rect[0] && cx <= rect[2] && cy >= rect[1] && cy <= rect[3])) {
                            sb.append(l.text).append("\n")
                        }
                    }
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("text", sb.toString()))
                    true
                }
                com.yuanlingbb.auto.data.ActionType.OPEN_APP -> {
                    val pkg = a.params.optString("package")
                    if (pkg.isEmpty()) return false
                    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                        ?: return false
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                    Thread.sleep(a.params.optInt("waitMs", 1000).toLong())
                    true
                }
                com.yuanlingbb.auto.data.ActionType.OPEN_WEB -> {
                    val url = a.params.optString("url")
                    if (url.isEmpty()) return false
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                    true
                }
                com.yuanlingbb.auto.data.ActionType.KEY_BACK -> svc != null && svc.pressBack()
                com.yuanlingbb.auto.data.ActionType.KEY_HOME -> svc != null && svc.pressHome()
                com.yuanlingbb.auto.data.ActionType.RECENTS -> svc != null && svc.pressRecents()
                com.yuanlingbb.auto.data.ActionType.NOTIF_BAR -> svc != null && svc.openNotificationBar()
                com.yuanlingbb.auto.data.ActionType.SCREENSHOT -> svc != null && svc.takeScreenshot()
                com.yuanlingbb.auto.data.ActionType.PLAY_SOUND -> {
                    try {
                        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                        Thread.sleep(250)
                        tg.release()
                    } catch (_: Exception) {}
                    true
                }
                com.yuanlingbb.auto.data.ActionType.MULTI_GESTURE -> {
                    if (svc == null) return false
                    val arr = a.params.optJSONArray("strokes") ?: return false
                    val strokes = mutableListOf<AutoAccessibilityService.Quad>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        strokes.add(AutoAccessibilityService.Quad(
                            o.optInt("x1"), o.optInt("y1"), o.optInt("x2"), o.optInt("y2"),
                            o.optInt("dur", 300).toLong()))
                    }
                    awaitGesture { svc.multiGesture(strokes, it) }
                }
                com.yuanlingbb.auto.data.ActionType.VAR_OP -> {
                    val name = a.params.optString("name")
                    if (name.isEmpty()) return false
                    val op = a.params.optString("op", "set")
                    val scope = a.params.optString("scope", "task")
                    val table = if (scope == "global") globalVars else taskVars
                    if (op == "add") {
                        val old = table[name]?.toIntOrNull() ?: 0
                        table[name] = (old + a.params.optInt("value", 1)).toString()
                    } else if (op == "reset") {
                        table[name] = "0"
                    } else {
                        table[name] = a.params.optString("value")
                    }
                    true
                }
                com.yuanlingbb.auto.data.ActionType.SUB_TASK -> {
                    if (depth >= 3) return false // 防无限递归
                    val sub = TaskStore.get(ctx, a.params.optLong("taskId", -1L))
                        ?: return false
                    execute(ctx, sub, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseRect(params: org.json.JSONObject): IntArray? {
        val arr = params.optJSONArray("rect") ?: return null
        if (arr.length() < 4) return null
        return intArrayOf(arr.optInt(0), arr.optInt(1), arr.optInt(2), arr.optInt(3))
    }
}
