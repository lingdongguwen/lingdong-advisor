package com.yuanlingbb.auto.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.yuanlingbb.auto.data.Action
import com.yuanlingbb.auto.data.Condition
import com.yuanlingbb.auto.data.Task
import com.yuanlingbb.auto.data.TaskStore
import com.yuanlingbb.auto.R
import java.io.File

/**
 * 动作设置表单：为每类动作动态生成设置面板（识别设置/通用参数/运行条件）。
 * 原创实现，界面与对标产品无代码/素材重合。
 */
object ActionForms {

    private const val REQ_POINT = 101
    private const val REQ_SWIPE = 102
    private const val REQ_COLOR = 103
    private const val REQ_REGION = 104
    private const val REQ_GESTURE = 105
    private const val REQ_TEMPLATE = 106       // 从相册导入模板
    private const val REQ_GALLERY_COLOR = 107  // 从相册图片取色

    // 拾取回填目标
    private var pendingX: EditText? = null
    private var pendingY: EditText? = null
    private var pendingX1: EditText? = null
    private var pendingY1: EditText? = null
    private var pendingX2: EditText? = null
    private var pendingY2: EditText? = null
    private var pendingColor: EditText? = null
    private var pendingRect: EditText? = null
    private var pendingGestureRows: List<Pair<EditText, EditText>>? = null
    private var ctxRef: Activity? = null
    private var pendingAction: Action? = null
    private var currentDialog: AlertDialog? = null

    fun handleResult(requestCode: Int, data: Intent?) {
        if (data == null) return
        when (requestCode) {
            REQ_POINT -> {
                pendingX?.setText(data.getIntExtra("x", 0).toString())
                pendingY?.setText(data.getIntExtra("y", 0).toString())
            }
            REQ_SWIPE -> {
                pendingX1?.setText(data.getIntExtra("x1", 0).toString())
                pendingY1?.setText(data.getIntExtra("y1", 0).toString())
                pendingX2?.setText(data.getIntExtra("x2", 0).toString())
                pendingY2?.setText(data.getIntExtra("y2", 0).toString())
            }
            REQ_COLOR -> {
                val c = data.getIntExtra("color", 0)
                pendingColor?.setText("#" + Integer.toHexString(c).uppercase())
                pendingX?.setText(data.getIntExtra("x", 0).toString())
                pendingY?.setText(data.getIntExtra("y", 0).toString())
            }
            REQ_REGION -> {
                val r = data.getIntArrayExtra("rect") ?: return
                pendingRect?.setText("${r[0]},${r[1]},${r[2]},${r[3]}")
            }
            REQ_GESTURE -> {
                val pts = data.getIntArrayExtra("points") ?: return
                val rws = pendingGestureRows ?: return
                var k = 0
                for (i in rws.indices) {
                    if (k + 1 < pts.size) {
                        rws[i].first.setText(pts[k].toString())
                        rws[i].second.setText(pts[k + 1].toString())
                        k += 2
                    }
                }
            }
        }
    }

    // ---------- 相册/控件辅助 ----------

    fun handleGallery(requestCode: Int, data: Intent?, ctx: Activity) {
        if (data == null) return
        val uri = data.data ?: return
        when (requestCode) {
            REQ_TEMPLATE -> {
                val name = copyUriToTemplates(ctx, uri) ?: run {
                    Toast.makeText(ctx, "导入模板失败", Toast.LENGTH_SHORT).show(); return
                }
                Toast.makeText(ctx, "已导入模板：$name", Toast.LENGTH_SHORT).show()
                // 关闭当前表单并以最新模板列表重新打开
                currentDialog?.dismiss()
                pendingAction?.let { a -> ctxRef?.let { c -> show(c, a, onSaved = {}) } }
            }
            REQ_GALLERY_COLOR -> {
                val path = copyUriToCache(ctx, uri, "color_pick.png") ?: run {
                    Toast.makeText(ctx, "取色失败", Toast.LENGTH_SHORT).show(); return
                }
                val i = Intent(ctx, CaptureCoordActivity::class.java)
                i.putExtra("mode", "color")
                i.putExtra("bitmapPath", path)
                ctx.startActivityForResult(i, REQ_COLOR)
            }
        }
    }

    private fun importTemplate(ctx: Activity) {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        ctx.startActivityForResult(i, REQ_TEMPLATE)
    }

    private fun pickColorFromGallery(ctx: Activity) {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        ctx.startActivityForResult(i, REQ_GALLERY_COLOR)
    }

    private fun copyUriToTemplates(ctx: Activity, uri: android.net.Uri): String? {
        return try {
            val dir = File(ctx.filesDir, "templates")
            if (!dir.exists()) dir.mkdirs()
            val name = "tpl_${System.currentTimeMillis()}.png"
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                File(dir, name).outputStream().use { outs -> ins.copyTo(outs) }
            }
            name
        } catch (e: Exception) { null }
    }

    private fun copyUriToCache(ctx: Activity, uri: android.net.Uri, name: String): String? {
        return try {
            val dir = File(ctx.cacheDir, "picker")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, name)
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                f.outputStream().use { outs -> ins.copyTo(outs) }
            }
            f.absolutePath
        } catch (e: Exception) { null }
    }

    private fun findControlId(ctx: Activity, edit: EditText) {
        val svc = com.yuanlingbb.auto.accessibility.AutoAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(ctx, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }
        val list = svc.dumpViewIds()
        if (list.isEmpty()) {
            Toast.makeText(ctx, "当前界面未检测到带 ID 的控件", Toast.LENGTH_SHORT).show()
            return
        }
        val items = list.map { "${it.second}  |  ${it.first}" }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle("选择控件 ID（${list.size} 个）")
            .setItems(items) { _, which -> edit.setText(list[which].first) }
            .setNegativeButton("取消", null)
            .show()
    }

    fun show(activity: Activity, action: Action, onSave: (() -> Unit)? = null, onSaved: () -> Unit) {
        val ctx = activity
        ctxRef = activity
        pendingAction = action
        pendingX = null; pendingY = null; pendingX1 = null; pendingY1 = null
        pendingX2 = null; pendingY2 = null; pendingColor = null; pendingRect = null
        pendingGestureRows = null
        savers.clear()
        val container = LinearLayout(ctx)
        container.orientation = LinearLayout.VERTICAL
        container.setBackgroundColor(Color.WHITE)
        val pad = dp(ctx, 20)
        container.setPadding(pad, pad, pad, dp(ctx, 8))

        var delayEdit: EditText? = null
        var timesEdit: EditText? = null

        fun buildCommon() {
            delayEdit = rowInt(ctx, container, "延迟时间(ms)", action.delayMs.toString())
            timesEdit = rowInt(ctx, container, "执行次数", action.times.toString())
            val condBtn = smallBtn(ctx, "设置运行条件 ▶")
            condBtn.setOnClickListener { showCondition(ctx, action.condition) }
            container.addView(condBtn)
        }

        val p = action.params
        when (action.type) {
            com.yuanlingbb.auto.data.ActionType.TAP,
            com.yuanlingbb.auto.data.ActionType.DOUBLE_TAP -> {
                pendingX = rowInt(ctx, container, "X 坐标", p.optInt("x").toString())
                pendingY = rowInt(ctx, container, "Y 坐标", p.optInt("y").toString())
                container.addView(pickBtn(ctx, "拾取坐标", REQ_POINT))
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.LONG_PRESS -> {
                pendingX = rowInt(ctx, container, "X 坐标", p.optInt("x").toString())
                pendingY = rowInt(ctx, container, "Y 坐标", p.optInt("y").toString())
                container.addView(pickBtn(ctx, "拾取坐标", REQ_POINT))
                rowInt(ctx, container, "按压时长(ms)", p.optInt("longMs", 800).toString())
                    .let { e -> saveIntLater(p, "longMs", e) }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.SWIPE -> {
                pendingX1 = rowInt(ctx, container, "起点 X", p.optInt("x1").toString())
                pendingY1 = rowInt(ctx, container, "起点 Y", p.optInt("y1").toString())
                pendingX2 = rowInt(ctx, container, "终点 X", p.optInt("x2").toString())
                pendingY2 = rowInt(ctx, container, "终点 Y", p.optInt("y2").toString())
                container.addView(pickBtn(ctx, "拾取滑动轨迹", REQ_SWIPE))
                rowInt(ctx, container, "滑动时长(ms)", p.optInt("durMs", 500).toString())
                    .let { e -> saveIntLater(p, "durMs", e) }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.INPUT_TEXT -> {
                rowText(ctx, container, "要输入的文字", p.optString("text"))
                    .let { e -> saveTextLater(p, "text", e) }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.CLICK_IMAGE -> {
                val names = listTemplates(ctx)
                val sp = rowSpinner(ctx, container, "模板图片", names, p.optString("template"))
                val simEdit = rowInt(ctx, container, "相似度(50-100)", p.optInt("similarity", 90).toString())
                val failSp = rowSpinner(ctx, container, "识别失败后", listOf("stop", "next"), p.optString("onFail", "stop"))
                saveLater {
                    if (names.isNotEmpty()) p.put("template", names[sp.selectedItemPosition])
                    p.put("similarity", clampInt(simEdit, 50, 100, 90))
                    p.put("onFail", failSp.selectedItem.toString())
                }
                val impBtn = smallBtn(ctx, "从相册导入模板")
                impBtn.setOnClickListener { importTemplate(ctx) }
                container.addView(impBtn)
                if (names.isEmpty()) {
                    val hint = TextView(ctx)
                    hint.text = "（暂无模板：请先点上方按钮从相册导入一张图片作为模板）"
                    hint.textSize = 11f
                    hint.setTextColor(0xFFD32F2F.toInt())
                    hint.setPadding(0, dp(ctx, 4), 0, 0)
                    container.addView(hint)
                }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.CLICK_TEXT -> {
                rowText(ctx, container, "识别文字", p.optString("text"))
                    .let { e -> saveTextLater(p, "text", e) }
                rowSpinner(ctx, container, "文字匹配", listOf("包含文字", "完全匹配"),
                    if (p.optBoolean("exact", false)) "完全匹配" else "包含文字")
                    .let { sp -> saveLater { p.put("exact", sp.selectedItem.toString() == "完全匹配") } }
                rowSpinner(ctx, container, "识别失败后", listOf("stop", "next"), p.optString("onFail", "stop"))
                    .let { sp -> saveLater { p.put("onFail", sp.selectedItem.toString()) } }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.CLICK_CONTROL -> {
                val viewIdEdit = rowText(ctx, container, "控件 ID (viewId)", p.optString("viewId"))
                saveTextLater(p, "viewId", viewIdEdit)
                val findBtn = smallBtn(ctx, "辅助查找控件ID")
                findBtn.setOnClickListener { findControlId(ctx, viewIdEdit) }
                container.addView(findBtn)
                rowSpinner(ctx, container, "识别失败后", listOf("stop", "next"), p.optString("onFail", "stop"))
                    .let { sp -> saveLater { p.put("onFail", sp.selectedItem.toString()) } }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.CLICK_COLOR -> {
                pendingColor = rowText(ctx, container, "颜色(#RRGGBB)", formatColor(p.optInt("color")))
                rowInt(ctx, container, "容差(0-120)", p.optInt("tolerance", 0).toString())
                    .let { e -> saveIntLater(p, "tolerance", e) }
                container.addView(pickBtn(ctx, "屏幕取色(需先开启屏幕捕获)", REQ_COLOR))
                val galBtn = smallBtn(ctx, "从相册图片取色")
                galBtn.setOnClickListener { pickColorFromGallery(ctx) }
                container.addView(galBtn)
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.EXTRACT_TEXT -> {
                pendingRect = rowText(ctx, container, "限定区域(x1,y1,x2,y2 空则全屏)",
                    p.optJSONArray("rect")?.let { "${it.optInt(0)},${it.optInt(1)},${it.optInt(2)},${it.optInt(3)}" } ?: "")
                container.addView(pickBtn(ctx, "框选区域", REQ_REGION))
                saveLater {
                    val s = pendingRect?.text?.toString()?.trim() ?: ""
                    if (s.isEmpty()) p.remove("rect")
                    else {
                        val parts = s.split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (parts.size == 4) {
                            val arr = org.json.JSONArray()
                            for (v in parts) arr.put(v)
                            p.put("rect", arr)
                        }
                    }
                }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.OPEN_APP -> {
                val pkgEdit = rowText(ctx, container, "应用包名", p.optString("package"))
                val selBtn = smallBtn(ctx, "选择APP")
                selBtn.setOnClickListener {
                    pickApp(ctx) { pkg, label ->
                        pkgEdit.setText(pkg)
                        Toast.makeText(ctx, "已选 $label", Toast.LENGTH_SHORT).show()
                    }
                }
                container.addView(selBtn)
                rowInt(ctx, container, "启动后等待(ms)", p.optInt("waitMs", 1000).toString())
                    .let { e -> saveIntLater(p, "waitMs", e) }
                saveLater { pkgEdit?.let { p.put("package", it.text.toString().trim()) } }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.OPEN_WEB -> {
                rowText(ctx, container, "网址 URL", p.optString("url"))
                    .let { e -> saveTextLater(p, "url", e) }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.MULTI_GESTURE -> {
                val arr = p.optJSONArray("strokes")
                val n = arr?.length() ?: 2
                rowInt(ctx, container, "手指数量(2-5)", n.toString())
                val holder = LinearLayout(ctx); holder.orientation = LinearLayout.VERTICAL
                container.addView(holder)
                val rows = mutableListOf<Pair<EditText, EditText>>()
                for (i in 0 until Math.max(2, Math.min(5, n))) {
                    val x = arr?.optJSONObject(i)?.optInt("x1") ?: 0
                    val y = arr?.optJSONObject(i)?.optInt("y1") ?: 0
                    val le = LinearLayout(ctx); le.orientation = LinearLayout.HORIZONTAL
                    val ex = intEdit(ctx, x.toString()); val ey = intEdit(ctx, y.toString())
                    le.addView(lab(ctx, "指${i + 1} X")); le.addView(ex, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    le.addView(lab(ctx, "Y")); le.addView(ey, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    holder.addView(le)
                    rows.add(Pair(ex, ey))
                }
                rowInt(ctx, container, "触摸时长(ms)", (arr?.optJSONObject(0)?.optInt("dur") ?: 300).toString())
                saveLater {
                    val out = org.json.JSONArray()
                    for (r in rows) {
                        val o = org.json.JSONObject()
                        o.put("x1", r.first.text.toString().toIntOrNull() ?: 0)
                        o.put("y1", r.second.text.toString().toIntOrNull() ?: 0)
                        o.put("x2", r.first.text.toString().toIntOrNull() ?: 0)
                        o.put("y2", r.second.text.toString().toIntOrNull() ?: 0)
                        o.put("dur", 300)
                        out.put(o)
                    }
                    p.put("strokes", out)
                }
                val pickG = smallBtn(ctx, "在屏幕上标记坐标")
                pickG.setOnClickListener {
                    pendingGestureRows = rows
                    val i = Intent(ctx, CaptureCoordActivity::class.java)
                    i.putExtra("mode", "multigesture")
                    i.putExtra("count", rows.size)
                    ctx.startActivityForResult(i, REQ_GESTURE)
                }
                container.addView(pickG)
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.VAR_OP -> {
                rowText(ctx, container, "变量名", p.optString("name"))
                    .let { e -> saveTextLater(p, "name", e) }
                rowSpinner(ctx, container, "操作", listOf("set", "add", "reset"), p.optString("op", "set"))
                    .let { sp -> saveLater { p.put("op", sp.selectedItem.toString()) } }
                rowText(ctx, container, "值", p.optString("value"))
                    .let { e -> saveTextLater(p, "value", e) }
                rowSpinner(ctx, container, "作用范围", listOf("task", "global"), p.optString("scope", "task"))
                    .let { sp -> saveLater { p.put("scope", sp.selectedItem.toString()) } }
                buildCommon()
            }
            com.yuanlingbb.auto.data.ActionType.SUB_TASK -> {
                val nameEdit = rowText(ctx, container, "子任务", p.optString("taskName"))
                val selBtn = smallBtn(ctx, "选择任务")
                selBtn.setOnClickListener {
                    pickTask(ctx) { t ->
                        p.put("taskId", t.id)
                        p.put("taskName", t.name)
                        nameEdit.setText(t.name)
                    }
                }
                container.addView(selBtn)
                buildCommon()
            }
            else -> buildCommon()
        }

        // 保存按钮
        val saveBtn = Button(ctx)
        saveBtn.text = "保存"
        saveBtn.setTextColor(Color.WHITE)
        saveBtn.background = roundRect(0xFF66BB00.toInt(), dp(ctx, 12).toFloat())
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ctx, 14)
        saveBtn.layoutParams = lp
        container.addView(saveBtn)

        val scroll = ScrollView(ctx)
        scroll.addView(container)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(action.type.label)
            .setView(scroll)
            .create()
        currentDialog = dialog

        saveBtn.setOnClickListener {
            delayEdit?.text?.toString()?.toIntOrNull()?.let { v -> action.delayMs = v }
            timesEdit?.text?.toString()?.toIntOrNull()?.let { v -> action.times = if (v < 1) 1 else v }
            runSavers()
            onSaved()
            onSave?.invoke()
            dialog.dismiss()
        }
        dialog.show()
    }

    // ---------- 运行条件表单 ----------

    private fun showCondition(ctx: Activity, c: Condition) {
        val container = LinearLayout(ctx)
        container.orientation = LinearLayout.VERTICAL
        container.setBackgroundColor(Color.WHITE)
        container.setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 8))

        val spType = rowSpinner(ctx, container, "条件类型", listOf("无", "出现图片", "打开APP"),
            when (c.type) { "image" -> "出现图片"; "appOpen" -> "打开APP"; else -> "无" })

        val imgHolder = LinearLayout(ctx); imgHolder.orientation = LinearLayout.VERTICAL
        container.addView(imgHolder)
        val templates = listTemplates(ctx)
        val spTpl = rowSpinner(ctx, imgHolder, "图片匹配", templates, c.params.optString("template"))
        val simEdit = rowInt(ctx, imgHolder, "相似度(50-100)", c.params.optInt("similarity", 90).toString())

        val appHolder = LinearLayout(ctx); appHolder.orientation = LinearLayout.VERTICAL
        container.addView(appHolder)
        val pkgs = StringBuilder()
        val arr = c.params.optJSONArray("packages")
        if (arr != null) for (i in 0 until arr.length()) { if (i > 0) pkgs.append(","); pkgs.append(arr.optString(i)) }
        val pkgEdit = rowText(ctx, appHolder, "生效APP包名(逗号分隔,空=全部)", pkgs.toString())

        val abort = CheckBox(ctx); abort.text = "条件满足后中止执行"
        abort.isChecked = c.abortOnSuccess
        container.addView(abort)
        val inv = CheckBox(ctx); inv.text = "逻辑取反(条件不满足才执行)"
        inv.isChecked = c.invert
        container.addView(inv)

        fun refresh() {
            val t = spType.selectedItem.toString()
            imgHolder.visibility = if (t == "出现图片") View.VISIBLE else View.GONE
            appHolder.visibility = if (t == "打开APP") View.VISIBLE else View.GONE
        }
        spType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(a: android.widget.AdapterView<*>?) {}
        }
        refresh()

        AlertDialog.Builder(ctx)
            .setTitle("运行条件")
            .setView(scrollOf(ctx, container))
            .setPositiveButton("确定") { _, _ ->
                when (spType.selectedItem.toString()) {
                    "出现图片" -> {
                        c.type = "image"
                        c.params = org.json.JSONObject()
                        if (templates.isNotEmpty()) c.params.put("template", templates[spTpl.selectedItemPosition])
                        c.params.put("similarity", simEdit.text.toString().toIntOrNull() ?: 90)
                    }
                    "打开APP" -> {
                        c.type = "appOpen"
                        c.params = org.json.JSONObject()
                        val out = org.json.JSONArray()
                        for (s in pkgEdit.text.toString().split(",")) {
                            val t = s.trim(); if (t.isNotEmpty()) out.put(t)
                        }
                        c.params.put("packages", out)
                    }
                    else -> c.type = ""
                }
                c.abortOnSuccess = abort.isChecked
                c.invert = inv.isChecked
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 选择器 ----------

    private fun pickApp(ctx: Activity, onPick: (String, String) -> Unit) {
        val pm = ctx.packageManager
        val items = mutableListOf<String>()
        val pkgs = mutableListOf<String>()
        val intents = pm.getInstalledApplications(0)
        for (ai in intents) {
            val launch = pm.getLaunchIntentForPackage(ai.packageName) ?: continue
            val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { ai.packageName }
            items.add("$label (${ai.packageName})")
            pkgs.add(ai.packageName)
        }
        AlertDialog.Builder(ctx)
            .setTitle("选择APP")
            .setItems(items.toTypedArray()) { _, which -> onPick(pkgs[which], items[which]) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickTask(ctx: Activity, onPick: (Task) -> Unit) {
        val tasks = TaskStore.all(ctx).filter { it.mode != "listener" }
        if (tasks.isEmpty()) { Toast.makeText(ctx, "暂无其他任务", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(ctx)
            .setTitle("选择任务")
            .setItems(tasks.map { it.name }.toTypedArray()) { _, which -> onPick(tasks[which]) }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 通用小构件 ----------

    private val savers = mutableListOf<() -> Unit>()
    private fun saveLater(f: () -> Unit) { savers.add(f) }
    private fun runSavers() { for (f in savers) f(); savers.clear() }
    private fun saveTextLater(p: org.json.JSONObject, key: String, e: EditText?) {
        saveLater { p.put(key, e?.text?.toString()?.trim() ?: "") }
    }
    private fun saveIntLater(p: org.json.JSONObject, key: String, e: EditText?) {
        saveLater { e?.text?.toString()?.toIntOrNull()?.let { v -> p.put(key, v) } }
    }

    private fun clampInt(e: EditText?, min: Int, max: Int, def: Int): Int {
        val v = e?.text?.toString()?.toIntOrNull() ?: def
        return Math.max(min, Math.min(max, v))
    }

    private fun styleEdit(e: EditText) {
        val d = e.resources.displayMetrics.density
        e.setTextColor(0xFF222222.toInt())
        e.setHintTextColor(0xFF999999.toInt())
        e.setBackgroundResource(0)
        e.background = roundRect(0xFFF3F3F3.toInt(), (8 * d).toInt().toFloat())
        e.setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
    }

    private fun rowText(ctx: Activity, parent: LinearLayout, label: String, value: String): EditText {
        parent.addView(lab(ctx, label))
        val e = EditText(ctx)
        e.setText(value)
        e.textSize = 13f
        styleEdit(e)
        parent.addView(e)
        return e
    }

    private fun rowInt(ctx: Activity, parent: LinearLayout, label: String, value: String): EditText {
        parent.addView(lab(ctx, label))
        val e = intEdit(ctx, value)
        parent.addView(e)
        return e
    }

    private fun intEdit(ctx: Activity, value: String): EditText {
        val e = EditText(ctx)
        e.setText(value)
        e.textSize = 13f
        e.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        styleEdit(e)
        return e
    }

    private fun rowSpinner(ctx: Activity, parent: LinearLayout, label: String, items: List<String>, sel: String): Spinner {
        parent.addView(lab(ctx, label))
        val sp = Spinner(ctx)
        val ad = ArrayAdapter(ctx, R.layout.spinner_item_light, items)
        ad.setDropDownViewResource(R.layout.spinner_dropdown_item_light)
        sp.adapter = ad
        val idx = items.indexOf(sel)
        if (idx >= 0) sp.setSelection(idx)
        parent.addView(sp)
        return sp
    }

    private fun pickBtn(ctx: Activity, text: String, req: Int): Button {
        val b = smallBtn(ctx, text)
        b.setOnClickListener {
            val i = Intent(ctx, CaptureCoordActivity::class.java)
            i.putExtra("mode", when (req) {
                REQ_SWIPE -> "swipe"; REQ_COLOR -> "color"; REQ_REGION -> "region"; else -> "point"
            })
            ctx.startActivityForResult(i, req)
        }
        return b
    }

    private fun smallBtn(ctx: Activity, text: String): Button {
        val b = Button(ctx)
        b.text = text
        b.textSize = 13f
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ctx, 8)
        b.layoutParams = lp
        return b
    }

    private fun lab(ctx: Activity, s: String): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = 12f
        t.setTextColor(0xFF555555.toInt())
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ctx, 8)
        t.layoutParams = lp
        return t
    }

    private fun scrollOf(ctx: Activity, v: View): ScrollView {
        val s = ScrollView(ctx)
        s.addView(v)
        return s
    }

    private fun roundRect(color: Int, radius: Float): android.graphics.drawable.GradientDrawable {
        val d = android.graphics.drawable.GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radius
        return d
    }

    private fun dp(ctx: Activity, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun formatColor(c: Int): String =
        if (c == 0) "" else "#" + Integer.toHexString(c and 0xFFFFFF).uppercase()

    fun listTemplates(ctx: Activity): List<String> {
        val dir = File(ctx.filesDir, "templates")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
    }
}
