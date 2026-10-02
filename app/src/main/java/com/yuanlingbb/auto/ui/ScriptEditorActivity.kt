package com.yuanlingbb.auto.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuanlingbb.auto.R
import com.yuanlingbb.auto.accessibility.AutoAccessibilityService
import com.yuanlingbb.auto.data.Script
import com.yuanlingbb.auto.data.ScriptStore
import com.yuanlingbb.auto.lua.LuaEngine
import com.yuanlingbb.auto.util.FrameHolder
import com.yuanlingbb.auto.util.InsetsUtil

class ScriptEditorActivity : AppCompatActivity() {

    private var scriptId: String? = null
    private lateinit var etName: EditText
    private lateinit var etCode: EditText
    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_editor)
        InsetsUtil.fitActivity(this)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "编辑脚本"

        etName = findViewById(R.id.etName)
        etCode = findViewById(R.id.etCode)
        tvLog = findViewById(R.id.tvLog)

        scriptId = intent.getStringExtra("id")
        if (scriptId != null) {
            ScriptStore.get(this, scriptId!!)?.let {
                etName.setText(it.name)
                etCode.setText(it.code)
            }
        } else {
            etCode.setText(SAMPLE)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnRun).setOnClickListener { run() }
        findViewById<Button>(R.id.btnCapture).setOnClickListener {
            startActivity(Intent(this, CapturePreviewActivity::class.java))
        }
    }

    private fun save() {
        val name = etName.text.toString().trim().ifBlank { "未命名脚本" }
        val code = etCode.text.toString()
        val id = scriptId ?: java.util.UUID.randomUUID().toString()
        ScriptStore.save(this, Script(id, name, code, System.currentTimeMillis()))
        scriptId = id
        toast(getString(R.string.toast_saved))
    }

    private fun run() {
        if (AutoAccessibilityService.instance == null) {
            toast(getString(R.string.toast_accessibility_off))
            startActivity(Intent(this, PermissionGuideActivity::class.java))
            return
        }
        if (FrameHolder.latestBitmap == null) {
            toast(getString(R.string.toast_capture_off))
            startActivity(Intent(this, CapturePreviewActivity::class.java))
            return
        }
        val code = etCode.text.toString()
        if (code.isBlank()) {
            toast(getString(R.string.toast_script_empty))
            return
        }
        appendLog("▶ 运行脚本")
        val engine = LuaEngine({ line -> runOnUiThread { appendLog(line) } }, this)
        engine.runScript(code) { err ->
            runOnUiThread { appendLog(if (err == null) "✓ 脚本执行完毕" else "✗ 错误：$err") }
        }
    }

    private fun appendLog(line: String) {
        val c = tvLog.text.toString()
        tvLog.text = if (c.isBlank()) line else "$c\n$line"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val SAMPLE = """-- 示例：找到红色(255,0,0)区域并点击，否则提示未找到
local p = findColor(255, 0, 0, 32)
if p then
  log("找到红色 @ " .. p.x .. "," .. p.y)
  tap(p.x, p.y)
else
  log("未找到红色")
end

-- 也可找图：findImage("图标名", 0.8) 其中图标名对应截屏预览里保存的模板
-- 也可识别文字：local t = ocrText(); log(t)
"""
    }
}
