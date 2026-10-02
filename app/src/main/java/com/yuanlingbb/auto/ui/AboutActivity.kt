package com.yuanlingbb.auto.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yuanlingbb.auto.R
import com.yuanlingbb.auto.util.InsetsUtil

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        InsetsUtil.fitActivity(this)
    }
}
