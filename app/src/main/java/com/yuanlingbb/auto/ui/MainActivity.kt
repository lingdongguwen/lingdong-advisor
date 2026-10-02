package com.yuanlingbb.auto.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.yuanlingbb.auto.R
import com.yuanlingbb.auto.accessibility.AutoAccessibilityService
import com.yuanlingbb.auto.data.Script
import com.yuanlingbb.auto.data.ScriptStore
import com.yuanlingbb.auto.data.Task
import com.yuanlingbb.auto.data.TaskStore
import com.yuanlingbb.auto.overlay.FloatingConsoleService
import com.yuanlingbb.auto.util.InsetsUtil
import com.yuanlingbb.auto.util.OverlayState

/**
 * 主界面：卡片导航（简易模式 / Lua 脚本 / 教程 / 悬浮控制台）+ 统一任务列表。
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        InsetsUtil.fit(findViewById(R.id.rootMain))

        findViewById<TextView>(R.id.cardSimple).setOnClickListener { newSimpleTask() }
        findViewById<TextView>(R.id.cardLua).setOnClickListener { newLuaScript() }
        findViewById<TextView>(R.id.cardTutorial).setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java))
        }
        findViewById<TextView>(R.id.cardConsole).setOnClickListener { openConsole() }
    }

    override fun onResume() {
        super.onResume()
        refreshTasks()
    }

    private fun newSimpleTask() {
        val t = Task()
        t.name = "未命名"
        OverlayState.openTask(t, true)
        startActivity(Intent(this, ActionEditorActivity::class.java))
    }

    private fun newLuaScript() {
        startActivity(Intent(this, ScriptEditorActivity::class.java))
    }

    private fun openConsole() {
        if (!Settings.canDrawOverlays(this)) {
            toast("请先在权限引导中开启悬浮窗权限")
            startActivity(Intent(this, PermissionGuideActivity::class.java))
            return
        }
        FloatingConsoleService.show(this)
        toast("悬浮控制台已开启，到目标APP上操作吧")
        moveTaskToBack(true)
    }

    private fun refreshTasks() {
        val col = findViewById<LinearLayout>(R.id.llTasks)
        col.removeAllViews()
        var count = 0
        // 简易模式任务
        for (t in TaskStore.all(this)) {
            if (t.mode == "lua") continue
            col.addView(taskRow(t.name, "简易", t.createdAt) { openSimple(t) },
                colLayoutParams())
            col.addView(actionRow({ openSimple(t) }, { runSimple(t) }, { deleteSimple(t) }),
                colLayoutParams())
            count++
        }
        // Lua 脚本
        for (s in ScriptStore.list(this)) {
            col.addView(taskRow(s.name, "Lua", s.updated) { openLua(s) },
                colLayoutParams())
            col.addView(actionRow({ openLua(s) }, { runLua(s) }, { deleteLua(s) }),
                colLayoutParams())
            count++
        }
        findViewById<TextView>(R.id.tvEmpty).visibility =
            if (count == 0) View.VISIBLE else View.GONE
    }

    private fun colLayoutParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** 任务行：名称 + 类型标签 */
    private fun taskRow(name: String, tag: String, ts: Long, onOpen: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(10), dp(12), dp(2))
        row.setOnClickListener { onOpen() }

        val tvName = TextView(this)
        tvName.text = name
        tvName.textSize = 15f
        tvName.setTextColor(0xFF222222.toInt())
        row.addView(tvName, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val tvTag = TextView(this)
        tvTag.text = tag
        tvTag.textSize = 11f
        tvTag.setTextColor(Color.WHITE)
        tvTag.background = tagBg()
        tvTag.setPadding(dp(8), dp(2), dp(8), dp(2))
        row.addView(tvTag)
        return row
    }

    /** 操作行：✎ 编辑 / ▶ 运行 / 🗑 删除 */
    private fun actionRow(onEdit: () -> Unit, onRun: () -> Unit, onDelete: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(20), 0, dp(12), dp(8))

        val edit = TextView(this)
        edit.text = "✎ 编辑"
        edit.textSize = 13f
        edit.setTextColor(0xFF66BB00.toInt())
        edit.setPadding(0, dp(6), dp(16), dp(6))
        edit.setOnClickListener { onEdit() }
        row.addView(edit)

        val run = TextView(this)
        run.text = "▶ 运行"
        run.textSize = 13f
        run.setTextColor(0xFF1565C0.toInt())
        run.setPadding(0, dp(6), dp(16), dp(6))
        run.setOnClickListener { onRun() }
        row.addView(run)

        val del = TextView(this)
        del.text = "🗑 删除"
        del.textSize = 13f
        del.setTextColor(0xFFD32F2F.toInt())
        del.setPadding(0, dp(6), 0, dp(6))
        del.setOnClickListener { onDelete() }
        row.addView(del)
        return row
    }

    private fun tagBg(): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(0xFF7CB342.toInt())
        d.cornerRadius = dp(8).toFloat()
        return d
    }

    // ---------- 任务操作 ----------

    private fun openSimple(t: Task) {
        OverlayState.openTask(t, false)
        startActivity(Intent(this, ActionEditorActivity::class.java))
    }

    private fun runSimple(t: Task) {
        if (AutoAccessibilityService.instance == null) {
            toast("请先开启无障碍服务")
            startActivity(Intent(this, PermissionGuideActivity::class.java))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("请先开启悬浮窗权限")
            startActivity(Intent(this, PermissionGuideActivity::class.java))
            return
        }
        FloatingConsoleService.play(this, t.id)
        moveTaskToBack(true)
    }

    private fun deleteSimple(t: Task) {
        TaskStore.delete(this, t.id)
        refreshTasks()
    }

    private fun openLua(s: Script) {
        val i = Intent(this, ScriptEditorActivity::class.java)
        i.putExtra("id", s.id)
        startActivity(i)
    }

    private fun runLua(s: Script) {
        if (AutoAccessibilityService.instance == null) {
            toast(getString(R.string.toast_accessibility_off))
            startActivity(Intent(this, PermissionGuideActivity::class.java))
            return
        }
        val i = Intent(this, CapturePreviewActivity::class.java)
        i.putExtra("runScriptId", s.id)
        startActivity(i)
    }

    private fun deleteLua(s: Script) {
        ScriptStore.delete(this, s.id)
        refreshTasks()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
