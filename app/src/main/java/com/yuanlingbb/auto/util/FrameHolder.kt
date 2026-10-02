package com.yuanlingbb.auto.util

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect

/**
 * 全局帧容器：截屏服务把最新整屏帧写入 latestBitmap，
 * 选区 / 取色结果 / 模板也在此共享，供 Lua 引擎与各界面读取。
 */
object FrameHolder {
    var latestBitmap: Bitmap? = null
    var selectedRect: Rect? = null      // 位图坐标
    var pickedColor: Int? = null
    var pickedPoint: Point? = null       // 位图坐标
    var templateBitmap: Bitmap? = null
    var templateName: String? = null

    fun hasFrame(): Boolean = latestBitmap != null

    fun clearSelection() {
        selectedRect = null
        pickedColor = null
        pickedPoint = null
        templateBitmap = null
        templateName = null
    }
}
