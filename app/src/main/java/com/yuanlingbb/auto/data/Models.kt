package com.yuanlingbb.auto.data

import org.json.JSONArray
import org.json.JSONObject

/** 动作类型全集（对标主流自动点击器的功能分类，原创实现） */
enum class ActionType(val label: String, val category: String) {
    TAP("点击", "普通点击/滑动"),
    DOUBLE_TAP("双击", "普通点击/滑动"),
    LONG_PRESS("长按", "普通点击/滑动"),
    SWIPE("滑动", "普通点击/滑动"),
    INPUT_TEXT("输入文字", "普通点击/滑动"),
    CLICK_IMAGE("点击图片", "识别点击"),
    CLICK_TEXT("点击文字", "识别点击"),
    CLICK_CONTROL("点击控件", "识别点击"),
    CLICK_COLOR("点击颜色", "识别点击"),
    EXTRACT_TEXT("文本提取/复制", "文本/应用"),
    OPEN_APP("打开APP", "文本/应用"),
    OPEN_WEB("打开网页", "文本/应用"),
    KEY_BACK("返回键", "系统按键"),
    KEY_HOME("返回桌面", "系统按键"),
    SCREENSHOT("截屏", "系统按键"),
    RECENTS("最近任务", "系统按键"),
    NOTIF_BAR("下拉状态栏", "系统按键"),
    PLAY_SOUND("播放声音", "文本/应用"),
    MULTI_GESTURE("多指手势", "进阶"),
    VAR_OP("变量操作", "进阶"),
    SUB_TASK("子任务", "进阶");

    companion object {
        fun from(s: String): ActionType {
            for (t in entries) if (t.name == s) return t
            return TAP
        }
    }
}

/** 运行条件（动作执行前判断） */
class Condition {
    var type: String = "" // "":无条件 / "image":出现图片 / "appOpen":前台为指定APP
    var params: JSONObject = JSONObject()
    var abortOnSuccess: Boolean = false
    var invert: Boolean = false

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("type", type)
        o.put("params", params)
        o.put("abort", abortOnSuccess)
        o.put("invert", invert)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject?): Condition {
            val c = Condition()
            if (o == null) return c
            c.type = o.optString("type", "")
            c.params = o.optJSONObject("params") ?: JSONObject()
            c.abortOnSuccess = o.optBoolean("abort", false)
            c.invert = o.optBoolean("invert", false)
            return c
        }
    }
}

/** 动作：类型 + 参数 + 公共属性（延迟/次数/条件） */
class Action(val type: ActionType) {
    var id: Long = System.nanoTime()
    var params: JSONObject = JSONObject()
    var delayMs: Int = 50
    var times: Int = 1
    var condition: Condition = Condition()

    fun summary(): String {
        return when (type) {
            ActionType.TAP -> "(${params.optInt("x")},${params.optInt("y")})"
            ActionType.DOUBLE_TAP -> "(${params.optInt("x")},${params.optInt("y")})"
            ActionType.LONG_PRESS -> "(${params.optInt("x")},${params.optInt("y")}) ${params.optInt("longMs", 800)}ms"
            ActionType.SWIPE -> "(${params.optInt("x1")},${params.optInt("y1")})→(${params.optInt("x2")},${params.optInt("y2")}) ${params.optInt("durMs", 500)}ms"
            ActionType.INPUT_TEXT -> "\"${params.optString("text")}\""
            ActionType.CLICK_IMAGE -> "模板:${params.optString("template")} 相似度:${params.optInt("similarity", 90)}"
            ActionType.CLICK_TEXT -> "文字:${params.optString("text")}"
            ActionType.CLICK_CONTROL -> "ID:${params.optString("viewId")}"
            ActionType.CLICK_COLOR -> "色:#${Integer.toHexString(params.optInt("color")).uppercase()}"
            ActionType.EXTRACT_TEXT -> "区域复制"
            ActionType.OPEN_APP -> params.optString("package")
            ActionType.OPEN_WEB -> params.optString("url")
            ActionType.MULTI_GESTURE -> "${params.optJSONArray("strokes")?.length() ?: 0}指"
            ActionType.VAR_OP -> "${params.optString("name")} ${params.optString("op")} ${params.optString("value")}"
            ActionType.SUB_TASK -> params.optString("taskName")
            ActionType.PLAY_SOUND -> "提示音"
            else -> ""
        }
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("type", type.name)
        o.put("params", params)
        o.put("delay", delayMs)
        o.put("times", times)
        o.put("cond", condition.toJson())
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Action {
            val a = Action(ActionType.from(o.optString("type")))
            a.id = o.optLong("id", System.nanoTime())
            a.params = o.optJSONObject("params") ?: JSONObject()
            a.delayMs = o.optInt("delay", 50)
            a.times = o.optInt("times", 1)
            a.condition = Condition.fromJson(o.optJSONObject("cond"))
            return a
        }
    }
}

/** 任务：简易模式(动作组) / Lua脚本 / 监听器 */
class Task {
    var id: Long = System.nanoTime()
    var name: String = "未命名"
    var mode: String = "simple" // simple / lua / listener
    var actions: ArrayList<Action> = ArrayList()
    var lua: String = ""
    var createdAt: Long = System.currentTimeMillis()
    // listener 模式专用
    var listenerTemplate: String = ""   // 监听图片模板文件名
    var listenerSimilarity: Int = 90
    var listenerTargetTaskId: Long = -1L

    fun isSimple(): Boolean = mode == "simple"

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("mode", mode)
        val arr = JSONArray()
        for (a in actions) arr.put(a.toJson())
        o.put("actions", arr)
        o.put("lua", lua)
        o.put("createdAt", createdAt)
        o.put("ltpl", listenerTemplate)
        o.put("lsim", listenerSimilarity)
        o.put("ltarget", listenerTargetTaskId)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Task {
            val t = Task()
            t.id = o.optLong("id", System.nanoTime())
            t.name = o.optString("name", "未命名")
            t.mode = o.optString("mode", "simple")
            t.actions = ArrayList()
            val arr = o.optJSONArray("actions")
            if (arr != null) for (i in 0 until arr.length()) {
                t.actions.add(Action.fromJson(arr.getJSONObject(i)))
            }
            t.lua = o.optString("lua", "")
            t.createdAt = o.optLong("createdAt", System.currentTimeMillis())
            t.listenerTemplate = o.optString("ltpl", "")
            t.listenerSimilarity = o.optInt("lsim", 90)
            t.listenerTargetTaskId = o.optLong("ltarget", -1L)
            return t
        }
    }
}
