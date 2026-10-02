package com.yuanlingbb.auto.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** 任务持久化：filesDir/tasks.json（应用私有目录，兼容 Android 9+） */
object TaskStore {
    private var tasks: ArrayList<Task> = ArrayList()
    private var loaded = false

    private fun file(ctx: Context): File = File(ctx.filesDir, "tasks.json")

    @Synchronized
    fun load(ctx: Context): ArrayList<Task> {
        if (loaded) return tasks
        tasks = ArrayList()
        try {
            val f = file(ctx)
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    tasks.add(Task.fromJson(arr.getJSONObject(i)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        loaded = true
        return tasks
    }

    @Synchronized
    fun save(ctx: Context) {
        try {
            val arr = JSONArray()
            for (t in tasks) arr.put(t.toJson())
            file(ctx).writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun all(ctx: Context): ArrayList<Task> = load(ctx)

    fun get(ctx: Context, id: Long): Task? {
        for (t in load(ctx)) if (t.id == id) return t
        return null
    }

    fun upsert(ctx: Context, task: Task) {
        load(ctx)
        var found = false
        for (i in tasks.indices) {
            if (tasks[i].id == task.id) { tasks[i] = task; found = true; break }
        }
        if (!found) tasks.add(task)
        save(ctx)
    }

    fun delete(ctx: Context, id: Long) {
        load(ctx)
        val it = tasks.iterator()
        while (it.hasNext()) { if (it.next().id == id) it.remove() }
        save(ctx)
    }

    fun simpleTasks(ctx: Context): List<Task> = load(ctx).filter { it.mode == "simple" || it.mode == "lua" }
    fun listeners(ctx: Context): List<Task> = load(ctx).filter { it.mode == "listener" }
}
