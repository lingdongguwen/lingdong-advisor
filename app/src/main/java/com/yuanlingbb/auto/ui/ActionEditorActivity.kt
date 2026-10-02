package com.yuanlingbb.auto.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.yuanlingbb.auto.data.Action
import com.yuanlingbb.auto.data.ActionType
import com.yuanlingbb.auto.data.Task
import com.yuanlingbb.auto.data.TaskStore
import com.yuanlingbb.auto.engine.ActionRunner
import com.yuanlingbb.auto.overlay.FloatingConsoleService
import com.yuanlingbb.auto.util.InsetsUtil
import com.yuanlingbb.auto.util.OverlayState

/**
 * 简易模式动作编辑器：动作列表管理（增删改/复制/批量复制/上移下移/从该步运行）
 * + 完整添加动作菜单（分类分组） + 每动作表单设置。
 */
class ActionEditorActivity : Activity() {

    private lateinit var list: ListView
    private lateinit var nameEdit: EditText
    private lateinit var adapter: ActionAdapter
    private var task: Task = Task()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        task = OverlayState.currentTask ?: Task().also {
            OverlayState.currentTask = it
            OverlayState.isNewTask = true
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val pad = dp(16)
        root.setPadding(pad, pad, pad, pad)
        root.setBackgroundColor(Color.WHITE)

        // 顶栏：任务名 + 保存
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        nameEdit = EditText(this)
        nameEdit.setText(task.name)
        nameEdit.textSize = 16f
        nameEdit.hint = "任务名称"
        top.addView(nameEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val saveBtn = greenBtn(this, "保存")
        saveBtn.setOnClickListener {
            task.name = nameEdit.text.toString().trim().ifEmpty { "未命名" }
            TaskStore.upsert(this, task)
            OverlayState.isNewTask = false
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            adapter.notifyDataSetChanged()
        }
        top.addView(saveBtn)
        root.addView(top)

        // 运行按钮行
        val runRow = LinearLayout(this)
        runRow.orientation = LinearLayout.HORIZONTAL
        val runBtn = greenBtn(this, "▶ 运行")
        runBtn.setOnClickListener { runFrom(0) }
        runRow.addView(runBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val consoleBtn = greenBtn(this, "打开悬浮窗")
        consoleBtn.setOnClickListener {
            FloatingConsoleService.show(this)
            finish()
        }
        val lp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp2.leftMargin = dp(8)
        runRow.addView(consoleBtn, lp2)
        val lp3 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp3.topMargin = dp(10)
        root.addView(runRow, lp3)

        // 动作列表
        adapter = ActionAdapter()
        list = ListView(this)
        list.adapter = adapter
        list.dividerHeight = dp(6)
        val lp4 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        lp4.topMargin = dp(10)
        root.addView(list, lp4)
        list.setOnItemClickListener { _, _, pos, _ ->
            ActionForms.show(this, task.actions[pos]) { adapter.notifyDataSetChanged() }
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            showItemMenu(pos)
            true
        }

        // 添加动作按钮
        val addBtn = Button(this)
        addBtn.text = "＋ 添加动作"
        addBtn.setTextColor(Color.WHITE)
        addBtn.textSize = 15f
        addBtn.background = roundRect(0xFF66BB00.toInt(), dp(14).toFloat())
        val lp5 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp5.topMargin = dp(10)
        root.addView(addBtn, lp5)
        addBtn.setOnClickListener { showAddMenu() }

        InsetsUtil.fit(root)
        setContentView(root)
    }

    private fun runFrom(index: Int) {
        if (task.actions.isEmpty()) {
            Toast.makeText(this, "请先添加动作", Toast.LENGTH_SHORT).show()
            return
        }
        FloatingConsoleService.show(this)
        ActionRunner.onFinished = { ok, msg ->
            runOnUiThread {
                Toast.makeText(this, if (ok) "执行完成" else (msg.ifEmpty { "执行失败" }), Toast.LENGTH_SHORT).show()
            }
        }
        ActionRunner.run(this, task, index)
    }

    private fun showItemMenu(pos: Int) {
        val opts = arrayOf("复制动作", "批量复制添加", "上移", "下移", "删除", "从该动作开始运行")
        AlertDialog.Builder(this)
            .setTitle("第${pos + 1}步 · ${task.actions[pos].type.label}")
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> copyAction(pos, 1)
                    1 -> copyAction(pos, 3)
                    2 -> move(pos, -1)
                    3 -> move(pos, 1)
                    4 -> { task.actions.removeAt(pos); adapter.notifyDataSetChanged() }
                    5 -> runFrom(pos)
                }
            }
            .show()
    }

    private fun copyAction(pos: Int, times: Int) {
        val src = task.actions[pos]
        for (i in 0 until times) {
            val o = src.toJson()
            val copy = Action.fromJson(o)
            copy.id = System.nanoTime() + i
            task.actions.add(pos + 1 + i, copy)
        }
        adapter.notifyDataSetChanged()
    }

    private fun move(pos: Int, delta: Int) {
        val to = pos + delta
        if (to < 0 || to >= task.actions.size) return
        val a = task.actions.removeAt(pos)
        task.actions.add(to, a)
        adapter.notifyDataSetChanged()
    }

    private fun showAddMenu() {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val pad = dp(16)
        container.setPadding(pad, pad, pad, pad)
        var lastCat = ""
        for (t in ActionType.entries) {
            if (t.category != lastCat) {
                lastCat = t.category
                val h = TextView(this)
                h.text = "── ${t.category} ──"
                h.textSize = 12f
                h.setTextColor(0xFF888888.toInt())
                h.gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(8); lp.bottomMargin = dp(4)
                h.layoutParams = lp
                container.addView(h)
            }
            val b = Button(this)
            b.text = t.label
            b.textSize = 13f
            b.setTextColor(Color.WHITE)
            b.background = roundRect(0xFF7CB342.toInt(), dp(10).toFloat())
            container.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            b.setOnClickListener {
                val a = Action(t)
                ActionForms.show(this, a, { adapter.notifyDataSetChanged() }) {
                    task.actions.add(a)
                    adapter.notifyDataSetChanged()
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("添加动作")
            .setView(ScrollView(this).also { it.addView(container) })
            .setNegativeButton("关闭", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            ActionForms.handleResult(requestCode, data)
            ActionForms.handleGallery(requestCode, data, this)
        }
    }

    // ---------- 适配器 ----------

    private inner class ActionAdapter : BaseAdapter() {
        override fun getCount(): Int = task.actions.size
        override fun getItem(position: Int): Any = task.actions[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = LinearLayout(this@ActionEditorActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(10), dp(10), dp(10), dp(10))
            row.background = roundRect(0xFFF3F6EE.toInt(), dp(10).toFloat())

            val idx = TextView(this@ActionEditorActivity)
            idx.text = "${position + 1}"
            idx.textSize = 12f
            idx.setTextColor(Color.WHITE)
            idx.gravity = Gravity.CENTER
            idx.background = roundRect(0xFF66BB00.toInt(), dp(16).toFloat())
            idx.layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            row.addView(idx)

            val mid = LinearLayout(this@ActionEditorActivity)
            mid.orientation = LinearLayout.VERTICAL
            val title = TextView(this@ActionEditorActivity)
            title.text = "${task.actions[position].type.label}  ${task.actions[position].summary()}"
            title.textSize = 14f
            title.setTextColor(0xFF222222.toInt())
            mid.addView(title)
            val meta = TextView(this@ActionEditorActivity)
            val c = task.actions[position].condition
            meta.text = "延迟${task.actions[position].delayMs}ms ×${task.actions[position].times}" +
                    (if (c.type.isNotEmpty()) " · 条件:${c.type}" else "")
            meta.textSize = 11f
            meta.setTextColor(0xFF888888.toInt())
            mid.addView(meta)
            val mlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            mlp.leftMargin = dp(10)
            row.addView(mid, mlp)

            val edit = TextView(this@ActionEditorActivity)
            edit.text = "✎ 编辑"
            edit.textSize = 14f
            edit.setTextColor(0xFF66BB00.toInt())
            edit.setPadding(dp(10), dp(8), dp(10), dp(8))
            edit.setOnClickListener {
                ActionForms.show(this@ActionEditorActivity, task.actions[position]) { adapter.notifyDataSetChanged() }
            }
            row.addView(edit)

            val del = TextView(this@ActionEditorActivity)
            del.text = "🗑 删除"
            del.textSize = 14f
            del.setTextColor(0xFFD32F2F.toInt())
            del.setPadding(dp(16), dp(8), dp(10), dp(8))
            del.setOnClickListener {
                AlertDialog.Builder(this@ActionEditorActivity)
                    .setTitle("确认删除")
                    .setMessage("确定删除第${position + 1}步「${task.actions[position].type.label}」？")
                    .setPositiveButton("删除") { _, _ ->
                        task.actions.removeAt(position)
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            row.addView(del)
            return row
        }
    }

    // ---------- 构件 ----------

    private fun greenBtn(ctx: Activity, text: String): Button {
        val b = Button(ctx)
        b.text = text
        b.textSize = 13f
        b.setTextColor(Color.WHITE)
        b.background = roundRect(0xFF66BB00.toInt(), dp(12).toFloat())
        return b
    }

    private fun roundRect(color: Int, radius: Float): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radius
        return d
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
