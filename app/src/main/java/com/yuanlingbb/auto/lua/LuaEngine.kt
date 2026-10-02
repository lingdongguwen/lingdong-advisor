package com.yuanlingbb.auto.lua

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.yuanlingbb.auto.accessibility.AutoAccessibilityService
import com.yuanlingbb.auto.util.FrameHolder
import com.yuanlingbb.auto.vision.VisionEngine
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Lua 脚本引擎（luaj）：把无障碍操作、视觉能力暴露为 Lua 全局函数。
 *
 * 暴露的 API：
 *   log(msg) / sleep(ms)
 *   tap(x,y) / swipe(x1,y1,x2,y2[,dur]) / longPress(x,y) / inputText(s)
 *   home() / back() / screenW() / screenH()
 *   findColor(r,g,b[,tol]) -> {x,y}|nil
 *   findColorAll(r,g,b[,tol]) -> {count, [1]= {x,y}, ...}
 *   findImage(name[,threshold]) -> {x,y,score}|nil
 *   ocr() -> {count, [1]={text,x,y,w,h}, ...}
 *   ocrText() -> string
 */
class LuaEngine(private val log: (String) -> Unit, private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()

    fun runScript(content: String, onDone: (error: String?) -> Unit) {
        executor.submit {
            try {
                val globals = JsePlatform.standardGlobals()
                registerApi(globals)
                val chunk = globals.load(content, "script")
                chunk.call()
                onDone(null)
            } catch (e: Exception) {
                log("✗ 错误: ${e.message}")
                onDone(e.message)
            }
        }
    }

    private fun registerApi(globals: Globals) {
        globals["log"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                log(args.arg(1).tojstring())
                return LuaValue.NIL
            }
        }
        globals["sleep"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val ms = args.arg(1).optint(0)
                Thread.sleep(ms.toLong())
                return LuaValue.NIL
            }
        }
        globals["tap"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val x = args.arg(1).checkint()
                val y = args.arg(2).checkint()
                gesture { AutoAccessibilityService.instance?.tap(x, y, it) ?: it() }
                return LuaValue.NIL
            }
        }
        globals["swipe"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val x1 = args.arg(1).checkint()
                val y1 = args.arg(2).checkint()
                val x2 = args.arg(3).checkint()
                val y2 = args.arg(4).checkint()
                val d = args.arg(5).optint(300).toLong()
                gesture { AutoAccessibilityService.instance?.swipe(x1, y1, x2, y2, d, it) ?: it() }
                return LuaValue.NIL
            }
        }
        globals["longPress"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val x = args.arg(1).checkint()
                val y = args.arg(2).checkint()
                gesture { AutoAccessibilityService.instance?.longPress(x, y, 600L, it) ?: it() }
                return LuaValue.NIL
            }
        }
        globals["inputText"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                AutoAccessibilityService.instance?.input(args.arg(1).tojstring())
                return LuaValue.NIL
            }
        }
        globals["home"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                AutoAccessibilityService.instance?.pressHome()
                return LuaValue.NIL
            }
        }
        globals["back"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                AutoAccessibilityService.instance?.pressBack()
                return LuaValue.NIL
            }
        }
        globals["screenW"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                return LuaValue.valueOf(FrameHolder.latestBitmap?.width ?: 0)
            }
        }
        globals["screenH"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                return LuaValue.valueOf(FrameHolder.latestBitmap?.height ?: 0)
            }
        }
        globals["findColor"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val r = args.arg(1).checkint()
                val g = args.arg(2).checkint()
                val b = args.arg(3).checkint()
                val tol = args.arg(4).optint(0)
                val color = Color.rgb(r, g, b)
                val pts = VisionEngine.findColor(color, tol)
                if (pts.isEmpty()) return LuaValue.NIL
                val t = LuaValue.tableOf()
                t["x"] = LuaValue.valueOf(pts[0].first)
                t["y"] = LuaValue.valueOf(pts[0].second)
                return t
            }
        }
        globals["findColorAll"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val r = args.arg(1).checkint()
                val g = args.arg(2).checkint()
                val b = args.arg(3).checkint()
                val tol = args.arg(4).optint(0)
                val pts = VisionEngine.findColor(Color.rgb(r, g, b), tol)
                val t = LuaValue.tableOf()
                pts.forEachIndexed { i, p ->
                    val e = LuaValue.tableOf()
                    e["x"] = LuaValue.valueOf(p.first)
                    e["y"] = LuaValue.valueOf(p.second)
                    t[i + 1] = e
                }
                t["count"] = LuaValue.valueOf(pts.size)
                return t
            }
        }
        globals["findImage"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val name = args.arg(1).tojstring()
                val thr = args.arg(2).optdouble(0.7)
                val bmp = loadTemplate(name)
                if (bmp == null) {
                    log("✗ 模板未找到: $name")
                    return LuaValue.NIL
                }
                val matches = VisionEngine.findImage(bmp, thr)
                if (matches.isEmpty()) return LuaValue.NIL
                val m = matches[0]
                val t = LuaValue.tableOf()
                t["x"] = LuaValue.valueOf(m.x)
                t["y"] = LuaValue.valueOf(m.y)
                t["score"] = LuaValue.valueOf(m.score)
                return t
            }
        }
        globals["ocr"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val lines = VisionEngine.ocr()
                val t = LuaValue.tableOf()
                lines.forEachIndexed { i, l ->
                    val e = LuaValue.tableOf()
                    e["text"] = LuaValue.valueOf(l.text)
                    e["x"] = LuaValue.valueOf(l.x)
                    e["y"] = LuaValue.valueOf(l.y)
                    e["w"] = LuaValue.valueOf(l.w)
                    e["h"] = LuaValue.valueOf(l.h)
                    t[i + 1] = e
                }
                t["count"] = LuaValue.valueOf(lines.size)
                return t
            }
        }
        globals["ocrText"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                return LuaValue.valueOf(VisionEngine.ocr().joinToString("") { it.text })
            }
        }
    }

    private fun gesture(action: (done: () -> Unit) -> Unit) {
        val latch = CountDownLatch(1)
        action { latch.countDown() }
        latch.await(5, TimeUnit.SECONDS)
    }

    private fun loadTemplate(name: String): Bitmap? {
        val dir = File(context.filesDir, "templates")
        if (!dir.exists()) return null
        for (cand in listOf(name, "$name.png", "$name.jpg", "$name.jpeg")) {
            val f = File(dir, cand)
            if (f.exists()) return BitmapFactory.decodeFile(f.absolutePath)
        }
        return null
    }
}
