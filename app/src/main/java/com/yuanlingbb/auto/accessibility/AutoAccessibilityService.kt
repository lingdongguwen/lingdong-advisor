package com.yuanlingbb.auto.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍自动化服务：在用户主动开启后，提供点击/滑动/长按/输入/系统按键/多指手势等能力。
 * 全部通过 dispatchGesture 与节点 ACTION_SET_TEXT 实现，不读取任何应用私有数据。
 */
class AutoAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private val handler = Handler(Looper.getMainLooper())

    fun tap(x: Int, y: Int, done: () -> Unit) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        dispatch(path, 60, done)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long, done: () -> Unit) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        dispatch(path, durationMs, done)
    }

    fun longPress(x: Int, y: Int, durationMs: Long, done: () -> Unit) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        dispatch(path, durationMs, done)
    }

    /** 多指手势：每指一条轨迹 {x1,y1,x2,y2,durMs,起始延迟startMs}，同时下发 */
    fun multiGesture(strokes: List<Quad>, done: () -> Unit) {
        if (strokes.isEmpty()) { done(); return }
        val builder = GestureDescription.Builder()
        for (s in strokes) {
            val path = Path().apply {
                moveTo(s.x1.toFloat(), s.y1.toFloat())
                if (s.x2 != s.x1 || s.y2 != s.y1) lineTo(s.x2.toFloat(), s.y2.toFloat())
                else lineTo(s.x1.toFloat(), s.y1.toFloat())
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(path, s.startMs, s.durMs)
            )
        }
        val ok = dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done()
            override fun onCancelled(gestureDescription: GestureDescription?) = done()
        }, handler)
        if (!ok) done()
    }

    data class Quad(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val durMs: Long, val startMs: Long = 0)

    private fun dispatch(path: Path, durationMs: Long, done: () -> Unit) {
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
        val ok = dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done()
            override fun onCancelled(gestureDescription: GestureDescription?) = done()
        }, handler)
        if (!ok) done()
    }

    /** 向当前获得焦点的输入框写入文本（若某应用输入法不支持，则可能无效，属已知限制） */
    fun input(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val bundle = android.os.Bundle()
                bundle.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                focused.recycle()
                true
            } else false
        } catch (e: Exception) {
            false
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
    }

    /** 按控件 ID 查找节点（如 com.android.calculator2/id/digit_5） */
    fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return try {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            nodes.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /** 点击控件节点（优先 ACTION_CLICK，失败则点其中心坐标） */
    fun clickNode(node: AccessibilityNodeInfo, done: () -> Unit) {
        val ok = try { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (e: Exception) { false }
        if (ok) { done(); return }
        val r = android.graphics.Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) { done(); return }
        tap(r.centerX(), r.centerY(), done)
    }

    /** 遍历当前窗口节点，返回所有带 viewId 的控件（id, 摘要），用于"辅助查找控件ID" */
    fun dumpViewIds(): List<Pair<String, String>> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val id = try { node.viewIdResourceName } catch (_: Exception) { null }
            if (!id.isNullOrEmpty()) {
                val raw = node.text?.toString()
                    ?: node.contentDescription?.toString()
                    ?: node.className?.toString() ?: ""
                val desc = if (raw.length > 24) raw.take(24) + "…" else raw
                out.add(Pair(id, desc))
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        try { root.recycle() } catch (_: Exception) {}
        return out.distinctBy { it.first }.take(200)
    }

    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotificationBar() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun takeScreenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    /** 当前前台窗口包名（用于"打开APP"条件判断） */
    fun frontPackage(): String? {
        return try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
    }

    companion object {
        var instance: AutoAccessibilityService? = null
    }
}
