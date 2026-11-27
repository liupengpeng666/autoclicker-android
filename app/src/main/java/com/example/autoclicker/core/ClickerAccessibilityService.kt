package com.example.autoclicker.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 核心：通过无障碍服务的 dispatchGesture 注入点击/滑动手势。
 * 需要用户手动在系统设置中开启无障碍权限。
 */
class ClickerAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ClickerAccessibilityService? = null
            private set

        /** 权限是否已开启且服务已连接 */
        fun isReady(): Boolean = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "连点服务已就绪", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 不处理事件 */ }
    override fun onInterrupt() { /* 服务被中断 */ }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** 注入一次单击，返回是否成功 */
    suspend fun click(x: Int, y: Int, durationMs: Long): Boolean = suspendCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            cont.resume(false)
            return@suspendCoroutine
        }
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                cont.resume(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                cont.resume(false)
            }
        }, handler)
    }

    /** 注入一次滑动（从起点到终点），返回是否成功 */
    suspend fun swipe(
        fromX: Int, fromY: Int,
        toX: Int, toY: Int,
        durationMs: Long
    ): Boolean = suspendCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            cont.resume(false)
            return@suspendCoroutine
        }
        val path = Path().apply {
            moveTo(fromX.toFloat(), fromY.toFloat())
            lineTo(toX.toFloat(), toY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                cont.resume(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                cont.resume(false)
            }
        }, handler)
    }
}
