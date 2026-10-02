package com.yuanlingbb.auto.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Script(
    val id: String,
    val name: String,
    val code: String,
    val updated: Long
)

/** 脚本本地存储：以 JSON 索引（scripts.json）保存于应用私有目录，所有内容仅存于本机 */
object ScriptStore {

    private const val FILE = "scripts.json"

    fun list(ctx: Context): List<Script> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        val out = mutableListOf<Script>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Script(
                    o.getString("id"),
                    o.getString("name"),
                    o.getString("code"),
                    o.optLong("updated", 0)
                )
            )
        }
        return out
    }

    fun save(ctx: Context, script: Script) {
        val all = list(ctx).toMutableList()
        val idx = all.indexOfFirst { it.id == script.id }
        if (idx >= 0) all[idx] = script else all.add(script)
        write(ctx, all)
    }

    fun delete(ctx: Context, id: String) {
        write(ctx, list(ctx).filter { it.id != id })
    }

    fun get(ctx: Context, id: String): Script? = list(ctx).firstOrNull { it.id == id }

    private fun write(ctx: Context, list: List<Script>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("name", it.name)
            o.put("code", it.code)
            o.put("updated", it.updated)
            arr.put(o)
        }
        File(ctx.filesDir, FILE).writeText(arr.toString())
    }
}
