package com.yuanlingbb.auto.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.yuanlingbb.auto.data.Action
import com.yuanlingbb.auto.data.ActionType
import com.yuanlingbb.auto.data.Task
import com.yuanlingbb.auto.data.TaskStore
import com.yuanlingbb.auto.engine.ActionRunner
import com.yuanlingbb.auto.ui.ActionEditorActivity
import com.yuanlingbb.auto.util.OverlayState

/**
 * 悬浮窗控制台（灵魂交互）：竖排胶囊浮窗，在目标APP之上直接操作。
 * 按钮：执行(开始/停止) / 添加 / 删除 / 列表 / 隐藏 / 设置 / 保存。
 * 添加模式：全屏透明层捕获点击坐标 → 生成"点击"动作 + 屏幕序号标记。
 * 执行时显示底部播放条（任务名 + 进度 + 停止）。
 */
class FloatingConsoleService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var consoleView: LinearLayout? = null
    private var captureView: View? = null
    private var playBarView: LinearLayout? = null
    private val markers = HashMap<Long, TextView>()
    private var addMode = false
    private var deleteMode = false
    private var execBtn: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        val act = intent?.action ?: ACTION_SHOW
        when (act) {
            ACTION_SHOW -> {
                startForeground(NOTIF_ID, buildNotification())
                showConsole()
            }
            ACTION_PLAY -> {
                val tid = intent?.getLongExtra("taskId", -1L) ?: -1L
                val task = TaskStore.get(this, tid)
                if (task != null) {
                    OverlayState.openTask(task, false)
                    showConsole()
                    runTask(task)
                }
            }
            ACTION_STOP -> {
                ActionRunner.requestStop()
            }
            ACTION_HIDE -> removeAllViews()
        }
        return START_STICKY
    }

    // ---------- 控制台浮窗 ----------

    private fun showConsole() {
        if (consoleView != null) return
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(8, 8, 8, 8)
        col.background = roundBg(0xDD2B2B2B.toInt(), 24f)
        col.elevation = 12f

        execBtn = makeBtn("开始", 0xFF7CB342.toInt())
        execBtn?.setOnClickListener {
            val t = OverlayState.ensureTask()
            if (ActionRunner.running) ActionRunner.requestStop()
            else runTask(t)
        }
        col.addView(execBtn)

        col.addView(makeBtn("添加", 0xFF555555.toInt()).apply {
            setOnClickListener { toggleAddMode() }
        })
        col.addView(makeBtn("删除", 0xFF555555.toInt()).apply {
            setOnClickListener { toggleDeleteMode() }
        })
        col.addView(makeBtn("列表", 0xFF555555.toInt()).apply {
            setOnClickListener {
                exitModes()
                val i = Intent(this@FloatingConsoleService, ActionEditorActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this@FloatingConsoleService.startActivity(i)
            }
        })
        col.addView(makeBtn("隐藏", 0xFF555555.toInt()).apply {
            setOnClickListener { toggleMarkers() }
        })
        col.addView(makeBtn("设置", 0xFF555555.toInt()).apply {
            setOnClickListener {
                exitModes()
                val i = Intent(this@FloatingConsoleService, ActionEditorActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this@FloatingConsoleService.startActivity(i)
            }
        })
        col.addView(makeBtn("保存", 0xFF555555.toInt()).apply {
            setOnClickListener {
                val t = OverlayState.currentTask
                if (t == null) {
                    Toast.makeText(this@FloatingConsoleService, "尚未添加任何动作", Toast.LENGTH_SHORT).show()
                } else {
                    TaskStore.upsert(this@FloatingConsoleService, t)
                    OverlayState.isNewTask = false
                    Toast.makeText(this@FloatingConsoleService, "已保存「${t.name}」", Toast.LENGTH_SHORT).show()
                }
            }
        })

        val p = FloatParams().apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
        }
        // 长按拖动
        var downX = 0f; var downY = 0f; var ox = 0; var oy = 0; var dragging = false
        col.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; ox = p.x; oy = p.y; dragging = false; false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - downX) > 8 || Math.abs(e.rawY - downY) > 8) dragging = true
                    if (dragging) {
                        p.x = ox + (e.rawX - downX).toInt()
                        p.y = oy + (e.rawY - downY).toInt()
                        safeUpdate(col, p.build())
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { v.performClick(); dragging }
                else -> false
            }
        }

        try {
            wm.addView(col, p.build())
            consoleView = col
        } catch (e: Exception) {
            Toast.makeText(this, "悬浮窗权限未开启", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeBtn(text: String, bg: Int): TextView {
        val b = TextView(this)
        b.text = text
        b.textSize = 13f
        b.setTextColor(Color.WHITE)
        b.gravity = Gravity.CENTER
        b.setPadding(0, 18, 0, 18)
        b.background = roundBg(bg, 22f)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 6
        b.layoutParams = lp
        return b
    }

    private fun roundBg(color: Int, radius: Float): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radius
        return d
    }

    private class FloatParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val format = PixelFormat.TRANSLUCENT
        var width = WindowManager.LayoutParams.WRAP_CONTENT
        var height = WindowManager.LayoutParams.WRAP_CONTENT
        var x = 0; var y = 0; var gravity = Gravity.TOP or Gravity.START
        fun build(): WindowManager.LayoutParams {
            val lp = WindowManager.LayoutParams(width, height, type, flags, format)
            lp.x = x; lp.y = y; lp.gravity = gravity
            return lp
        }
    }

    // ---------- 添加/删除模式 ----------

    private fun toggleAddMode() {
        if (addMode) { exitModes(); return }
        exitModes()
        addMode = true
        Toast.makeText(this, "添加模式：点击屏幕任意位置即可添加点击动作", Toast.LENGTH_SHORT).show()
        val cap = View(this)
        cap.setBackgroundColor(0x01000000) // 近乎全透明
        val p = FloatParams().apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        cap.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                if (deleteMode) {
                    deleteNearest(e.rawX.toInt(), e.rawY.toInt())
                } else {
                    addTapAction(e.rawX.toInt(), e.rawY.toInt())
                }
                exitModes()
                true
            } else false
        }
        try {
            wm.addView(cap, p.build())
            captureView = cap
        } catch (e: Exception) {
            addMode = false
        }
    }

    private fun toggleDeleteMode() {
        Toast.makeText(this, "删除模式：点击屏幕上的序号标记即可删除", Toast.LENGTH_SHORT).show()
        toggleAddMode() // 复用捕获层，deleteMode 标志区分
    }

    private fun addTapAction(x: Int, y: Int) {
        val t = OverlayState.ensureTask()
        val a = Action(ActionType.TAP)
        a.params.put("x", x)
        a.params.put("y", y)
        t.actions.add(a)
        addMarker(a.id, t.actions.size, x, y)
    }

    private fun deleteNearest(x: Int, y: Int) {
        val t = OverlayState.currentTask ?: return
        var bestId = -1L
        var bestD = Int.MAX_VALUE
        for (i in t.actions.indices) {
            val a = t.actions[i]
            if (a.type == ActionType.TAP) {
                val dx = a.params.optInt("x") - x
                val dy = a.params.optInt("y") - y
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; bestId = a.id }
            }
        }
        if (bestId >= 0 && bestD < 90 * 90) {
            val it = t.actions.iterator()
            while (it.hasNext()) if (it.next().id == bestId) it.remove()
            markers[bestId]?.let { safeRemove(it) }
            markers.remove(bestId)
            renumber()
        }
    }

    private fun renumber() {
        val t = OverlayState.currentTask ?: return
        for (idx in t.actions.indices) {
            val a = t.actions[idx]
            val m = markers[a.id] ?: continue
            m.text = (idx + 1).toString()
        }
    }

    private fun addMarker(actionId: Long, index: Int, x: Int, y: Int) {
        val tv = TextView(this)
        tv.text = index.toString()
        tv.textSize = 11f
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        val d = GradientDrawable()
        d.setColor(0xCC66BB00.toInt())
        d.shape = GradientDrawable.OVAL
        tv.background = d
        val p = FloatParams().apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            width = 44; height = 44
            this.x = x - 22; this.y = y - 22
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            wm.addView(tv, p.build())
            markers[actionId] = tv
        } catch (_: Exception) {}
    }

    private fun toggleMarkers() {
        val hide = markers.values.firstOrNull()?.visibility == View.VISIBLE
        for (m in markers.values) m.visibility = if (hide) View.GONE else View.VISIBLE
    }

    // ---------- 执行 + 播放条 ----------

    private fun runTask(task: Task) {
        if (task.actions.isEmpty() && task.mode != "lua") {
            Toast.makeText(this, "任务没有动作", Toast.LENGTH_SHORT).show()
            return
        }
        execBtn?.text = "停止"
        execBtn?.background = roundBg(0xFFD32F2F.toInt(), 22f)
        showPlayBar(task.name)
        ActionRunner.onProgress = { cur, total ->
            handler.post { setPlayBarText("「${task.name}」 第${cur}/${total}步") }
        }
        ActionRunner.onFinished = { ok, msg ->
            handler.post {
                execBtn?.text = "开始"
                execBtn?.background = roundBg(0xFF7CB342.toInt(), 22f)
                hidePlayBar()
                Toast.makeText(this, if (ok) "执行完成" else (msg.ifEmpty { "执行失败" }), Toast.LENGTH_SHORT).show()
            }
        }
        ActionRunner.run(this, task, 0)
    }

    private fun showPlayBar(name: String) {
        if (playBarView != null) return
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(28, 16, 28, 16)
        bar.background = roundBg(0xEE323232.toInt(), 40f)
        val tv = TextView(this)
        tv.text = "「$name」 准备执行"
        tv.setTextColor(Color.WHITE)
        tv.textSize = 13f
        bar.addView(tv)
        val stop = TextView(this)
        stop.text = "  停止  "
        stop.setTextColor(Color.WHITE)
        stop.textSize = 13f
        stop.background = roundBg(0xFFD32F2F.toInt(), 30f)
        stop.setPadding(20, 8, 20, 8)
        stop.setOnClickListener { ActionRunner.requestStop() }
        bar.addView(stop)

        val p = FloatParams().apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        try {
            wm.addView(bar, p.build())
            playBarView = bar
        } catch (_: Exception) {}
    }

    private fun setPlayBarText(s: String) {
        (playBarView?.getChildAt(0) as? TextView)?.text = s
    }

    private fun hidePlayBar() {
        playBarView?.let { safeRemove(it) }
        playBarView = null
    }

    // ---------- 通用 ----------

    private fun exitModes() {
        addMode = false
        deleteMode = false
        captureView?.let { safeRemove(it) }
        captureView = null
    }

    private fun removeAllViews() {
        exitModes()
        consoleView?.let { safeRemove(it) }
        consoleView = null
        hidePlayBar()
        for (m in markers.values) safeRemove(m)
        markers.clear()
    }

    private fun safeRemove(v: View) {
        try { wm.removeView(v) } catch (_: Exception) {}
    }

    private fun safeUpdate(v: View, lp: WindowManager.LayoutParams) {
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    private fun buildNotification(): Notification {
        val channelId = "console_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "悬浮控制台", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("灵动豹豹")
            .setContentText("悬浮控制台运行中")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
    }

    override fun onDestroy() {
        removeAllViews()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 1002
        const val ACTION_SHOW = "com.yuanlingbb.auto.SHOW_CONSOLE"
        const val ACTION_PLAY = "com.yuanlingbb.auto.PLAY_TASK"
        const val ACTION_STOP = "com.yuanlingbb.auto.STOP_TASK"
        const val ACTION_HIDE = "com.yuanlingbb.auto.HIDE_CONSOLE"

        fun show(ctx: android.content.Context) {
            val i = Intent(ctx, FloatingConsoleService::class.java)
            i.action = ACTION_SHOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun play(ctx: android.content.Context, taskId: Long) {
            val i = Intent(ctx, FloatingConsoleService::class.java)
            i.action = ACTION_PLAY
            i.putExtra("taskId", taskId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun hide(ctx: android.content.Context) {
            val i = Intent(ctx, FloatingConsoleService::class.java)
            i.action = ACTION_HIDE
            ctx.startService(i)
        }
    }
}
