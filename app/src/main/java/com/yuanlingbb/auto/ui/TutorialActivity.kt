package com.yuanlingbb.auto.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yuanlingbb.auto.util.InsetsUtil

/** 新手教程：图文分步引导（原创内容） */
class TutorialActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this)
        root.setBackgroundColor(Color.WHITE)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val pad = (18 * resources.displayMetrics.density).toInt()
        col.setPadding(pad, pad, pad, pad)
        root.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun title(s: String) {
            val t = TextView(this)
            t.text = s
            t.textSize = 17f
            t.setTypeface(null, Typeface.BOLD)
            t.setTextColor(0xFF33691E.toInt())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (16 * resources.displayMetrics.density).toInt()
            col.addView(t, lp)
        }

        fun body(s: String) {
            val t = TextView(this)
            t.text = s
            t.textSize = 14f
            t.setTextColor(0xFF444444.toInt())
            t.setLineSpacing(0f, 1.35f)
            col.addView(t)
        }

        fun card(s: String) {
            val t = TextView(this)
            t.text = s
            t.textSize = 13f
            t.setTextColor(0xFF33691E.toInt())
            t.setPadding((12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt())
            val d = GradientDrawable()
            d.setColor(0xFFF3F6EE.toInt())
            d.cornerRadius = (10 * resources.displayMetrics.density).toFloat()
            t.background = d
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (6 * resources.displayMetrics.density).toInt()
            col.addView(t, lp)
        }

        title("灵动豹豹 · 快速上手")
        body("三步开启自动化：授予权限 → 悬浮窗添加动作 → 回目标APP执行。全程在目标APP之上操作，本APP只负责编辑与管理。")

        title("第 1 步 · 开启权限")
        body("① 无障碍服务：设置 → 无障碍 → 灵动豹豹 → 开启（用于模拟点击/滑动/输入）。\n② 悬浮窗权限：允许「显示在其他应用上层」。\n③ 录屏授权：主界面点「截屏」并同意（识别点击图片/文字/颜色依赖整屏帧）。")

        title("第 2 步 · 悬浮窗控制台")
        card("开始：执行当前任务；运行时变红，再点即停止\n添加：点击屏幕任意位置即加入「点击」动作，屏幕出现序号圆标\n删除：点击序号圆标即删除该动作\n列表 / 设置：打开动作编辑器（排序/复制/表单参数）\n隐藏：隐藏或显示序号圆标\n保存：保存任务（改名后请保存）\n提示：按住浮窗空白处可拖动位置")
        body("打开方式：主界面任务卡「✎」进入编辑器 → 「打开悬浮窗」；或运行任务后自动弹出。")

        title("第 3 步 · 编辑动作")
        body("编辑器内「＋添加动作」可选择全部动作类型：点击 / 双击 / 长按 / 滑动 / 输入文字；识别点击（图片 / 文字 / 控件 / 颜色）；文本提取复制 / 打开APP / 打开网页；系统按键（返回 / 桌面 / 截屏 / 最近任务 / 状态栏）；多指手势 / 变量操作 / 子任务。")
        body("每个动作都可设置：延迟时间、执行次数、运行条件（出现图片 / 前台APP，支持成功后中止与逻辑取反）。识别类动作可设「失败后跳过」。")

        title("常用技巧")
        card("· 图片识别：在「截屏预览」框选并保存模板后，编辑器表单即可选用；相似度建议 80–95，区域越小匹配越快。\n· 控件识别：包名/控件ID 可通过系统的「开发者选项 → 布局边界」或无障碍检查工具获取。\n· 变量：set 赋值、add 累加、reset 归零；task 作用范围随任务重置，global 全局保留。\n· 子任务：把常用操作存成独立任务，在动作里选择调用（最多嵌套 3 层）。")

        title("免责与合规")
        body("本工具仅用于用户主动授权范围内的个人自动化操作（模拟点击、识别与录入）。请遵守目标应用的服务条款与当地法律法规，勿用于抢购、刷量、外挂等违规用途。")

        InsetsUtil.fit(root)
        setContentView(root)
    }
}
