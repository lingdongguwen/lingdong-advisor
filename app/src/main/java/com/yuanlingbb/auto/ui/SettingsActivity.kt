package com.yuanlingbb.auto.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yuanlingbb.auto.BuildConfig
import com.yuanlingbb.auto.R
import com.yuanlingbb.auto.util.InsetsUtil

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        InsetsUtil.fitActivity(this)

        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            startActivity(Intent(this, PermissionGuideActivity::class.java))
        }
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<TextView>(R.id.tvVersion).text =
            "灵动豹豹 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }
}
