package com.yuanlingbb.auto.util

import com.yuanlingbb.auto.data.Task

/** 悬浮窗/编辑器共享的"当前任务"状态 */
object OverlayState {
    var currentTask: Task? = null
    var isNewTask: Boolean = false

    fun ensureTask(): Task {
        if (currentTask == null) {
            currentTask = Task()
            isNewTask = true
        }
        return currentTask!!
    }

    fun openTask(t: Task, fresh: Boolean) {
        currentTask = t
        isNewTask = fresh
    }
}
