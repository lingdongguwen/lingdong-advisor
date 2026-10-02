package com.yuanlingbb.auto.util

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge 适配（targetSdk 35 在 Android 15+ 强制 edge-to-edge）：
 * 把系统栏 + 刘海 insets 追加到根视图 padding，
 * 防止内容顶进状态栏 / 被底部导航条（手势条或三键导航）遮挡。
 * 背景（windowBackground / 根视图 background）依然延伸全屏，只内缩内容。
 */
object InsetsUtil {

    /** 对任意根视图追加系统栏 padding（保留视图原有 padding） */
    fun fit(view: View) {
        val pl = view.paddingLeft
        val pt = view.paddingTop
        val pr = view.paddingRight
        val pb = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(pl + bars.left, pt + bars.top, pr + bars.right, pb + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        view.requestApplyInsets()
    }

    /** 对使用 XML 布局的 Activity：直接给 content view 打 padding */
    fun fitActivity(activity: Activity) {
        fit(activity.findViewById(android.R.id.content))
    }
}
