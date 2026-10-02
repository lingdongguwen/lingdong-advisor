package com.yuanlingbb.auto.ui

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuanlingbb.auto.R
import com.yuanlingbb.auto.capture.ScreenCaptureService
import com.yuanlingbb.auto.util.InsetsUtil

class PermissionGuideActivity : AppCompatActivity() {

    private val CAPTURE_REQ = 3001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_guide)
        InsetsUtil.fitActivity(this)

        findViewById<Button>(R.id.btnOpenAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOpenCapture).setOnClickListener {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), CAPTURE_REQ)
        }
    }

    @Deprecated("Deprecated in API 30+，仍可用")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CAPTURE_REQ && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java)
            intent.putExtra("resultCode", resultCode)
            intent.putExtra("data", data)
            startForegroundService(intent)
            Toast.makeText(this, "屏幕捕获已开启", Toast.LENGTH_SHORT).show()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
