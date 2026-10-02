package com.yuanlingbb.auto.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuanlingbb.auto.R
import com.yuanlingbb.auto.accessibility.AutoAccessibilityService
import com.yuanlingbb.auto.capture.ScreenCaptureService
import com.yuanlingbb.auto.data.ScriptStore
import com.yuanlingbb.auto.lua.LuaEngine
import com.yuanlingbb.auto.util.FrameHolder
import com.yuanlingbb.auto.util.InsetsUtil
import java.io.File

class CapturePreviewActivity : AppCompatActivity() {

    private lateinit var ivCapture: ImageView
    private lateinit var regionView: RegionSelectorView
    private lateinit var tvStatus: TextView
    private val CAPTURE_REQ = 1001
    private val handler = Handler(Looper.getMainLooper())
    private var runScriptId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_preview)
        InsetsUtil.fitActivity(this)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "截屏与取色"

        ivCapture = findViewById(R.id.ivCapture)
        regionView = findViewById(R.id.regionView)
        tvStatus = findViewById(R.id.tvStatus)
        regionView.bindSource(ivCapture)

        runScriptId = intent.getStringExtra("runScriptId")

        findViewById<Button>(R.id.btnPickColor).setOnClickListener {
            regionView.pickMode = true
            toast("点击画面任意位置取色")
        }
        findViewById<Button>(R.id.btnSaveTemplate).setOnClickListener { saveTemplate() }
        findViewById<Button>(R.id.btnRecapture).setOnClickListener { ensureCapture() }

        ensureCapture()
    }

    private fun ensureCapture() {
        if (!ScreenCaptureService.running) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), CAPTURE_REQ)
        } else {
            showFrame()
        }
    }

    @Deprecated("Deprecated in API 30+，仍可用")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CAPTURE_REQ) {
            if (resultCode == RESULT_OK && data != null) {
                val intent = Intent(this, ScreenCaptureService::class.java)
                intent.putExtra("resultCode", resultCode)
                intent.putExtra("data", data)
                startForegroundService(intent)
                handler.postDelayed({ showFrame() }, 900)
            } else {
                toast("未授权屏幕捕获，找图/找色/OCR 将不可用")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showFrame() {
        val b = FrameHolder.latestBitmap
        if (b == null) {
            handler.postDelayed({ showFrame() }, 300)
            return
        }
        ivCapture.setImageBitmap(b)
        regionView.invalidate()
        toast(getString(R.string.toast_capture_done))
        if (runScriptId != null) {
            val id = runScriptId
            runScriptId = null
            if (id != null) runScript(id)
        }
    }

    fun onColorPicked(color: Int, x: Int, y: Int) {
        val hex = String.format("#%06X", color and 0xFFFFFF)
        tvStatus.text = "取色 @($x,$y) = $hex  (RGB ${color shr 16 and 0xFF},${color shr 8 and 0xFF},${color and 0xFF})"
    }

    fun onRegionSelected(rect: Rect) {
        tvStatus.text = "已选区域：左${rect.left} 上${rect.top} 宽${rect.width()} 高${rect.height()}"
    }

    private fun saveTemplate() {
        val rect = FrameHolder.selectedRect
        val b = FrameHolder.latestBitmap
        if (rect == null || b == null) {
            toast("请先在画面上拖动框选一个区域")
            return
        }
        val safe = Rect(
            rect.left.coerceAtLeast(0), rect.top.coerceAtLeast(0),
            rect.right.coerceAtMost(b.width), rect.bottom.coerceAtMost(b.height)
        )
        if (safe.width() < 4 || safe.height() < 4) {
            toast("选区太小")
            return
        }
        val crop = Bitmap.createBitmap(b, safe.left, safe.top, safe.width(), safe.height())
        val input = EditText(this)
        input.hint = getString(R.string.template_name_hint)
        AlertDialog.Builder(this)
            .setTitle("保存为模板")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "tpl_${System.currentTimeMillis()}" }
                val dir = File(filesDir, "templates")
                dir.mkdirs()
                val file = File(dir, "$name.png")
                file.outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                toast(getString(R.string.toast_template_saved) + "：$name")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun runScript(id: String) {
        if (AutoAccessibilityService.instance == null) {
            toast(getString(R.string.toast_accessibility_off))
            startActivity(Intent(this, PermissionGuideActivity::class.java))
            return
        }
        val script = ScriptStore.get(this, id) ?: return
        appendLog("▶ 运行脚本：${script.name}")
        val engine = LuaEngine({ line -> runOnUiThread { appendLog(line) } }, this)
        engine.runScript(script.code) { err ->
            runOnUiThread { appendLog(if (err == null) "✓ 脚本执行完毕" else "✗ 结束：$err") }
        }
    }

    private fun appendLog(line: String) {
        val c = tvStatus.text.toString()
        tvStatus.text = if (c.isBlank()) line else "$c\n$line"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
